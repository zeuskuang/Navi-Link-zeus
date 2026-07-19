package com.navi.link;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * zeus 本机悬浮条：收到高德注入的巡航设施(红绿灯/摄像头)坐标后，在屏幕右上角显示。
 * 独立于导航/巡航悬浮窗，避免互相干扰；收到后停留数秒自动隐藏。
 */
public class CruiseFacilityOverlay {

    private static final long HIDE_DELAY_MS = 6000;
    private static CruiseFacilityOverlay instance;

    private final Context context;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView textView;
    private WindowManager.LayoutParams params;
    private boolean added = false;

    private CruiseFacilityOverlay(Context context) {
        this.context = context.getApplicationContext();
        this.wm = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized CruiseFacilityOverlay getInstance(Context context) {
        if (instance == null) {
            instance = new CruiseFacilityOverlay(context);
        }
        return instance;
    }

    public void show(double lng, double lat, double devLat, double devLng,
                      int distMeters, int type, int limitSpeed) {
        handler.post(() -> doShow(lng, lat, devLat, devLng, distMeters, type, limitSpeed));
    }

    private void doShow(double lng, double lat, double devLat, double devLng,
                        int distMeters, int type, int limitSpeed) {
        if (wm == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("📍 ").append(facilityTypeName(type));
        if (distMeters >= 0) sb.append("  到灯 ").append(distMeters).append("m");
        else sb.append("  到灯 ?");
        if (limitSpeed > 0) sb.append("  限速 ").append(limitSpeed);
        sb.append("\n车 ").append(formatCoord(devLat)).append(",").append(formatCoord(devLng))
                .append(" | 灯 ").append(formatCoord(lat)).append(",").append(formatCoord(lng));

        if (textView == null) {
            textView = new TextView(context);
            textView.setPadding(dp(10), dp(6), dp(10), dp(6));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            textView.setTextColor(Color.WHITE);
            textView.setMaxEms(18);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColor(0xCC000000);
            bg.setCornerRadius(dp(10));
            textView.setBackground(bg);
        }
        textView.setText(sb.toString());

        if (!added) {
            int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    -3);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = dp(12);
            params.y = dp(80);
            try {
                wm.addView(textView, params);
                added = true;
            } catch (Exception ignored) {
                return;
            }
        }

        // 重新测量后贴到右上角，避免与主悬浮窗(顶部居中)重叠
        textView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = textView.getMeasuredWidth();
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int margin = dp(12);
        params.x = Math.max(margin, screenW - w - margin);
        params.y = dp(80);
        try {
            wm.updateViewLayout(textView, params);
        } catch (Exception ignored) {
        }
        textView.setVisibility(View.VISIBLE);

        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, HIDE_DELAY_MS);
    }

    private final Runnable hideRunnable = () -> {
        if (textView != null) textView.setVisibility(View.GONE);
    };

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private String formatCoord(double c) {
        return String.format(java.util.Locale.US, "%.6f", c);
    }

    private String facilityTypeName(int type) {
        switch (type) {
            case 2:
            case 15:
                return "红绿灯";
            case 22:
            case 1001:
                return "红绿灯(左)";
            case 6:
            case 20:
                return "自行车";
            case 4:
            case 16:
                return "公交";
            case 11:
            case 1099:
                return "ETC";
            case 3:
            case 21:
            case 1017:
                return "停车";
            case 19:
            case 1005:
                return "手机测速";
            case 12:
            case 1030:
                return "压线";
            case 5:
                return "应急车道";
            case 26:
            case 1024:
                return "铁路道口";
            case 24:
            case 1021:
                return "声呐";
            case 13:
            case 1015:
                return "步行";
            case 29:
            case 1029:
                return "HOV";
            case 28:
            case 1028:
                return "空间";
            case 27:
            case 1011:
                return "尾号";
            case 30:
            case 1012:
                return "环保";
            case 25:
            case 1016:
                return "逆向";
            case 18:
            case 1002:
                return "安全带";
            default:
                return "设施#" + type;
        }
    }
}
