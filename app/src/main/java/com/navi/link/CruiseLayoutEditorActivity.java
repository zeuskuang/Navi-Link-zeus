package com.navi.link;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.Map;

public class CruiseLayoutEditorActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "floating_config";
    public static final String EXTRA_EDIT_MODE = "edit_mode";
    public static final int EDIT_MODE_CRUISE = 0;
    public static final int EDIT_MODE_NAVI = 1;

    private int editMode = EDIT_MODE_CRUISE;

    /** cruise要素: 不含红绿灯个数、转向信息 */
    private static final String[][] ELEMENTS_CRUISE = {
            {"speed",        "速度",     "custom_speed_group"},
            {"direction",    "方向",     "custom_cruise_direction"},
            {"roadname",     "路名",     "custom_road_name"},
            {"trafficlight", "红绿灯",   "custom_traffic_lights"},
            {"camera",       "摄像头",   "custom_camera"},
            {"lane",         "车道线",   "custom_lane_line"},
    };

    /** navi要素: 全部 */
    private static final String[][] ELEMENTS_NAVI = {
            {"speed",        "速度",     "custom_navi_speed_group"},
            {"direction",    "方向",     "custom_navi_direction"},
            {"turninfo",     "转向信息", "custom_navi_turn_group"},
            {"roadname",     "路名",     "custom_navi_roadname"},
            {"lightcount",   "红绿灯个数","custom_navi_lightcount"},
            {"trafficlight", "红绿灯",   "custom_navi_trafficlight"},
            {"camera",       "摄像头",   "custom_navi_camera"},
            {"lane",         "车道线",   "custom_navi_lane"},
            {"tmc",          "路况条",   "custom_navi_tmc"},
    };

    private String[][] ELEMENTS;
    private String prefPrefix;
    private String titleText;

    private FrameLayout previewWindow;
    private LinearLayout toggleContainer;
    private HorizontalScrollView toggleScroll;
    private View rootLayout;

    private int themeColor = 0xFF4FC3F7;
    private final Map<String, float[]> editPositions = new HashMap<>();
    private final Map<String, Boolean> editEnabled = new HashMap<>();
    private final Map<String, Float> editSizes = new HashMap<>();
    private float density;
    private float globalScale = 1.0f;

    // 上次点击时间，用于双击检测
    private final java.util.Map<String, Long> lastClickTime = new HashMap<>();

    private static final Map<String, String> ICON_MAP = new HashMap<>();
    static {
        ICON_MAP.put("speed", "\u26A1"); ICON_MAP.put("direction", "\uD83E\uDDED");
        ICON_MAP.put("roadname", "\uD83D\uDEE3\uFE0F"); ICON_MAP.put("lightcount", "\uD83D\uDEA6");
        ICON_MAP.put("trafficlight", "\uD83D\uDD34"); ICON_MAP.put("camera", "\uD83D\uDCF7");
        ICON_MAP.put("lane", "\uD83D\uDEDE"); ICON_MAP.put("turninfo", "\u21AA\uFE0F");
        ICON_MAP.put("tmc", "\uD83D\uDFE2");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cruise_layout_editor);

        editMode = getIntent().getIntExtra(EXTRA_EDIT_MODE, EDIT_MODE_CRUISE);
        boolean isNavi = editMode == EDIT_MODE_NAVI;
        ELEMENTS = isNavi ? ELEMENTS_NAVI : ELEMENTS_CRUISE;
        prefPrefix = isNavi ? CustomNaviWindow.PREFIX : CustomCruiseWindow.PREFIX;
        titleText = isNavi ? "自定义导航布局" : "自定义巡航布局";

        View contentView = findViewById(android.R.id.content);
        ViewGroup root = null;
        if (contentView instanceof ViewGroup) {
            root = ((ViewGroup) contentView).getChildCount() > 0
                    ? (ViewGroup) ((ViewGroup) contentView).getChildAt(0) : null;
        }
        if (root != null) {
            final int pl = root.getPaddingLeft(), pt = root.getPaddingTop();
            final int pr = root.getPaddingRight(), pb = root.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(pl + sys.left, pt + sys.top, pr + sys.right, pb + sys.bottom);
                return insets;
            });
        }

        androidx.core.view.WindowInsetsControllerCompat wic =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (wic != null) wic.setAppearanceLightStatusBars(false);

        density = getResources().getDisplayMetrics().density;
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeColor = sp.getInt("theme_color", 0xFF4FC3F7);

        ((TextView) findViewById(R.id.tv_editor_title)).setText(titleText);
        previewWindow = findViewById(R.id.preview_window);
        toggleContainer = findViewById(R.id.toggle_container);
        toggleScroll = findViewById(R.id.toggle_scroll);

        int layoutRes = isNavi ? R.layout.layout_floating_navi_custom : R.layout.layout_floating_cruise_custom;
        View cruiseView = LayoutInflater.from(this).inflate(layoutRes, null);
        rootLayout = cruiseView.findViewById(R.id.root_layout);
        applyThemeBackground(rootLayout);

        // 确保FloatingWindowManager已初始化，供LaneLineView等组件获取缩放值
        FloatingWindowManager.getInstance(this);
        globalScale = FloatingWindowManager.getInstance().getScale();

        // 应用全局缩放到预览视图（文字大小、padding、边距等）
        if (globalScale != 1.0f) {
            FloatingWindowManager.getInstance().scaleViewRecursive(cruiseView, globalScale);
        }

        // 预填充所有要素的默认值（确保即使view为null也有数据）
        for (String[] elem : ELEMENTS) {
            String k = elem[0];
            editPositions.put(k, new float[]{getDefaultX(k), getDefaultY(k)});
            editEnabled.put(k, true);
            editSizes.put(k, 1.0f);
        }

        for (String[] elem : ELEMENTS) {
            String key = elem[0], vid = elem[2];
            int viewId = getResources().getIdentifier(vid, "id", getPackageName());
            View view = cruiseView.findViewById(viewId);
            if (view == null) continue;

            boolean enabled = sp.getBoolean(prefPrefix + key + "_enabled", true);
            float xDp = sp.getFloat(prefPrefix + key + "_x", getDefaultX(key));
            float yDp = sp.getFloat(prefPrefix + key + "_y", getDefaultY(key));
            float size = sp.getFloat(prefPrefix + key + "_size", 1.0f);

            editPositions.put(key, new float[]{xDp, yDp});
            editEnabled.put(key, enabled);
            editSizes.put(key, size);
            applyElementPosition(view, xDp, yDp);
            view.setVisibility(enabled ? View.VISIBLE : View.GONE);
            applyLocalScale(view, size);
            setupDragListener(view, key);
            // 仪表盘与速度组共享同一位置和拖动
            if (key.equals("speed")) {
                int gaugeId = isNavi
                    ? R.id.navi_speed_gauge
                    : getResources().getIdentifier("cruise_speed_gauge", "id", getPackageName());
                View gauge = cruiseView.findViewById(gaugeId);
                if (gauge != null) {
                    applyElementPosition(gauge, xDp, yDp);
                    applyLocalScale(gauge, size);
                    setupDragListener(gauge, key);
                }
            }
        }

        adjustPreviewRoot();

        setPreviewData(cruiseView, isNavi);

        // setPreviewData 会强制设置 VISIBLE，重新应用开关状态
        for (String[] e : ELEMENTS) {
            String k = e[0];
            View v = findElementView(k);
            if (v != null) v.setVisibility(editEnabled.get(k) ? View.VISIBLE : View.GONE);
        }

        // 应用保存的速度显示模式（数字/仪表）
        String savedStyle = sp.getString("speed_style", "digital");
        updateSpeedPreview(savedStyle);

        previewWindow.addView(cruiseView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        // 预览数据更新后重新调整边框
        adjustPreviewRoot();

        createToggles();

        MaterialButton btnSave = findViewById(R.id.btn_save);
        MaterialButton btnCancel = findViewById(R.id.btn_cancel);
        MaterialButton btnReset = findViewById(R.id.btn_reset);
        int accent = isDarkColor(themeColor) ? Color.WHITE : themeColor;
        btnSave.setBackgroundTintList(ColorStateList.valueOf(accent));
        btnSave.setOnClickListener(v -> saveAndExit());
        btnCancel.setOnClickListener(v -> finish());
        btnReset.setOnClickListener(v -> resetDefaults());
    }

    private void applyThemeBackground(View root) {
        if (root == null) return;
        int bm = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("background_mode", 0);
        if (bm == 2) { root.setBackground(null); return; }
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        if (bm == 1) bg.setColor((themeColor & 0x00FFFFFF) | 0x80000000);
        else {
            if (isDarkColor(themeColor)) bg.setColor(0xFF121212);
            else {
                int r = (themeColor >> 16) & 0xFF, g = (themeColor >> 8) & 0xFF, b = themeColor & 0xFF;
                bg.setColor(0xFF000000 | ((int)(r * 0.12f) << 16) | ((int)(g * 0.12f) << 8) | (int)(b * 0.12f));
            }
        }
        bg.setCornerRadius(dpToPx(12));
        root.setBackground(bg);
    }

    private void setPreviewData(View v, boolean isNavi) {
        int accent = isDarkColor(themeColor) ? Color.WHITE : themeColor;
        if (isNavi) {
            setText(v, R.id.tv_navi_speed, "60", accent);
            setText(v, R.id.tv_navi_speed_unit, "km/h", accent);
            setText(v, R.id.custom_navi_roadname, "居里夫人大道", Color.WHITE);
            setText(v, R.id.custom_navi_direction, "北", accent);
            setText(v, R.id.tv_navi_distance_num, "283", Color.WHITE);
            setText(v, R.id.tv_navi_distance_unit, "米", 0xBBFFFFFF);
            setText(v, R.id.custom_navi_lightcount, "\uD83D\uDEA63个", Color.WHITE);
            // 红绿灯示例
            TrafficLightView tlNavi = v.findViewById(R.id.custom_navi_trafficlight);
            if (tlNavi != null) tlNavi.setData(1, 2, 15, true);
            // 车道线示例
            LaneLineView llNavi = v.findViewById(R.id.custom_navi_lane);
            if (llNavi != null) {
                llNavi.setSimpleMode(true);
                llNavi.updateLanes("{\"drive_way_enabled\":true,\"drive_way_size\":3,\"drive_way_info\":[{\"drive_way_number\":0,\"drive_way_lane_Back_icon\":\"0\"},{\"drive_way_number\":1,\"drive_way_lane_Back_icon\":\"1\"},{\"drive_way_number\":2,\"drive_way_lane_Back_icon\":\"2\"}]}");
            }
            // 摄像头示例
            CameraWarningView camNavi = v.findViewById(R.id.custom_navi_camera);
            if (camNavi != null) camNavi.updateCameraInfo(1, 300, 60);
            // TMC路况条示例
            TmcProgressBar tmcNavi = v.findViewById(R.id.custom_navi_tmc);
            if (tmcNavi != null) {
                tmcNavi.updateTmcData("{\"total_distance\":1000,\"finish_distance\":300,\"tmc_info\":[{\"tmc_status\":1,\"tmc_segment_distance\":300},{\"tmc_status\":2,\"tmc_segment_distance\":400},{\"tmc_status\":3,\"tmc_segment_distance\":300}]}");
            }
        } else {
            setText(v, R.id.tv_cruise_speed, "60", accent);
            setText(v, R.id.tv_cruise_unit, "km/h", accent);
            setText(v, R.id.custom_road_name, "建国路", Color.WHITE);
            setText(v, R.id.custom_cruise_direction, "北", accent);
            // 红绿灯容器示例（加一个样本灯）
            LinearLayout tlContainer = v.findViewById(R.id.custom_traffic_lights);
            if (tlContainer != null && tlContainer.getChildCount() == 0) {
                TrafficLightView sampleLight = new TrafficLightView(v.getContext());
                sampleLight.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                sampleLight.setData(1, 2, 15, false);
                tlContainer.addView(sampleLight);
            }
            // 车道线示例
            LaneLineView llCruise = v.findViewById(R.id.custom_lane_line);
            if (llCruise != null) {
                llCruise.setSimpleMode(true);
                llCruise.updateLanes("{\"drive_way_enabled\":true,\"drive_way_size\":2,\"drive_way_info\":[{\"drive_way_number\":0,\"drive_way_lane_Back_icon\":\"0\"},{\"drive_way_number\":1,\"drive_way_lane_Back_icon\":\"1\"}]}");
            }
            // 摄像头示例
            CameraWarningView camCruise = v.findViewById(R.id.custom_camera);
            if (camCruise != null) camCruise.updateCameraInfo(1, 300, 60);
            // 仪表盘示例（速度默认 60）
            SpeedometerView cruiseGauge = v.findViewById(R.id.cruise_speed_gauge);
            if (cruiseGauge != null) {
                cruiseGauge.setSpeed(60);
            }
        }
    }

    private void setText(View root, int id, String text, int color) {
        TextView tv = root.findViewById(id);
        if (tv != null) { tv.setText(text); tv.setTextColor(color); }
    }

    private void applyElementPosition(View view, float xDp, float yDp) {
        if (view == null) return;
        int xPx = Math.round(xDp * density * globalScale);
        int yPx = Math.round(yDp * density * globalScale);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).leftMargin = xPx;
            ((FrameLayout.LayoutParams) lp).topMargin = yPx;
            view.setLayoutParams(lp);
        }
    }

    private void setupDragListener(final View view, final String key) {
        view.setOnTouchListener((v, event) -> {
            if (!editEnabled.get(key)) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    float[] start = editPositions.get(key);
                    v.setTag(new float[]{event.getRawX(), event.getRawY(), start[0], start[1]});
                    v.setAlpha(0.7f);
                    blinkToggle(key);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    float[] d = (float[]) v.getTag();
                    if (d == null) return false;
                    float nx = Math.max(0, Math.min(d[2] + (event.getRawX() - d[0]) / density, 2000));
                    float ny = Math.max(0, Math.min(d[3] + (event.getRawY() - d[1]) / density, 2000));
                    editPositions.put(key, new float[]{nx, ny});
                    applyElementPosition(view, nx, ny);
                    adjustPreviewRoot();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f); v.setTag(null);
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        long now = System.currentTimeMillis();
                        Long last = lastClickTime.get(key);
                        if (last != null && now - last < 400) {
                            showSizePopup(view, key);
                            lastClickTime.put(key, 0L);
                        } else {
                            lastClickTime.put(key, now);
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    /** 闪烁对应开关并滚动到可见位置 */
    private void blinkToggle(String key) {
        if (toggleContainer == null) return;
        int idx = -1;
        for (int i = 0; i < ELEMENTS.length; i++) {
            if (ELEMENTS[i][0].equals(key)) { idx = i; break; }
        }
        if (idx < 0 || idx >= toggleContainer.getChildCount()) return;
        final View chip = toggleContainer.getChildAt(idx);

        chip.animate().alpha(0.3f).setDuration(150)
                .withEndAction(() -> chip.animate().alpha(1f).setDuration(150)
                .withEndAction(() -> chip.animate().alpha(0.3f).setDuration(150)
                .withEndAction(() -> chip.animate().alpha(1f).setDuration(150)
                .start()).start()).start());

        if (toggleScroll != null) {
            int[] loc = new int[2]; chip.getLocationOnScreen(loc);
            int[] svLoc = new int[2]; toggleScroll.getLocationOnScreen(svLoc);
            int offsetX = loc[0] - svLoc[0];
            int tw = toggleScroll.getWidth();
            int cw = chip.getWidth();
            if (offsetX + cw > tw) {
                toggleScroll.smoothScrollBy(offsetX + cw - tw + dpToPx(8), 0);
            } else if (offsetX < 0) {
                toggleScroll.smoothScrollBy(offsetX - dpToPx(8), 0);
            }
        }
    }

    private void createToggles() {
        int accent = isDarkColor(themeColor) ? Color.WHITE : themeColor;
        for (String[] elem : ELEMENTS) {
            String key = elem[0], label = elem[1];
            String icon = ICON_MAP.get(key);
            if (icon == null) icon = "\u25CF";

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            int p = dpToPx(8);
            chip.setPadding(p, dpToPx(5), p, dpToPx(5));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF262626); bg.setCornerRadius(dpToPx(8));
            chip.setBackground(bg);

            TextView iconTv = new TextView(this);
            iconTv.setText(icon); iconTv.setTextSize(16); iconTv.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ilp.setMarginEnd(dpToPx(4)); iconTv.setLayoutParams(ilp);

            TextView tv = new TextView(this);
            tv.setText(label); tv.setTextSize(13); tv.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.setMarginEnd(dpToPx(6)); tv.setLayoutParams(tlp);

            SwitchCompat sw = new SwitchCompat(this);
            sw.setChecked(editEnabled.get(key));
            sw.setButtonTintList(ColorStateList.valueOf(accent));

            chip.addView(iconTv); chip.addView(tv); chip.addView(sw);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMarginEnd(dpToPx(6)); chip.setLayoutParams(clp);

            final String fk = key;
            final View fv = findElementView(key);
            sw.setOnCheckedChangeListener((b, isChecked) -> {
                editEnabled.put(fk, isChecked);
                if (fv != null) {
                    fv.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    adjustPreviewRoot();
                }
            });
            chip.setOnClickListener(v -> {
                if ("speed".equals(fk)) {
                    // 双击速度开关切换数字/仪表模式
                    long now = System.currentTimeMillis();
                    Long last = lastClickTime.get(fk);
                    if (last != null && now - last < 400) {
                        SharedPreferences sp2 = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        String cur = sp2.getString("speed_style", "digital");
                        String next = "digital".equals(cur) ? "analog" : "digital";
                        sp2.edit().putString("speed_style", next).commit();
                        FloatingWindowManager fwm = FloatingWindowManager.getInstance();
                        if (fwm != null && fwm.isShowing()) fwm.refreshWindow();
                        updateSpeedPreview(next);
                        lastClickTime.put(fk, 0L);
                    } else {
                        lastClickTime.put(fk, now);
                    }
                } else {
                    sw.toggle();
                }
            });
            toggleContainer.addView(chip);
        }
    }

    private void showSizePopup(View anchor, final String key) {
        String label = key;
        for (String[] e : ELEMENTS) {
            if (e[0].equals(key)) { label = e[1]; break; }
        }
        final String fLabel = label;

        // 创建弹窗内容
        LinearLayout popupContent = new LinearLayout(this);
        popupContent.setOrientation(LinearLayout.VERTICAL);
        popupContent.setBackgroundColor(0xFF1E1E1E);
        int pad = dpToPx(16);
        popupContent.setPadding(pad, dpToPx(12), pad, dpToPx(12));

        // 圆角背景
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E1E1E); bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), 0xFF444444);
        popupContent.setBackground(bg);

        TextView labelTv = new TextView(this);
        labelTv.setText(fLabel);
        labelTv.setTextColor(Color.WHITE);
        labelTv.setTextSize(14);
        popupContent.addView(labelTv);

        final TextView sizeTv = new TextView(this);
        sizeTv.setText("1.0x");
        sizeTv.setTextColor(0xFF4FC3F7);
        sizeTv.setTextSize(20);
        popupContent.addView(sizeTv);

        SeekBar seek = new SeekBar(this);
        int cur = Math.round((editSizes.get(key) - 0.5f) / 1.5f * 100);
        seek.setProgress(Math.max(0, Math.min(cur, 100)));
        popupContent.addView(seek);

        final View fAnchor = anchor;
        final PopupWindow popup = new PopupWindow(popupContent,
                dpToPx(200), dpToPx(120), true);
        popup.setOutsideTouchable(true);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                float s = 0.5f + p / 100f * 1.5f;
                editSizes.put(key, s);
                sizeTv.setText((int)(s * 10) / 10.0f + "x");
                // 实时缩放预览
                View v = findElementView(key);
                if (v != null) {
                    applyLocalScale(v, s);
                    // 速度缩放同步到仪表盘
                    if ("speed".equals(key) && rootLayout != null) {
                        SpeedometerView gauge = rootLayout.findViewById(
                                getResources().getIdentifier("navi_speed_gauge", "id", getPackageName()));
                        if (gauge != null) applyLocalScale(gauge, s);
                    }
                    adjustPreviewRoot();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // 弹窗显示在锚点下方
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY,
                loc[0] + anchor.getWidth() / 2 - dpToPx(100),
                loc[1] + anchor.getHeight() + dpToPx(4));
    }

    /** 长按速度要素弹出样式选择 */
    private void showStylePopup(View anchor) {
        final SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final String current = sp.getString("speed_style", "digital");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFF1E1E1E);
        int p = dpToPx(16);
        content.setPadding(p, dpToPx(12), p, dpToPx(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E1E1E); bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), 0xFF444444);
        content.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("速度显示样式");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        content.addView(title);

        String[][] styles = {{"digital", "数字速度"}, {"analog", "仪表速度"}};

        final PopupWindow popup = new PopupWindow(content, dpToPx(180), dpToPx(140), true);
        popup.setOutsideTouchable(true);

        for (final String[] st : styles) {
            TextView item = new TextView(this);
            String prefix = current.equals(st[0]) ? "● " : "○ ";
            item.setText(prefix + st[1]);
            item.setTextColor(current.equals(st[0]) ? 0xFF4FC3F7 : 0xFFAAAAAA);
            item.setTextSize(16);
            item.setPadding(0, dpToPx(8), 0, dpToPx(8));
            item.setOnClickListener(v -> {
                sp.edit().putString("speed_style", st[0]).commit();
                FloatingWindowManager fwm = FloatingWindowManager.getInstance();
                if (fwm != null && fwm.isShowing()) fwm.refreshWindow();
                updateSpeedPreview(st[0]);
                popup.dismiss();
            });
            content.addView(item);
        }

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY,
                loc[0] + anchor.getWidth() / 2 - dpToPx(90),
                loc[1] + anchor.getHeight() + dpToPx(4));
    }

    private void updateSpeedPreview(String style) {
        View sv = findElementView("speed");
        if (sv == null) return;
        boolean isDigital = "digital".equals(style);
        // 数字/仪表切换：隐藏/显示整个速度组
        sv.setVisibility(isDigital ? View.VISIBLE : View.GONE);
        // 仪表盘在 rootLayout 中，与 speed_group 同级
        int gaugeId = editMode == EDIT_MODE_NAVI
            ? R.id.navi_speed_gauge
            : getResources().getIdentifier("cruise_speed_gauge", "id", getPackageName());
        SpeedometerView gauge = rootLayout.findViewById(gaugeId);
        if (gauge != null) gauge.setVisibility(isDigital ? View.GONE : View.VISIBLE);
        adjustPreviewRoot();
    }

    private void updateSpeedToggleButton(MaterialButton btn) {
        String style = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("speed_style", "digital");
        boolean isDigital = "digital".equals(style);
        btn.setText(isDigital ? "仪表" : "数字");
        btn.setTextColor(isDigital ? 0xFF888888 : 0xFF4FC3F7);
        btn.setStrokeColor(ColorStateList.valueOf(isDigital ? 0xFF444444 : 0xFF4FC3F7));
    }

    private View findElementView(String key) {
        if (rootLayout == null) return null;
        for (String[] e : ELEMENTS) {
            if (e[0].equals(key)) {
                int id = getResources().getIdentifier(e[2], "id", getPackageName());
                return rootLayout.findViewById(id);
            }
        }
        return null;
    }

    private void applyLocalScale(View view, float factor) {
        if (view == null) return;
        view.setScaleX(factor);
        view.setScaleY(factor);
    }

    private void adjustPreviewRoot() {
        if (rootLayout == null || !(rootLayout instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) rootLayout;
        // 强制测量所有子视图
        vg.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxR = 0, maxB = 0;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            c.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) c.getLayoutParams();
            int w = Math.max(c.getMeasuredWidth(), c.getMinimumWidth());
            int h = Math.max(c.getMeasuredHeight(), c.getMinimumHeight());
            if (w <= 0) w = dpToPx(50);
            if (h <= 0) h = dpToPx(30);
            float s = Math.abs(c.getScaleX());
            int r = mlp.leftMargin + (int)(w * s);
            int b = mlp.topMargin + (int)(h * s);
            if (r > maxR) maxR = r;
            if (b > maxB) maxB = b;
        }
        int pad = dpToPx(10);
        ViewGroup.LayoutParams lp = vg.getLayoutParams();
        if (lp != null) {
            lp.width = maxR + pad;
            lp.height = maxB + pad;
            vg.setLayoutParams(lp);
        }
    }

    private void resetDefaults() {
        for (String[] e : ELEMENTS) {
            String key = e[0];
            editPositions.put(key, new float[]{getDefaultX(key), getDefaultY(key)});
            editEnabled.put(key, true);
            editSizes.put(key, 1.0f);
            View v = findElementView(key);
            if (v != null) {
                applyElementPosition(v, getDefaultX(key), getDefaultY(key));
                v.setVisibility(View.VISIBLE);
            }
        }
        for (int i = 0; i < toggleContainer.getChildCount(); i++) {
            View chip = toggleContainer.getChildAt(i);
            if (chip instanceof LinearLayout && ((LinearLayout) chip).getChildCount() > 2
                    && ((LinearLayout) chip).getChildAt(2) instanceof SwitchCompat) {
                ((SwitchCompat) ((LinearLayout) chip).getChildAt(2)).setChecked(true);
            }
        }
        Toast.makeText(this, "已重置为默认布局", Toast.LENGTH_SHORT).show();
    }

    private void saveAndExit() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        for (String[] e : ELEMENTS) {
            String key = e[0];
            float[] pos = editPositions.get(key);
            boolean en = editEnabled.get(key);
            float sz = editSizes.get(key);
            editor.putFloat(prefPrefix + key + "_x", pos[0]);
            editor.putFloat(prefPrefix + key + "_y", pos[1]);
            editor.putBoolean(prefPrefix + key + "_enabled", en);
            editor.putFloat(prefPrefix + key + "_size", sz);
        }
        editor.commit();
        FloatingWindowManager fwm = FloatingWindowManager.getInstance();
        if (fwm != null && fwm.isShowing()) fwm.refreshWindow();
        Toast.makeText(this, "布局已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private float getDefaultX(String key) {
        if (editMode == EDIT_MODE_NAVI) {
            switch (key) {
                case "tmc": return 100;
                case "speed": return 8; case "direction": return 8;
                case "turninfo": return 100; case "roadname": return 100;
                case "lightcount": return 280; case "trafficlight": return 280;
                case "lane": return 200; case "camera": return 320;
                default: return 0;
            }
        } else {
            switch (key) {
                case "speed": return 8; case "direction": return 8;
                case "roadname": return 110; case "trafficlight": return 200;
                case "lane": return 240; case "camera": return 300;
                default: return 0;
            }
        }
    }

    private float getDefaultY(String key) {
        if (editMode == EDIT_MODE_NAVI) {
            switch (key) {
                case "tmc": return 80;
                case "speed": return 30; case "direction": return 6;
                case "turninfo": return 8; case "roadname": return 55;
                case "lightcount": return 8; case "trafficlight": return 40;
                case "lane": return 6; case "camera": return 6;
                default: return 0;
            }
        } else {
            switch (key) {
                case "speed": return 30; case "direction": return 6;
                case "roadname": return 14; case "trafficlight": return 6;
                case "lane": return 6; case "camera": return 6;
                default: return 0;
            }
        }
    }

    private int dpToPx(int dp) { return Math.round(dp * density + 0.5f); }

    private boolean isDarkColor(int color) {
        return ((color >> 16) & 0xFF) * 0.299
                + ((color >> 8) & 0xFF) * 0.587
                + (color & 0xFF) * 0.114 < 100;
    }
}
