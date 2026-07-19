package com.navi.link;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 副屏投屏（TCP 结构化数据）发送端。
 * 作为 TCP 客户端连接副屏接收端（ServerSocket 9000），
 * 把高德导航/巡航语义字段与配置（位置/缩放/主题色/背景/元素开关）序列化为 JSON 帧下发。
 * 帧格式：int header(-2) + int jsonLen + jsonLen 字节 UTF-8 JSON。
 *
 * 【架构变更】sendJson 改为异步入队，由独立 HandlerThread 消费队列执行 TCP 写入，
 * 确保主线程（BroadcastReceiver）不被 TCP 阻塞，解决闪退与悬浮窗卡死问题。
 */
public class SecondScreenTransmitter {
    private static final String TAG = "SecondScreenTx";
    public static final int FRAME_STRUCTURED = -2;
    private static final int PORT = 9000;
    private static final String PREFS = "floating_config";
    private static final String KEY_TCP_IP = "tcp_sub_screen_ip";
    private static final String KEY_TCP_ENABLED = "tcp_sub_screen_enabled";
    private static final long RECONNECT_INTERVAL_MS = 3000;
    private static final int SEND_QUEUE_MAX = 128;
    private static final int MSG_FLUSH = 1;
    private static final int WRITE_SO_TIMEOUT_MS = 3000;
    /** 设置连续变更去抖窗口：合并 150ms 内的多次偏好写入，只推送一帧配置 */
    private static final long PUSH_DEBOUNCE_MS = 150;

    private static SecondScreenTransmitter instance;

    private final Context appContext;
    private Socket socket;
    private DataOutputStream out;
    private String targetIp = "";
    private volatile boolean running = false;
    private Thread reconnectThread;
    private final Object lock = new Object();

    // --- 设置变更自动推送（覆盖全部 floating_config 设置项） ---
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable pushRunnable = this::pushConfig;

    // --- 异步发送 ---
    private HandlerThread sendThread;
    private Handler sendHandler;
    /** 无界并发安全队列暂存待发 JSON 字符串，不阻塞生产者（主线程） */
    private final ConcurrentLinkedQueue<String> sendQueue = new ConcurrentLinkedQueue<>();

    private SecondScreenTransmitter(Context context) {
        this.appContext = context.getApplicationContext();
        SharedPreferences sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.targetIp = sp.getString(KEY_TCP_IP, "");
        // 监听全部设置项（窗口样式/背景模式/缩放/功能避让/红绿灯/主题色/位置/元素显隐等），
        // 任一写入即去抖推送完整配置帧，使接收端实时跟随。
        prefListener = (spChanged, key) -> schedulePushConfig();
        sp.registerOnSharedPreferenceChangeListener(prefListener);
    }

    public static synchronized SecondScreenTransmitter getInstance(Context context) {
        if (instance == null) {
            instance = new SecondScreenTransmitter(context);
        }
        return instance;
    }

    public static SecondScreenTransmitter getInstance() {
        return instance;
    }

    public void setTargetIp(String ip) {
        this.targetIp = ip == null ? "" : ip.trim();
        SharedPreferences sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_TCP_IP, this.targetIp).apply();
        tryConnectNow(); // IP 更新后立即尝试连接/重连
    }

    public String getTargetIp() {
        return targetIp;
    }

    public boolean isEnabled() {
        SharedPreferences sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_TCP_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        SharedPreferences sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_TCP_ENABLED, enabled).apply();
        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    public void start() {
        if (running) return;
        running = true;

        // 重新注册设置变更监听器：stop() 会注销它，若不在 start() 恢复，
        // 一次 TCP 关/开循环后所有 floating_config 改动都不再推送配置帧。
        SharedPreferences sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefListener != null) {
            sp.unregisterOnSharedPreferenceChangeListener(prefListener); // 防重复注册
            sp.registerOnSharedPreferenceChangeListener(prefListener);
        }

        // 同步最新目标 IP，防止 stop 期间 IP 被外部更新而错过
        targetIp = sp.getString(KEY_TCP_IP, targetIp);

        // 启动异步发送线程
        startSendThread();

        reconnectThread = new Thread(this::reconnectLoop, "SecondScreenTx-Reconnect");
        reconnectThread.start();
        Log.i(TAG, "transmitter started, target=" + targetIp);
        tryConnectNow(); // 立即尝试首连，无需等待首个重连周期
    }

    public void stop() {
        running = false;
        SharedPreferences sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefListener != null) {
            sp.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        mainHandler.removeCallbacks(pushRunnable);
        closeSocket();
        stopSendThread();
        if (reconnectThread != null) {
            reconnectThread.interrupt();
            reconnectThread = null;
        }
        Log.i(TAG, "transmitter stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isConnected() {
        synchronized (lock) {
            return socket != null && socket.isConnected() && out != null;
        }
    }

    // ==================== 异步发送线程 ====================

    private void startSendThread() {
        sendQueue.clear();
        sendThread = new HandlerThread("SecondScreenTx-Send");
        sendThread.start();
        sendHandler = new Handler(sendThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_FLUSH) {
                    drainQueue();
                }
            }
        };
    }

    private void stopSendThread() {
        sendQueue.clear();
        if (sendHandler != null) {
            sendHandler.removeCallbacksAndMessages(null);
            sendHandler = null;
        }
        if (sendThread != null) {
            sendThread.quitSafely();
            try { sendThread.join(1000); } catch (InterruptedException ignored) {}
            sendThread = null;
        }
    }

    /** 从队列中取出所有待发 JSON 字符串并一次性写 TCP。
     * 每条 JSON 单独组帧（header + len + bytes），当前帧发送失败后丢弃该帧及后续帧，
     * 并关闭 socket 触发重连。 */
    private void drainQueue() {
        String json;
        while ((json = sendQueue.poll()) != null) {
            synchronized (lock) {
                if (out == null) return; // socket 已关闭，丢弃本轮
                try {
                    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(FRAME_STRUCTURED);
                    out.writeInt(bytes.length);
                    out.write(bytes);
                } catch (Exception e) {
                    Log.w(TAG, "drain send failed: " + e.getMessage());
                    closeSocket();
                    sendQueue.clear(); // 当前 socket 已无效，清空队列等待重连
                    return;
                }
            }
        }
    }

    // ==================== 重连线程 ====================

    /** 立即尝试一次连接（独立线程，不阻塞调用方）；已连接或 targetIp 为空则跳过 */
    private void tryConnectNow() {
        if (!running) return;
        final String ip = targetIp;
        if (ip.isEmpty()) return;
        new Thread(() -> {
            synchronized (lock) {
                if (socket == null || !socket.isConnected()) {
                    try {
                        connectAndSendConfig();
                    } catch (Exception e) {
                        Log.w(TAG, "tryConnectNow failed: " + e.getMessage());
                    }
                }
            }
        }, "SecondScreenTx-TryNow").start();
    }

    private void reconnectLoop() {
        while (running) {
            synchronized (lock) {
                if (socket == null || !socket.isConnected()) {
                    if (!targetIp.isEmpty()) {
                        try {
                            connectAndSendConfig();
                        } catch (Exception e) {
                            Log.w(TAG, "connect failed: " + e.getMessage());
                        }
                    }
                }
            }
            try {
                Thread.sleep(RECONNECT_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void connectAndSendConfig() throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(targetIp, PORT), 3000);
        s.setTcpNoDelay(true);
        s.setSoTimeout(WRITE_SO_TIMEOUT_MS);
        synchronized (lock) {
            this.socket = s;
            this.out = new DataOutputStream(s.getOutputStream());
        }
        Log.i(TAG, "connected to " + targetIp);
        pushConfig();
    }

    private void closeSocket() {
        synchronized (lock) {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) { }
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) { }
            out = null;
            socket = null;
        }
    }

    // ==================== 公共 API ====================

    /**
     * 异步发送 JSON 结构化帧——主线程安全、非阻塞。
     * 将 JSON 序列化为字符串放入并发队列，然后通知发送线程排空。
     * 队列超过上限时丢弃最旧帧，防止内存堆积。
     */
    public void sendJson(JSONObject json) {
        if (json == null) return;
        if (!running) return;
        String str = json.toString();
        sendQueue.offer(str);

        // 队列上限保护：丢弃最旧帧
        while (sendQueue.size() > SEND_QUEUE_MAX) {
            sendQueue.poll();
        }

        // 唤醒发送线程
        Handler h = sendHandler;
        if (h != null) {
            h.removeMessages(MSG_FLUSH);
            h.sendEmptyMessage(MSG_FLUSH);
        }
    }

    /** 去抖调度：合并 150ms 内的连续设置改动，只触发一次配置帧推送 */
    private void schedulePushConfig() {
        if (!running) return;
        mainHandler.removeCallbacks(pushRunnable);
        mainHandler.postDelayed(pushRunnable, PUSH_DEBOUNCE_MS);
    }

    /** 构建并发送配置帧（位置/缩放/主题色/背景/元素 flags），在连接建立或任一设置变更时调用 */
    public void pushConfig() {
        if (!running) return;
        try {
            JSONObject cfg = buildConfig();
            sendJson(cfg);
        } catch (Exception e) {
            Log.e(TAG, "pushConfig failed", e);
        }
    }

    private JSONObject buildConfig() throws JSONException {
        SharedPreferences sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        FloatingWindowManager fwm = FloatingWindowManager.getInstance();
        JSONObject flags = new JSONObject();
        flags.put("cruise", sp.getBoolean("cruise_enabled", true));
        flags.put("normalLane", sp.getBoolean("normal_navi_lane_enabled", false));
        flags.put("minimalLane", sp.getBoolean("minimal_navi_lane_enabled", false));
        flags.put("normalTmc", sp.getBoolean("normal_navi_tmc_enabled", true));
        flags.put("normalBottom", sp.getBoolean("normal_navi_bottom_info_enabled", true));
        flags.put("normalCruise", sp.getBoolean("normal_cruise_info_enabled", true));
        flags.put("minimalRoad", sp.getBoolean("minimal_road_name_enabled", true));
        flags.put("minimalDir", sp.getBoolean("minimal_direction_enabled", false));
        flags.put("minimalTurn", sp.getBoolean("minimal_turn_info_enabled", true));
        flags.put("minimalSpeed", sp.getBoolean("minimal_speed_enabled", true));
        flags.put("minimalLightCount", sp.getBoolean("minimal_light_count_enabled", false));
        flags.put("minimalSpeedLimit", sp.getBoolean("minimal_speed_limit_enabled", false));
        flags.put("minimalCamera", sp.getBoolean("minimal_camera_enabled", false));
        flags.put("minimalAccent", sp.getBoolean("minimal_accent_navi_info_enabled", false));
        flags.put("tlFill", sp.getBoolean("traffic_light_fill_enabled", false));
        flags.put("tlCapsule", sp.getBoolean("traffic_light_capsule_enabled", true));
        flags.put("tlIcon", sp.getBoolean("traffic_light_icon_enabled", true));
        flags.put("overspeed", sp.getBoolean("overspeed_warning_enabled", true));
        flags.put("crossMapHide", sp.getBoolean("hide_on_cross_map", false));
        flags.put("hideTurnBg", sp.getBoolean("hide_turn_icon_bg", false));
        flags.put("hideLaneBg", sp.getBoolean("hide_lane_line_bg", false));

        JSONObject cfg = new JSONObject();
        cfg.put("t", 0);
        cfg.put("flags", flags);
        cfg.put("themeColor", sp.getInt("theme_color", 0xFF4FC3F7));
        cfg.put("bgMode", sp.getInt("background_mode", 0));
        if (fwm != null) {
            cfg.put("mode", fwm.getCurrentMode());
            // 直接从 prefs 读取最新样式，避免依赖 FWM 内存字段未同步导致推送旧值
            cfg.put("styleMode", sp.getInt("style_mode", sp.getBoolean("is_minimal_style", false) ? 1 : 0));
            cfg.put("cruiseStyleMode", sp.getInt("cruise_style_mode", 0));

            // 位置/缩放：仪表盘镜像用其自身字段；TCP 副屏用独立字段（虚拟 1920x1280 设计画布坐标空间）
            boolean clusterActive = fwm.isClusterMirrorActive();
            float scale = clusterActive ? fwm.getClusterScale() : fwm.getTcpScale();
            int posX, posY, screenW, screenH;
            if (clusterActive) {
                posX = fwm.getClusterSavedPosX();
                posY = fwm.getClusterSavedPosY();
                screenW = fwm.getClusterScreenWidth();
                screenH = fwm.getClusterScreenHeight();
            } else {
                posX = fwm.getTcpPosX();
                posY = fwm.getTcpPosY();
                if (posX < 0) posX = 960;
                if (posY < 0) posY = 640;
                screenW = 1920;
                screenH = 1280;
            }
            cfg.put("scale", scale);
            cfg.put("posX", posX);
            cfg.put("posY", posY);
            cfg.put("naturalW", fwm.getClusterNaturalWidth() > 0 ? fwm.getClusterNaturalWidth() : 320);
            cfg.put("naturalH", fwm.getClusterNaturalHeight() > 0 ? fwm.getClusterNaturalHeight() : 180);
            cfg.put("screenW", screenW > 0 ? screenW : 1920);
            cfg.put("screenH", screenH > 0 ? screenH : 1280);
        } else {
            cfg.put("scale", 1.0);
            cfg.put("posX", 0);
            cfg.put("posY", 0);
            cfg.put("naturalW", 160);
            cfg.put("naturalH", 120);
            cfg.put("screenW", 1920);
            cfg.put("screenH", 1280);
        }
        return cfg;
    }
}
