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
import android.widget.LinearLayout;
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
    private View rootLayout;

    private int themeColor = 0xFF4FC3F7;
    private final Map<String, float[]> editPositions = new HashMap<>();
    private final Map<String, Boolean> editEnabled = new HashMap<>();
    private float density;

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

        int layoutRes = isNavi ? R.layout.layout_floating_navi_custom : R.layout.layout_floating_cruise_custom;
        View cruiseView = LayoutInflater.from(this).inflate(layoutRes, null);
        rootLayout = cruiseView.findViewById(R.id.root_layout);
        applyThemeBackground(rootLayout);

        // 确保FloatingWindowManager已初始化，供LaneLineView等组件获取缩放值
        FloatingWindowManager.getInstance(this);

        for (String[] elem : ELEMENTS) {
            String key = elem[0], vid = elem[2];
            int viewId = getResources().getIdentifier(vid, "id", getPackageName());
            View view = cruiseView.findViewById(viewId);
            if (view == null) continue;

            boolean enabled = sp.getBoolean(prefPrefix + key + "_enabled", true);
            float xDp = sp.getFloat(prefPrefix + key + "_x", getDefaultX(key));
            float yDp = sp.getFloat(prefPrefix + key + "_y", getDefaultY(key));

            editPositions.put(key, new float[]{xDp, yDp});
            editEnabled.put(key, enabled);
            applyElementPosition(view, xDp, yDp);
            view.setVisibility(enabled ? View.VISIBLE : View.GONE);
            setupDragListener(view, key);
        }

        setPreviewData(cruiseView, isNavi);
        previewWindow.addView(cruiseView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

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
        }
    }

    private void setText(View root, int id, String text, int color) {
        TextView tv = root.findViewById(id);
        if (tv != null) { tv.setText(text); tv.setTextColor(color); }
    }

    private void applyElementPosition(View view, float xDp, float yDp) {
        if (view == null) return;
        int xPx = Math.round(xDp * density);
        int yPx = Math.round(yDp * density);
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
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    float[] d = (float[]) v.getTag();
                    if (d == null) return false;
                    float nx = Math.max(0, Math.min(d[2] + (event.getRawX() - d[0]) / density, 900));
                    float ny = Math.max(0, Math.min(d[3] + (event.getRawY() - d[1]) / density, 300));
                    editPositions.put(key, new float[]{nx, ny});
                    applyElementPosition(view, nx, ny);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f); v.setTag(null);
                    return true;
            }
            return false;
        });
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
                if (fv != null) fv.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });
            chip.setOnClickListener(v -> sw.toggle());
            toggleContainer.addView(chip);
        }
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

    private void resetDefaults() {
        for (String[] e : ELEMENTS) {
            String key = e[0];
            editPositions.put(key, new float[]{getDefaultX(key), getDefaultY(key)});
            editEnabled.put(key, true);
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
            editor.putFloat(prefPrefix + key + "_x", pos[0]);
            editor.putFloat(prefPrefix + key + "_y", pos[1]);
            editor.putBoolean(prefPrefix + key + "_enabled", en);
        }
        editor.apply();
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
