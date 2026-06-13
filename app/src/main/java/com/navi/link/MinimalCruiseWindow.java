package com.navi.link;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MinimalCruiseWindow extends BaseFloatingWindow {

    private TextView tvCruiseSpeed;
    private TextView tvCruiseRoadName;
    private LinearLayout llTrafficLightsContainer;
    private View tvCruiseMargin;

    public MinimalCruiseWindow(Context context, View floatingView) {
        super(context, floatingView);
    }

    @Override
    protected void initViews() {
        tvCruiseSpeed = floatingView.findViewById(R.id.tv_cruise_speed);
        tvCruiseRoadName = floatingView.findViewById(R.id.tv_cruise_road_name);
        llTrafficLightsContainer = floatingView.findViewById(R.id.ll_traffic_lights_container);
        tvCruiseMargin = floatingView.findViewById(R.id.tv_margin);
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
        // 极简巡航不处理导航数据
    }

    @Override
    public void updateCruiseInfo(int speed, String roadName, int cameraSpeed, int cameraDist) {
        if (tvCruiseSpeed != null) {
            tvCruiseSpeed.setText(String.valueOf(speed));
        }
        if (tvCruiseRoadName != null && roadName != null) {
            tvCruiseRoadName.setText(roadName);
        }
    }

    @Override
    public void updateTrafficLight(int status, int dir, int countdown) {
        // 巡航使用 updateCruiseTrafficLights
    }

    @Override
    public void updateCruiseTrafficLights(JSONArray lightsArray) {
        LinearLayout container = llTrafficLightsContainer;
        if (container == null) return;

        int count = lightsArray != null ? lightsArray.length() : 0;
        int childCount = container.getChildCount();

        if (count == 0) {
            container.setVisibility(View.GONE);
            if (tvCruiseMargin != null) {
                tvCruiseMargin.setVisibility(View.GONE);
            }
            if (childCount > 0) container.removeAllViews();
            return;
        }

        if (count != childCount) {
            for (int i = 0; i < childCount; i++) {
                View child = container.getChildAt(i);
                if (child != null) {
                    ObjectAnimator animator = (ObjectAnimator) child.getTag();
                    if (animator != null) {
                        animator.cancel();
                        child.setTag(null);
                    }
                }
            }
            container.removeAllViews();
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
            int layoutRes = (count >= 3)
                    ? R.layout.item_cruise_traffic_light_small
                    : R.layout.item_cruise_traffic_light;
            for (int i = 0; i < count; i++) {
                try {
                    JSONObject lightObj = lightsArray.getJSONObject(i);
                    View lightView = inflater.inflate(layoutRes, container, false);
                    float scale = FloatingWindowManager.getInstance().getScale();
                    if (scale != 1.0f) {
                        scaleViewRecursive(lightView, scale);
                    }
                    updateSingleLightView(lightView, lightObj);
                    container.addView(lightView);
                } catch (Exception ignored) {
                }
            }
        } else {
            for (int i = 0; i < count; i++) {
                try {
                    updateSingleLightView(container.getChildAt(i), lightsArray.getJSONObject(i));
                } catch (Exception ignored) {
                }
            }
        }
        container.setVisibility(View.VISIBLE);
        if (tvCruiseMargin != null) {
            tvCruiseMargin.setVisibility(View.VISIBLE);
        }
        
        boolean allGone = true;
        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i).getVisibility() == View.VISIBLE) {
                allGone = false;
                break;
            }
        }
        if (allGone) {
            container.setVisibility(View.GONE);
            if (tvCruiseMargin != null) {
                tvCruiseMargin.setVisibility(View.GONE);
            }
        }
    }

    private void updateSingleLightView(View view, JSONObject jsonObj) throws Exception {
        int status = jsonObj.getInt("status");
        int countdown = jsonObj.getInt("countdown");
        int dir = jsonObj.getInt("dir");

        ImageView lightIcon = view.findViewById(R.id.iv_light_icon);
        ImageView lightArrow = view.findViewById(R.id.iv_light_arrow);
        TextView lightTime = view.findViewById(R.id.tv_light_time);

        if (lightIcon != null) lightIcon.setImageResource(getCruiseLightIconRes(status));
        if (lightArrow != null) lightArrow.setImageResource(getCruiseLightDirRes(dir));
        if (lightTime != null) lightTime.setText(String.valueOf(countdown));

        if (countdown > 0) {
            view.setVisibility(View.VISIBLE);
            if (countdown <= 5) {
                ObjectAnimator animator = (ObjectAnimator) view.getTag();
                if (animator == null) {
                    ObjectAnimator newAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.3f);
                    newAnimator.setDuration(500);
                    newAnimator.setRepeatCount(ValueAnimator.INFINITE);
                    newAnimator.setRepeatMode(ValueAnimator.REVERSE);
                    newAnimator.start();
                    view.setTag(newAnimator);
                }
            } else {
                ObjectAnimator animator = (ObjectAnimator) view.getTag();
                if (animator != null) {
                    animator.cancel();
                    view.setTag(null);
                }
                view.setAlpha(1f);
            }
        } else {
            view.setVisibility(View.GONE);
            ObjectAnimator animator = (ObjectAnimator) view.getTag();
            if (animator != null) {
                animator.cancel();
                view.setTag(null);
            }
            view.setAlpha(1f);
        }
    }

    @Override
    public void updateLaneLines(String driveWayJson) {
        // 极简巡航无车道线
    }

    @Override
    public void updateExitInfo(String exitName, String exitDirection) {
        // 极简巡航无出口信息
    }

    @Override
    public void applyThemeColor(int themeColor) {
        int accentColor = isDarkThemeColor(themeColor) ? Color.WHITE : themeColor;
        if (tvCruiseSpeed != null) {
            tvCruiseSpeed.setTextColor(accentColor);
        }
    }

    @Override
    public void applyDayNightTextColors(boolean isNightMode) {
        int textSecondary = isNightMode ? TEXT_SECONDARY_DARK : TEXT_SECONDARY_LIGHT;
        if (tvCruiseRoadName != null) {
            tvCruiseRoadName.setTextColor(textSecondary);
        }
    }

    @Override
    public void resetToDefaultTextColors() {
        if (tvCruiseRoadName != null) {
            tvCruiseRoadName.setTextColor(TEXT_SECONDARY_DARK);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (llTrafficLightsContainer != null) {
            for (int i = 0; i < llTrafficLightsContainer.getChildCount(); i++) {
                View child = llTrafficLightsContainer.getChildAt(i);
                if (child != null) {
                    ObjectAnimator animator = (ObjectAnimator) child.getTag();
                    if (animator != null) {
                        animator.cancel();
                        child.setTag(null);
                    }
                }
            }
            llTrafficLightsContainer.removeAllViews();
        }
    }
}
