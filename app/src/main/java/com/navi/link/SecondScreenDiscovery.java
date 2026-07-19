package com.navi.link;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * 副屏接收端自动发现（UDP 监听）。
 * 接收端 DiscoveryService 每 1.5s 在 UDP 9001 广播 beacon：
 *   NAVI_LINK|设备名|9000|WiFi名
 * 本类绑定 9001 监听并解析出接收端 IP / 设备名，通过回调交给 MainActivity
 * 自动喂给 SecondScreenTransmitter，无需用户手动填写 IP。
 */
public class SecondScreenDiscovery {
    private static final String TAG = "SecondScreenDisc";
    private static final int DISCOVERY_PORT = 9001;
    private static final String BEACON_PREFIX = "NAVI_LINK|";

    public interface Listener {
        void onReceiverFound(String ip, String deviceName, int tcpPort);
    }

    private final Listener listener;
    private DatagramSocket socket;
    private Thread thread;
    private volatile boolean running = false;

    public SecondScreenDiscovery(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "SecondScreenDiscovery");
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void loop() {
        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
            byte[] buf = new byte[256];
            while (running) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    String fromIp = p.getAddress().getHostAddress();
                    if (fromIp != null) parse(msg, fromIp);
                } catch (Exception e) {
                    if (running) Log.w(TAG, "recv err: " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "bind failed: " + e.getMessage());
        }
    }

    private void parse(String msg, String fromIp) {
        if (msg == null || !msg.startsWith(BEACON_PREFIX)) return;
        String[] parts = msg.split("\\|");
        // NAVI_LINK|设备名|9000|WiFi名
        if (parts.length < 3) return;
        String deviceName = parts.length >= 2 && !parts[1].isEmpty() ? parts[1] : "副屏接收端";
        int tcpPort = 9000;
        try {
            tcpPort = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignore) {
        }
        if (listener != null) {
            listener.onReceiverFound(fromIp, deviceName, tcpPort);
        }
    }
}
