package com.navi.link;

import android.content.Context;
import android.graphics.Color;
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
    private CameraWarningView cameraGroup;
    private TmcProgressBar tmcProgressBar;

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
        cameraGroup = floatingView.findViewById(R.id.custom_navi_camera);
        tmcProgressBar = floatingView.findViewById(R.id.custom_navi_tmc);

        if (laneLineView != null) laneLineView.setSimpleMode(true);
        themeColor = sp.getInt("theme_color", 0xFF4FC3F7);
        applyCustomLayout();
    }

    public void applyCustomLayout() {
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
        applyElement(tmcProgressBar, "tmc");
    }

    private void applyElement(View view, String key) {
        if (view == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        float xDp = sp.getFloat(PREFIX + key + "_x", getDefaultX(key));
        float yDp = sp.getFloat(PREFIX + key + "_y", getDefaultY(key));
        boolean enabled = sp.getBoolean(PREFIX + key + "_enabled", true);
        int xPx = Math.round(xDp * density);
        int yPx = Math.round(yDp * density);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
            flp.leftMargin = xPx; flp.topMargin = yPx;
            view.setLayoutParams(flp);
        }
        view.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private float getDefaultX(String key) {
        switch (key) {
            case "tmc": return 100;
            case "direction": return 8; case "speed": return 8;
            case "turninfo": return 100; case "roadname": return 100;
            case "lightcount": return 280; case "trafficlight": return 280;
            case "lane": return 200; case "camera": return 320;
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

        if (tvSpeed != null) {
            tvSpeed.setText(String.valueOf(curSpeed));
            int limit = cameraSpeed > 0 ? cameraSpeed : limitedSpeed;
            boolean overspeed = sp.getBoolean("overspeed_warning_enabled", true) && limit > 0 && curSpeed > limit;
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

        if (tvRoadName != null) {
            if (sp.getBoolean(PREFIX + "roadname_enabled", true)) {
                tvRoadName.setText(roadName); tvRoadName.setVisibility(View.VISIBLE);
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
            } else tvLightCount.setVisibility(View.GONE);
        }

        mCameraType = cameraType; mCameraDist = cameraDist; mCameraSpeed = cameraSpeed;
        updateCameraDist();
    }

    @Override
    public void updateCruiseInfo(int speed, String roadName, int cameraType, int cameraSpeed, int cameraDist, int carDirection) {}

    @Override
    public void updateTrafficLight(int status, int dir, int countdown) {
        if (trafficLightView == null) return;
        if (countdown <= 0) { trafficLightView.clear(); return; }
        trafficLightView.setVisibility(View.VISIBLE);
        trafficLightView.setData(status, dir, countdown, true);
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
            if (sp.getBoolean(PREFIX + "lane_enabled", true)) laneLineView.updateLanes(driveWayJson);
            else laneLineView.clear();
        }
    }

    @Override public void updateExitInfo(String exitName, String exitDirection) {}

    @Override
    public void updateTmcData(String tmcJson) {
        if (tmcProgressBar != null) {
            tmcProgressBar.updateTmcData(tmcJson);
        }
    }

    @Override
    public void applyThemeColor(int themeColor) {
        this.themeColor = themeColor;
        int accent = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
        if (tvSpeed != null && !isOverspeedBlinking) tvSpeed.setTextColor(accent);
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
