package com.navi.link;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;

public class CustomNaviWindow extends BaseFloatingWindow {

    private TextView tvDirection, tvSpeed, tvSpeedUnit;
    private ImageView ivTurnIcon;
    private TextView tvDistNum, tvDistUnit;
    private TextView tvRoadName, tvLightCount;
    private LaneLineView laneLineView;
    private TrafficLightView trafficLightView;
    private TrafficLightCamView lightCamView;
    private CameraWarningView cameraGroup;
    private TmcProgressBar tmcProgressBar;
    private SpeedometerView speedGauge;
    private IntervalSpeedView intervalView;
    private IntervalStartView intervalStartView;
    private long lastVisSig = -1L;   // 可见元素集合签名，未变则不重算尺寸（避免数字抖动）
    private int themeColor = 0xFF4FC3F7;
    private boolean isOverspeedBlinking = false;
    private int mCameraType, mCameraDist, mCameraSpeed;

    public static final String PREFIX = "custom_navi_";

    public CustomNaviWindow(Context context, View floatingView) {
        super(context, floatingView);
    }

    @Override
    protected void initViews() {
        tvDirection = floatingView.findViewById(R.id.custom_navi_direction);
        tvSpeed = floatingView.findViewById(R.id.tv_navi_speed);
        tvSpeedUnit = floatingView.findViewById(R.id.tv_navi_speed_unit);
        ivTurnIcon = floatingView.findViewById(R.id.iv_navi_turn_icon);
        tvDistNum = floatingView.findViewById(R.id.tv_navi_distance_num);
        tvDistUnit = floatingView.findViewById(R.id.tv_navi_distance_unit);
        tvRoadName = floatingView.findViewById(R.id.custom_navi_roadname);
        tvLightCount = floatingView.findViewById(R.id.custom_navi_lightcount);
        laneLineView = floatingView.findViewById(R.id.custom_navi_lane);
        trafficLightView = floatingView.findViewById(R.id.custom_navi_trafficlight);
        lightCamView = floatingView.findViewById(R.id.custom_navi_lightcam);
        cameraGroup = floatingView.findViewById(R.id.custom_navi_camera);
        tmcProgressBar = floatingView.findViewById(R.id.custom_navi_tmc);
        speedGauge = floatingView.findViewById(R.id.navi_speed_gauge);
        intervalView = floatingView.findViewById(R.id.custom_navi_interval);
        intervalStartView = floatingView.findViewById(R.id.custom_navi_interval_start);

        if (laneLineView != null) laneLineView.setSimpleMode(true);
        themeColor = sp.getInt("theme_color", 0xFF4FC3F7);
        applyCustomLayout();
    }

    public void applyCustomLayout() {
        lastVisSig = -1L; // 重新布局（含缩放变化）时强制按新尺寸重算
        applyElement(tvDirection, "direction");
        View speedGroup = floatingView.findViewById(R.id.custom_navi_speed_group);
        applyElement(speedGroup, "speed");
        if (tvSpeedUnit != null)
            tvSpeedUnit.setVisibility(tvSpeed != null ? tvSpeed.getVisibility() : View.GONE);
        View turnGroup = floatingView.findViewById(R.id.custom_navi_turn_group);
        applyElement(turnGroup, "turninfo");
        applyElement(tvRoadName, "roadname");
        applyElement(tvLightCount, "lightcount");
        applyElement(laneLineView, "lane");
        applyElement(trafficLightView, "trafficlight");
        applyElement(cameraGroup, "camera");
        // 红绿灯+违章 复合胶囊：由数据驱动显隐，初始隐藏
        applyElement(lightCamView, "lightcam");
        if (lightCamView != null) lightCamView.setVisibility(View.GONE);
        applyElement(tmcProgressBar, "tmc");
        applyElement(intervalView, "interval");
        // 区间测速初始无数据先隐藏，由数据驱动显隐（与车道线一致：显示撑高、消失变窄）
        if (intervalView != null) intervalView.setVisibility(View.GONE);
        if (intervalView != null) intervalView.setThemeBlue(isDarkThemeColor(themeColor) ? 0xFF0099FF : themeColor);
        // 区间起点距离：同样由数据驱动显隐
        applyElement(intervalStartView, "interval_start");
        if (intervalStartView != null) intervalStartView.setVisibility(View.GONE);
        if (intervalStartView != null) intervalStartView.setThemeBlue(isDarkThemeColor(themeColor) ? 0xFF0099FF : themeColor);
        // 给会变数字的 TextView 预留固定宽度，数字变化时只改文本不动框，悬浮窗不抖动
        fixDigitWidth(tvSpeed, 3);
        fixTextWidth(tvSpeedUnit, "km/h");
        fixDigitWidth(tvDistNum, 4);
        fixTextWidth(tvDistUnit, "公里");
        fixTextWidth(tvRoadName, "中中中中中中");
        fixTextWidth(tvLightCount, "🚦99个");
        // 速度样式最后应用，覆盖 applyElement 设置的显隐
        applySpeedStyle();
        adjustRootToFit();
    }

    /** 根据 speed_style 偏好切换数字/仪表速度；若 speed_enabled 关闭则整体隐藏 */
    private void applySpeedStyle() {
        String style = sp.getString("speed_style", "digital");
        boolean isDigital = "digital".equals(style);
        boolean speedEnabled = sp.getBoolean(PREFIX + "speed_enabled", true);
        int digitalVis = speedEnabled ? (isDigital ? View.VISIBLE : View.INVISIBLE) : View.GONE;
        int gaugeVis = speedEnabled ? (isDigital ? View.GONE : View.VISIBLE) : View.GONE;
        View speedGroup = floatingView.findViewById(R.id.custom_navi_speed_group);
        if (speedGroup != null) speedGroup.setVisibility(digitalVis);
        if (tvSpeed != null) tvSpeed.setVisibility(digitalVis);
        if (tvSpeedUnit != null) tvSpeedUnit.setVisibility(digitalVis);
        // 仪表盘同步速度组的位置
        if (speedGauge != null) {
            speedGauge.setVisibility(gaugeVis);
            // 同步坐标
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

    private void adjustRootToFit() {
        View root = floatingView.findViewById(R.id.root_layout);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) root;
        // 仅当「可见元素集合」发生变化时才重算尺寸；纯数字更新不改变可见性，直接保持原尺寸不抖动
        long sig = 0;
        for (int i = 0; i < vg.getChildCount(); i++) {
            sig = sig * 31 + (vg.getChildAt(i).getVisibility() == View.VISIBLE ? 1 : 0);
        }
        if (sig == lastVisSig) return;
        lastVisSig = sig;
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
        int pad = dpToPx(14);
        ViewGroup.LayoutParams lp = vg.getLayoutParams();
        if (lp != null) {
            lp.width = maxR + pad;
            lp.height = maxB + pad;
            vg.setLayoutParams(lp);
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
        // 仪表盘同步速度组的缩放
        if ("speed".equals(key) && speedGauge != null) {
            speedGauge.setScaleX(sz);
            speedGauge.setScaleY(sz);
        }
    }

    private void reapplySize(String key) {
        View v = null;
        if ("direction".equals(key)) v = tvDirection;
        else if ("speed".equals(key)) v = floatingView.findViewById(R.id.custom_navi_speed_group);
        else if ("turninfo".equals(key)) v = floatingView.findViewById(R.id.custom_navi_turn_group);
        else if ("roadname".equals(key)) v = tvRoadName;
        else if ("lightcount".equals(key)) v = tvLightCount;
        else if ("lane".equals(key)) v = laneLineView;
        else if ("trafficlight".equals(key)) v = trafficLightView;
        else if ("camera".equals(key)) v = cameraGroup;
        else if ("lightcam".equals(key)) v = lightCamView;
        else if ("tmc".equals(key)) v = tmcProgressBar;
        else if ("interval".equals(key)) v = intervalView;
        else if ("interval_start".equals(key)) v = intervalStartView;
        applySavedSize(v, key);
    }

    private float getDefaultX(String key) {
        switch (key) {
            case "tmc": return 100;
            case "direction": return 8; case "speed": return 8;
            case "turninfo": return 100; case "roadname": return 100;
            case "lightcount": return 280; case "trafficlight": return 280;
            case "lane": return 200; case "camera": return 320;
            case "interval": return 8;
            case "interval_start": return 8;
            case "lightcam": return 280;
            default: return 0;
        }
    }

    private float getDefaultY(String key) {
        switch (key) {
            case "tmc": return 80;
            case "direction": return 6; case "speed": return 30;
            case "turninfo": return 8; case "roadname": return 55;
            case "lightcount": return 8; case "trafficlight": return 40;
            case "lane": return 6; case "camera": return 6;
            case "interval": return 110;
            case "interval_start": return 165;
            case "lightcam": return 95;
            default: return 0;
        }
    }

    @Override
    public void updateNaviInfo(int icon, String disNum, String disUnit, String actionStr,
            String roadName, String summaryStr, String eta, int progress, int curSpeed,
            int limitedSpeed, int cameraType, int cameraDist, int cameraSpeed,
            String endPoiName, int totalLightNum, int remainLightNum,
            String curRoadName, int carDirection) {

        boolean speedEnabled = sp.getBoolean(PREFIX + "speed_enabled", true);
        boolean turnInfoEnabled = sp.getBoolean(PREFIX + "turninfo_enabled", true);

        int limit = cameraSpeed > 0 ? cameraSpeed : limitedSpeed;
        boolean overspeed = sp.getBoolean("overspeed_warning_enabled", true) && limit > 0 && curSpeed > limit;

        if (tvSpeed != null) {
            tvSpeed.setText(String.valueOf(curSpeed));
            if (overspeed) {
                tvSpeed.setTextColor(Color.RED);
                ObjectAnimator anim = (ObjectAnimator) tvSpeed.getTag();
                if (anim == null) {
                    ObjectAnimator na = ObjectAnimator.ofFloat(tvSpeed, "alpha", 1f, 0.3f);
                    na.setDuration(500); na.setRepeatCount(ValueAnimator.INFINITE);
                    na.setRepeatMode(ValueAnimator.REVERSE); na.start();
                    tvSpeed.setTag(na); isOverspeedBlinking = true;
                }
            } else {
                ObjectAnimator anim = (ObjectAnimator) tvSpeed.getTag();
                if (anim != null) { anim.cancel(); tvSpeed.setTag(null); }
                tvSpeed.setAlpha(1f); isOverspeedBlinking = false;
                int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
                tvSpeed.setTextColor(accent);
            }
            tvSpeed.setVisibility(speedEnabled ? View.VISIBLE : View.GONE);
        }
        if (tvSpeedUnit != null) tvSpeedUnit.setVisibility(speedEnabled ? View.VISIBLE : View.GONE);
        reapplySize("speed");
        // 更新仪表（中央数字白色，超速时变红）
        if (speedGauge != null) {
            speedGauge.setSpeed(curSpeed);
            speedGauge.setOverspeed(overspeed);
        }

        if (ivTurnIcon != null) {
            int turnRes = getTurnIconRes(icon);
            if (turnRes != 0) ivTurnIcon.setImageResource(turnRes);
            boolean blink = shouldBlinkTurnIcon(disNum, disUnit);
            if (blink) {
                ObjectAnimator anim = (ObjectAnimator) ivTurnIcon.getTag();
                if (anim == null) {
                    ObjectAnimator na = ObjectAnimator.ofFloat(ivTurnIcon, "alpha", 1f, 0.3f);
                    na.setDuration(500); na.setRepeatCount(ValueAnimator.INFINITE);
                    na.setRepeatMode(ValueAnimator.REVERSE); na.start();
                    ivTurnIcon.setTag(na);
                }
            } else {
                ObjectAnimator anim = (ObjectAnimator) ivTurnIcon.getTag();
                if (anim != null) { anim.cancel(); ivTurnIcon.setTag(null); }
                ivTurnIcon.setAlpha(1f);
            }
            ivTurnIcon.setVisibility(turnInfoEnabled ? View.VISIBLE : View.GONE);
        }
        if (tvDistNum != null) {
            tvDistNum.setText(disNum);
            tvDistNum.setVisibility(turnInfoEnabled ? View.VISIBLE : View.GONE);
        }
        if (tvDistUnit != null) {
            tvDistUnit.setText(disNumIsNow(disNum) ? "进入" : disUnit);
            tvDistUnit.setVisibility(turnInfoEnabled ? View.VISIBLE : View.GONE);
        }
        reapplySize("turninfo");

        if (tvRoadName != null) {
            if (sp.getBoolean(PREFIX + "roadname_enabled", true)) {
                tvRoadName.setText(roadName); tvRoadName.setVisibility(View.VISIBLE);
                reapplySize("roadname");
            } else tvRoadName.setVisibility(View.GONE);
        }
        if (tvDirection != null) {
            if (sp.getBoolean(PREFIX + "direction_enabled", true) && carDirection >= 0) {
                tvDirection.setText(getDirectionText(carDirection));
                tvDirection.setVisibility(View.VISIBLE);
            } else tvDirection.setVisibility(View.GONE);
        }
        if (tvLightCount != null) {
            if (sp.getBoolean(PREFIX + "lightcount_enabled", true) && remainLightNum > 0) {
                tvLightCount.setText("\uD83D\uDEA6" + remainLightNum);
                tvLightCount.setVisibility(View.VISIBLE);
                reapplySize("lightcount");
            } else tvLightCount.setVisibility(View.GONE);
        }

        mCameraType = cameraType; mCameraDist = cameraDist; mCameraSpeed = cameraSpeed;
        updateCameraDist();
        // 红绿灯+违章 复合胶囊下部：红绿灯违章电子眼距离（cameraType=12）
        if (lightCamView != null) {
            if (sp.getBoolean(PREFIX + "lightcam_enabled", true)) {
                lightCamView.updateRedLightCamera(mCameraType, mCameraDist, mCameraSpeed);
            } else {
                lightCamView.setVisibility(View.GONE);
            }
        }
        reapplySize("direction");
        adjustRootToFit();
    }

    @Override
    public void updateIntervalSpeed(int curSpeed, int avgSpeed, int limitSpeed, String endDistanceText) {
        if (intervalView == null) return;
        if (!sp.getBoolean(PREFIX + "interval_enabled", true)) {
            intervalView.setVisibility(View.GONE);
            adjustRootToFit();
            return;
        }
        intervalView.updateIntervalSpeed(curSpeed, avgSpeed, limitSpeed, endDistanceText);
        reapplySize("interval");
        adjustRootToFit();
    }

    @Override
    public void updateIntervalStartDistance(int meters) {
        if (intervalStartView == null) return;
        if (!sp.getBoolean(PREFIX + "interval_start_enabled", true)) {
            intervalStartView.setVisibility(View.GONE);
            adjustRootToFit();
            return;
        }
        intervalStartView.updateIntervalStart(meters);
        reapplySize("interval_start");
        adjustRootToFit();
    }

    @Override
    public void updateCruiseInfo(int speed, String roadName, int cameraType, int cameraSpeed, int cameraDist, int carDirection) {}

    @Override
    public void updateTrafficLight(int status, int dir, int countdown) {
        boolean lightEnabled = sp.getBoolean(PREFIX + "trafficlight_enabled", true);
        boolean camEnabled = sp.getBoolean(PREFIX + "lightcam_enabled", true);

        // 旧版独立红绿灯胶囊：由自身开关控制，与复合胶囊互不干扰
        if (lightEnabled) {
            if (trafficLightView != null) {
                if (countdown <= 0) {
                    trafficLightView.clear();
                } else {
                    trafficLightView.setData(status, dir, countdown, true);
                    trafficLightView.setVisibility(View.VISIBLE);
                    reapplySize("trafficlight");
                }
            }
        } else {
            if (trafficLightView != null) trafficLightView.setVisibility(View.GONE);
        }

        // 复合胶囊（红绿灯+违章）：由自身开关控制，不受旧独立红绿灯开关影响
        if (lightCamView != null) {
            if (camEnabled) {
                lightCamView.updateTrafficLight(status, dir, countdown, true);
            } else {
                lightCamView.setVisibility(View.GONE);
            }
        }

        adjustRootToFit();
    }

    private void updateCameraDist() {
        if (cameraGroup == null) return;
        if (sp.getBoolean(PREFIX + "camera_enabled", true))
            cameraGroup.updateCameraInfo(mCameraType, mCameraDist, mCameraSpeed);
        else cameraGroup.setVisibility(View.GONE);
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
    public void updateTmcData(String tmcJson) {
        if (tmcProgressBar != null) {
            tmcProgressBar.updateTmcData(tmcJson);
            reapplySize("tmc");
            adjustRootToFit();
        }
    }

    @Override
    public void applyThemeColor(int themeColor) {
        this.themeColor = themeColor;
        int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
        if (tvSpeed != null && !isOverspeedBlinking) tvSpeed.setTextColor(accent);
        if (speedGauge != null) speedGauge.setThemeColor(themeColor);
        if (intervalView != null) intervalView.setThemeBlue(isDarkThemeColor(themeColor) ? 0xFF0099FF : themeColor);
        if (sp.getBoolean(PREFIX + "accent_navi_info_enabled", false)) {
            if (tvDistNum != null) tvDistNum.setTextColor(accent);
            if (tvDistUnit != null) tvDistUnit.setTextColor(accent);
            if (tvRoadName != null) tvRoadName.setTextColor(accent);
            if (ivTurnIcon != null) ivTurnIcon.setColorFilter(accent);
            if (tvSpeedUnit != null) tvSpeedUnit.setTextColor(accent);
            if (tvDirection != null) tvDirection.setTextColor(accent);
            if (tvLightCount != null) tvLightCount.setTextColor(accent);
        }
    }

    @Override
    public void applyDayNightTextColors(boolean isNightMode) {
        this.isNightMode = isNightMode;
        int p = isNightMode ? TEXT_PRIMARY_DARK : TEXT_PRIMARY_LIGHT;
        int s = isNightMode ? TEXT_SECONDARY_DARK : TEXT_SECONDARY_LIGHT;
        if (tvDistNum != null) tvDistNum.setTextColor(p);
        if (tvDistUnit != null) tvDistUnit.setTextColor(s);
        if (tvRoadName != null) tvRoadName.setTextColor(s);
        if (ivTurnIcon != null) ivTurnIcon.setColorFilter(p);
        if (tvSpeedUnit != null) tvSpeedUnit.setTextColor(p);
        if (tvDirection != null) tvDirection.setTextColor(p);
        if (tvLightCount != null) tvLightCount.setTextColor(p);
        if (sp.getBoolean(PREFIX + "accent_navi_info_enabled", false)) {
            int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
            if (tvDistNum != null) tvDistNum.setTextColor(accent);
            if (tvDistUnit != null) tvDistUnit.setTextColor(accent);
            if (tvRoadName != null) tvRoadName.setTextColor(accent);
            if (ivTurnIcon != null) ivTurnIcon.setColorFilter(accent);
            if (tvSpeedUnit != null) tvSpeedUnit.setTextColor(accent);
            if (tvDirection != null) tvDirection.setTextColor(accent);
            if (tvLightCount != null) tvLightCount.setTextColor(accent);
        }
    }

    @Override
    public void resetToDefaultTextColors() {
        if (tvDistNum != null) tvDistNum.setTextColor(TEXT_PRIMARY_DARK);
        if (tvDistUnit != null) tvDistUnit.setTextColor(TEXT_SECONDARY_DARK);
        if (tvRoadName != null) tvRoadName.setTextColor(TEXT_SECONDARY_DARK);
        if (ivTurnIcon != null) ivTurnIcon.clearColorFilter();
        if (tvSpeedUnit != null) tvSpeedUnit.setTextColor(TEXT_PRIMARY_DARK);
        if (tvDirection != null) tvDirection.setTextColor(TEXT_PRIMARY_DARK);
        if (tvLightCount != null) tvLightCount.setTextColor(TEXT_PRIMARY_DARK);
        if (sp.getBoolean(PREFIX + "accent_navi_info_enabled", false)) {
            int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
            if (tvDistNum != null) tvDistNum.setTextColor(accent);
            if (tvDistUnit != null) tvDistUnit.setTextColor(accent);
            if (tvRoadName != null) tvRoadName.setTextColor(accent);
            if (ivTurnIcon != null) ivTurnIcon.setColorFilter(accent);
            if (tvSpeedUnit != null) tvSpeedUnit.setTextColor(accent);
            if (tvDirection != null) tvDirection.setTextColor(accent);
            if (tvLightCount != null) tvLightCount.setTextColor(accent);
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
        if (tvSpeed != null) {
            ObjectAnimator anim = (ObjectAnimator) tvSpeed.getTag();
            if (anim != null) { anim.cancel(); tvSpeed.setTag(null); }
            tvSpeed.setAlpha(1f);
        }
        if (ivTurnIcon != null) {
            ObjectAnimator anim = (ObjectAnimator) ivTurnIcon.getTag();
            if (anim != null) { anim.cancel(); ivTurnIcon.setTag(null); }
            ivTurnIcon.setAlpha(1f);
        }
        isOverspeedBlinking = false;
    }
}
