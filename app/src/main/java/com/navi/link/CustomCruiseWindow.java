package com.navi.link;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;

public class CustomCruiseWindow extends BaseFloatingWindow {

    private TextView tvCruiseSpeed, tvCruiseUnit;
    private TextView tvCruiseDirection, tvCruiseRoadName;
    private LinearLayout llTrafficLightsContainer;
    private CameraWarningView cameraGroup;
    private LaneLineView laneLineView;
    private SpeedometerView speedGauge;
    private int themeColor = 0xFF4FC3F7;
    private boolean isOverspeedBlinking = false;
    public static final String PREFIX = "custom_cruise_";

    public CustomCruiseWindow(Context context, View floatingView) {
        super(context, floatingView);
    }

    @Override
    protected void initViews() {
        tvCruiseDirection = floatingView.findViewById(R.id.custom_cruise_direction);
        tvCruiseSpeed = floatingView.findViewById(R.id.tv_cruise_speed);
        tvCruiseUnit = floatingView.findViewById(R.id.tv_cruise_unit);
        tvCruiseRoadName = floatingView.findViewById(R.id.custom_road_name);
        llTrafficLightsContainer = floatingView.findViewById(R.id.custom_traffic_lights);
        cameraGroup = floatingView.findViewById(R.id.custom_camera);
        laneLineView = floatingView.findViewById(R.id.custom_lane_line);
        speedGauge = floatingView.findViewById(R.id.cruise_speed_gauge);
        if (laneLineView != null) laneLineView.setSimpleMode(true);
        themeColor = sp.getInt("theme_color", 0xFF4FC3F7);
        applyCustomLayout();
    }

    public void applyCustomLayout() {
        applyElement(tvCruiseDirection, "direction");
        View speedGroup = floatingView.findViewById(R.id.custom_speed_group);
        applyElement(speedGroup, "speed");
        if (tvCruiseUnit != null)
            tvCruiseUnit.setVisibility(tvCruiseSpeed != null ? tvCruiseSpeed.getVisibility() : View.GONE);
        applyElement(tvCruiseRoadName, "roadname");
        applyElement(laneLineView, "lane");
        applyElement(llTrafficLightsContainer, "trafficlight");
        applyElement(cameraGroup, "camera");
        // 速度样式最后应用，覆盖 applyElement 设置的显隐
        applySpeedStyle();
        adjustRootToFit();
    }

    private void adjustRootToFit() {
        View root = floatingView.findViewById(R.id.root_layout);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) root;
        // 强制重新测量，避免动态添加的子视图使用脏缓存
        vg.forceLayout();
        vg.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxR = 0, maxB = 0;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) c.getLayoutParams();
            int w = Math.max(c.getMeasuredWidth(), c.getMinimumWidth());
            int h = Math.max(c.getMeasuredHeight(), c.getMinimumHeight());
            if (w <= 0) w = dpToPx(50);
            if (h <= 0) h = dpToPx(30);
            float s = Math.abs(c.getScaleX());
            // 计算右/下边缘时同时考虑 margin 两侧和缩放
            int r = mlp.leftMargin + (int)(w * s) + (mlp.rightMargin > 0 ? mlp.rightMargin : 0);
            int b = mlp.topMargin + (int)(h * s) + (mlp.bottomMargin > 0 ? mlp.bottomMargin : 0);
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

    /** 根据 speed_style 偏好切换数字/仪表速度 */
    private void applySpeedStyle() {
        String style = sp.getString("speed_style", "digital");
        boolean isDigital = "digital".equals(style);
        View speedGroup = floatingView.findViewById(R.id.custom_speed_group);
        if (speedGroup != null) speedGroup.setVisibility(isDigital ? View.VISIBLE : View.INVISIBLE);
        if (tvCruiseSpeed != null) tvCruiseSpeed.setVisibility(isDigital ? View.VISIBLE : View.INVISIBLE);
        if (tvCruiseUnit != null) tvCruiseUnit.setVisibility(isDigital ? View.VISIBLE : View.INVISIBLE);
        // 仪表盘同步速度组的位置
        if (speedGauge != null) {
            speedGauge.setVisibility(isDigital ? View.GONE : View.VISIBLE);
            if (speedGroup != null) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) speedGroup.getLayoutParams();
                ViewGroup.MarginLayoutParams glp = (ViewGroup.MarginLayoutParams) speedGauge.getLayoutParams();
                if (mlp != null && glp != null) {
                    glp.leftMargin = mlp.leftMargin;
                    glp.topMargin = mlp.topMargin;
                    speedGauge.setLayoutParams(glp);
                }
            }
        }
    }

    private void applyElement(View view, String key) {
        if (view == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        float xDp = sp.getFloat(PREFIX + key + "_x", getDefaultX(key));
        float yDp = sp.getFloat(PREFIX + key + "_y", getDefaultY(key));
        boolean enabled = sp.getBoolean(PREFIX + key + "_enabled", true);
        int xPx = Math.round(xDp * density * physicalScale);
        int yPx = Math.round(yDp * density * physicalScale);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            flp.leftMargin = xPx; flp.topMargin = yPx;
            view.setLayoutParams(flp);
        }
        view.setVisibility(enabled ? View.VISIBLE : View.GONE);
        // 应用保存的大小缩放
        applySavedSize(view, key);
    }

    private void applySavedSize(View view, String key) {
        if (view == null) return;
        float sz = sp.getFloat(PREFIX + key + "_size", 1.0f);
        view.setScaleX(sz);
        view.setScaleY(sz);
    }

    private void reapplySize(String key) {
        View v = null;
        if ("direction".equals(key)) v = tvCruiseDirection;
        else if ("speed".equals(key)) v = floatingView.findViewById(R.id.custom_speed_group);
        else if ("roadname".equals(key)) v = tvCruiseRoadName;
        else if ("lane".equals(key)) v = laneLineView;
        else if ("trafficlight".equals(key)) v = llTrafficLightsContainer;
        else if ("camera".equals(key)) v = cameraGroup;
        applySavedSize(v, key);
    }

    private float getDefaultX(String key) {
        switch (key) {
            case "direction": return 8; case "speed": return 8;
            case "roadname": return 110; case "lane": return 240;
            case "trafficlight": return 200; case "camera": return 300;
            default: return 0;
        }
    }

    private float getDefaultY(String key) {
        switch (key) {
            case "direction": return 6; case "speed": return 30;
            case "roadname": return 14; case "lane": return 6;
            case "trafficlight": return 6; case "camera": return 6;
            default: return 0;
        }
    }

    @Override
    public void updateNaviInfo(int icon, String disNum, String disUnit, String actionStr,
            String roadName, String summaryStr, String eta, int progress, int curSpeed,
            int limitedSpeed, int cameraType, int cameraDist, int cameraSpeed,
            String endPoiName, int totalLightNum, int remainLightNum,
            String curRoadName, int carDirection) {}

    @Override
    public void updateCruiseInfo(int speed, String roadName, int cameraType, int cameraSpeed, int cameraDist, int carDirection) {
        boolean speedEnabled = sp.getBoolean(PREFIX + "speed_enabled", true);
        int limit = cameraSpeed > 0 ? cameraSpeed : 0;
        boolean overspeed = sp.getBoolean("overspeed_warning_enabled", true) && limit > 0 && speed > limit;

        if (tvCruiseSpeed != null) {
            tvCruiseSpeed.setText(String.valueOf(speed));
            if (overspeed) {
                tvCruiseSpeed.setTextColor(Color.RED);
                ObjectAnimator anim = (ObjectAnimator) tvCruiseSpeed.getTag();
                if (anim == null) {
                    ObjectAnimator na = ObjectAnimator.ofFloat(tvCruiseSpeed, "alpha", 1f, 0.3f);
                    na.setDuration(500); na.setRepeatCount(ValueAnimator.INFINITE);
                    na.setRepeatMode(ValueAnimator.REVERSE); na.start();
                    tvCruiseSpeed.setTag(na); isOverspeedBlinking = true;
                }
            } else {
                ObjectAnimator anim = (ObjectAnimator) tvCruiseSpeed.getTag();
                if (anim != null) { anim.cancel(); tvCruiseSpeed.setTag(null); }
                tvCruiseSpeed.setAlpha(1f); isOverspeedBlinking = false;
                int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
                tvCruiseSpeed.setTextColor(accent);
            }
            tvCruiseSpeed.setVisibility(speedEnabled ? View.VISIBLE : View.GONE);
        }
        if (tvCruiseUnit != null) tvCruiseUnit.setVisibility(speedEnabled ? View.VISIBLE : View.GONE);
        reapplySize("speed");
        // 更新仪表
        if (speedGauge != null) {
            speedGauge.setSpeed(speed);
            speedGauge.setOverspeed(overspeed);
        }
        if (tvCruiseRoadName != null) {
            if (sp.getBoolean(PREFIX + "roadname_enabled", true)) {
                tvCruiseRoadName.setText(roadName);
                tvCruiseRoadName.setVisibility(View.VISIBLE);
                reapplySize("roadname");
            } else tvCruiseRoadName.setVisibility(View.GONE);
        }
        if (tvCruiseDirection != null) {
            if (sp.getBoolean(PREFIX + "direction_enabled", true) && carDirection >= 0) {
                tvCruiseDirection.setText(getDirectionText(carDirection));
                tvCruiseDirection.setVisibility(View.VISIBLE);
                reapplySize("direction");
            } else tvCruiseDirection.setVisibility(View.GONE);
        }
        if (cameraGroup != null) {
            if (sp.getBoolean(PREFIX + "camera_enabled", true))
                cameraGroup.updateCameraInfo(cameraType, cameraDist, cameraSpeed);
            else cameraGroup.setVisibility(View.GONE);
        }
        adjustRootToFit();
    }

    @Override
    public void updateTrafficLight(int status, int dir, int countdown) {}

    @Override
    public void updateCruiseTrafficLights(JSONArray lightsArray) {
        if (llTrafficLightsContainer == null) return;
        // 检查用户开关：关闭时强制隐藏
        if (!sp.getBoolean(PREFIX + "trafficlight_enabled", true)) {
            llTrafficLightsContainer.setVisibility(View.GONE);
            llTrafficLightsContainer.removeAllViews();
            return;
        }
        int count = lightsArray != null ? lightsArray.length() : 0;
        if (count == 0) { llTrafficLightsContainer.setVisibility(View.GONE); llTrafficLightsContainer.removeAllViews(); return; }
        llTrafficLightsContainer.removeAllViews();
        float scale = FloatingWindowManager.getInstance().getScale();
        for (int i = 0; i < count; i++) {
            try {
                JSONObject obj = lightsArray.getJSONObject(i);
                TrafficLightView lv = new TrafficLightView(context);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginStart(dpToPx(5)); lv.setLayoutParams(lp);
                if (scale != 1.0f) scaleViewRecursive(lv, scale);
                int s = obj.getInt("status"), c = obj.getInt("countdown"), d = obj.getInt("dir");
                lv.setVisibility(c > 0 ? View.VISIBLE : View.GONE);
                if (c > 0) lv.setData(s, d, c, false);
                llTrafficLightsContainer.addView(lv);
            } catch (Exception ignored) {}
        }
        llTrafficLightsContainer.setVisibility(View.VISIBLE);
        reapplySize("trafficlight");
        adjustRootToFit();
    }

    @Override
    public void updateLaneLines(String driveWayJson) {
        if (laneLineView != null) {
            if (sp.getBoolean(PREFIX + "lane_enabled", true)) {
                laneLineView.updateLanes(driveWayJson);
                laneLineView.setVisibility(View.VISIBLE);
                reapplySize("lane");
                adjustRootToFit();
            } else {
                laneLineView.clear();
                laneLineView.setVisibility(View.GONE);
                adjustRootToFit();
            }
        }
    }

    @Override public void updateExitInfo(String exitName, String exitDirection) {}

    @Override
    public void applyThemeColor(int themeColor) {
        this.themeColor = themeColor;
        int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
        if (tvCruiseSpeed != null && !isOverspeedBlinking) tvCruiseSpeed.setTextColor(accent);
        if (tvCruiseUnit != null && !isOverspeedBlinking) tvCruiseUnit.setTextColor(accent);
        if (sp.getBoolean(PREFIX + "accent_navi_info_enabled", false)) {
            if (tvCruiseRoadName != null) tvCruiseRoadName.setTextColor(accent);
            if (tvCruiseUnit != null) tvCruiseUnit.setTextColor(accent);
            if (tvCruiseDirection != null) tvCruiseDirection.setTextColor(accent);
        }
    }

    @Override
    public void applyDayNightTextColors(boolean isNightMode) {
        this.isNightMode = isNightMode;
        int p = isNightMode ? TEXT_PRIMARY_DARK : TEXT_PRIMARY_LIGHT;
        if (tvCruiseRoadName != null) tvCruiseRoadName.setTextColor(p);
        if (tvCruiseDirection != null) tvCruiseDirection.setTextColor(p);
        if (tvCruiseUnit != null) tvCruiseUnit.setTextColor(p);
        if (sp.getBoolean(PREFIX + "accent_navi_info_enabled", false)) {
            int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
            if (tvCruiseRoadName != null) tvCruiseRoadName.setTextColor(accent);
            if (tvCruiseUnit != null) tvCruiseUnit.setTextColor(accent);
            if (tvCruiseDirection != null) tvCruiseDirection.setTextColor(accent);
        }
    }

    @Override
    public void resetToDefaultTextColors() {
        if (tvCruiseRoadName != null) tvCruiseRoadName.setTextColor(TEXT_PRIMARY_DARK);
        if (tvCruiseDirection != null) tvCruiseDirection.setTextColor(TEXT_PRIMARY_DARK);
        if (tvCruiseUnit != null) tvCruiseUnit.setTextColor(TEXT_PRIMARY_DARK);
        if (sp.getBoolean(PREFIX + "accent_navi_info_enabled", false)) {
            int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
            if (tvCruiseRoadName != null) tvCruiseRoadName.setTextColor(accent);
            if (tvCruiseUnit != null) tvCruiseUnit.setTextColor(accent);
            if (tvCruiseDirection != null) tvCruiseDirection.setTextColor(accent);
        }
    }

    @Override
    public void setPhysicalScale(float scale) {
        super.setPhysicalScale(scale);
        // 重新应用自定义布局，使用新的缩放比例计算所有元素的 margin
        applyCustomLayout();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (llTrafficLightsContainer != null) llTrafficLightsContainer.removeAllViews();
        if (tvCruiseSpeed != null) {
            ObjectAnimator anim = (ObjectAnimator) tvCruiseSpeed.getTag();
            if (anim != null) { anim.cancel(); tvCruiseSpeed.setTag(null); }
            tvCruiseSpeed.setAlpha(1f);
        }
        isOverspeedBlinking = false;
    }
}
