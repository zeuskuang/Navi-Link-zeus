package com.navi.link;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.widget.ImageView;
import android.widget.TextView;

public class MinimalNaviWindow extends BaseFloatingWindow {

    private ImageView ivActionIconMin;
    private TextView tvMinSpeed;
    private TextView tvMinSpeedUnit;
    private TextView tvDistanceNumMin;
    private TextView tvDistanceUnitMin;
    private TextView tvRoadNameMin;

    private View llTrafficLightGroupMin;
    private ImageView ivLightIconMin;
    private ImageView ivLightArrowMin;
    private TextView tvLightTimeMin;

    private int themeColor = 0xFF4FC3F7;
    private boolean isOverspeedBlinking = false;

    public MinimalNaviWindow(Context context, View floatingView) {
        super(context, floatingView);
    }

    @Override
    protected void initViews() {
        ivActionIconMin = floatingView.findViewById(R.id.iv_action_icon_min);
        tvMinSpeed = floatingView.findViewById(R.id.tv_min_speed);
        tvMinSpeedUnit = floatingView.findViewById(R.id.tv_min_speed_unit);
        tvDistanceNumMin = floatingView.findViewById(R.id.tv_distance_num_min);
        tvDistanceUnitMin = floatingView.findViewById(R.id.tv_distance_unit_min);
        tvRoadNameMin = floatingView.findViewById(R.id.tv_road_name_min);

        llTrafficLightGroupMin = floatingView.findViewById(R.id.ll_traffic_light_group);
        if (llTrafficLightGroupMin != null) {
            ivLightIconMin = llTrafficLightGroupMin.findViewById(R.id.iv_light_icon);
            ivLightArrowMin = llTrafficLightGroupMin.findViewById(R.id.iv_light_arrow);
            tvLightTimeMin = llTrafficLightGroupMin.findViewById(R.id.tv_light_time);
        }

        themeColor = sp.getInt("theme_color", 0xFF4FC3F7);
    }

    @Override
    public void updateNaviInfo(
            int icon, String disNum, String disUnit, String actionStr,
            String roadName, String summaryStr, String eta,
            int progress, int curSpeed,
            int limitedSpeed, int cameraDist, int cameraSpeed,
            String endPoiName, int totalLightNum, int remainLightNum,
            String curRoadName, int carDirection
    ) {
        int turnIconRes = getTurnIconRes(icon);
        if (ivActionIconMin != null && turnIconRes != 0) {
            ivActionIconMin.setImageResource(turnIconRes);
        }
        if (tvMinSpeed != null) {
            tvMinSpeed.setText(String.valueOf(curSpeed));
            // 超速警告：限速>0 且 当前速度>限速 → 红色+闪烁
            boolean overspeed = limitedSpeed > 0 && curSpeed > limitedSpeed;
            if (overspeed) {
                tvMinSpeed.setTextColor(Color.RED);
                ObjectAnimator animator = (ObjectAnimator) tvMinSpeed.getTag();
                if (animator == null) {
                    ObjectAnimator newAnimator = ObjectAnimator.ofFloat(tvMinSpeed, "alpha", 1f, 0.3f);
                    newAnimator.setDuration(500);
                    newAnimator.setRepeatCount(ValueAnimator.INFINITE);
                    newAnimator.setRepeatMode(ValueAnimator.REVERSE);
                    newAnimator.start();
                    tvMinSpeed.setTag(newAnimator);
                    isOverspeedBlinking = true;
                }
            } else {
                ObjectAnimator animator = (ObjectAnimator) tvMinSpeed.getTag();
                if (animator != null) {
                    animator.cancel();
                    tvMinSpeed.setTag(null);
                }
                tvMinSpeed.setAlpha(1f);
                isOverspeedBlinking = false;
                // 恢复正常主题色
                int accentColor = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
                tvMinSpeed.setTextColor(accentColor);
            }
        }
        if (tvDistanceNumMin != null) {
            tvDistanceNumMin.setText(disNum);
        }
        if (tvDistanceUnitMin != null) {
            tvDistanceUnitMin.setText(disNumIsNow(disNum) ? "进入" : disUnit);
        }
        if (tvRoadNameMin != null) {
            tvRoadNameMin.setText(roadName);
        }
    }

    @Override
    public void updateCruiseInfo(int speed, String roadName, int cameraSpeed, int cameraDist) {
        // 极简导航不处理巡航
    }

    @Override
    public void updateTrafficLight(int status, int dir, int countdown) {
        if (llTrafficLightGroupMin == null) return;
        if (countdown <= 0) {
            llTrafficLightGroupMin.setVisibility(View.GONE);
            ObjectAnimator animator = (ObjectAnimator) llTrafficLightGroupMin.getTag();
            if (animator != null) {
                animator.cancel();
                llTrafficLightGroupMin.setTag(null);
            }
            llTrafficLightGroupMin.setAlpha(1f);
            return;
        }
        llTrafficLightGroupMin.setVisibility(View.VISIBLE);
        if (ivLightIconMin != null) {
            ivLightIconMin.setImageResource(getNaviLightIconRes(status));
        }
        if (ivLightArrowMin != null) {
            ivLightArrowMin.setImageResource(getNaviLightDirRes(dir));
        }
        if (tvLightTimeMin != null) {
            tvLightTimeMin.setText(String.valueOf(countdown));
        }

        if (countdown <= 5) {
            ObjectAnimator animator = (ObjectAnimator) llTrafficLightGroupMin.getTag();
            if (animator == null) {
                ObjectAnimator newAnimator = ObjectAnimator.ofFloat(llTrafficLightGroupMin, "alpha", 1f, 0.3f);
                newAnimator.setDuration(500);
                newAnimator.setRepeatCount(ValueAnimator.INFINITE);
                newAnimator.setRepeatMode(ValueAnimator.REVERSE);
                newAnimator.start();
                llTrafficLightGroupMin.setTag(newAnimator);
            }
        } else {
            ObjectAnimator animator = (ObjectAnimator) llTrafficLightGroupMin.getTag();
            if (animator != null) {
                animator.cancel();
                llTrafficLightGroupMin.setTag(null);
            }
            llTrafficLightGroupMin.setAlpha(1f);
        }
    }

    @Override
    public void updateLaneLines(String driveWayJson) {
        // 极简导航无车道线
    }

    @Override
    public void updateExitInfo(String exitName, String exitDirection) {
        // 极简导航无出口信息
    }

    @Override
    public void applyThemeColor(int themeColor) {
        this.themeColor = themeColor;
        int accentColor = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
        if (tvMinSpeed != null && !isOverspeedBlinking) {
            tvMinSpeed.setTextColor(accentColor);
        }
    }

    @Override
    public void applyDayNightTextColors(boolean isNightMode) {
        int textPrimary = isNightMode ? TEXT_PRIMARY_DARK : TEXT_PRIMARY_LIGHT;
        int textSecondary = isNightMode ? TEXT_SECONDARY_DARK : TEXT_SECONDARY_LIGHT;

        if (tvDistanceNumMin != null) tvDistanceNumMin.setTextColor(textPrimary);
        if (tvDistanceUnitMin != null) tvDistanceUnitMin.setTextColor(textSecondary);
        if (tvRoadNameMin != null) tvRoadNameMin.setTextColor(textSecondary);
        if (ivActionIconMin != null) ivActionIconMin.setColorFilter(textPrimary);
        if (tvMinSpeedUnit != null) tvMinSpeedUnit.setTextColor(textPrimary);
    }

    @Override
    public void resetToDefaultTextColors() {
        if (tvDistanceNumMin != null) tvDistanceNumMin.setTextColor(TEXT_PRIMARY_DARK);
        if (tvDistanceUnitMin != null) tvDistanceUnitMin.setTextColor(TEXT_SECONDARY_DARK);
        if (tvRoadNameMin != null) tvRoadNameMin.setTextColor(TEXT_SECONDARY_DARK);
        if (ivActionIconMin != null) ivActionIconMin.clearColorFilter();
        if (tvMinSpeedUnit != null) tvMinSpeedUnit.setTextColor(TEXT_PRIMARY_DARK);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (llTrafficLightGroupMin != null) {
            ObjectAnimator animator = (ObjectAnimator) llTrafficLightGroupMin.getTag();
            if (animator != null) {
                animator.cancel();
                llTrafficLightGroupMin.setTag(null);
            }
            llTrafficLightGroupMin.setAlpha(1f);
        }
        if (tvMinSpeed != null) {
            ObjectAnimator animator = (ObjectAnimator) tvMinSpeed.getTag();
            if (animator != null) {
                animator.cancel();
                tvMinSpeed.setTag(null);
            }
            tvMinSpeed.setAlpha(1f);
        }
        isOverspeedBlinking = false;
    }
}
