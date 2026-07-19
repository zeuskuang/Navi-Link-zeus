package com.navi.link;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 复合胶囊：上部红绿灯已停用，仅保留下部红绿灯违章电子眼距离行
 * （cameraType=12 / 2 且距离>0 时显示，距离+20米）。整体显隐仅由下部距离行决定。
 */
public class TrafficLightCamView extends LinearLayout {

    private TrafficLightView lightView;
    private View camRow;
    private TextView camDist;
    private SharedPreferences sp;
    private int cachedRedLightDist = -1;   // 缓存最近一次有效的红绿灯违章距离，避免中途闪烁消失

    public TrafficLightCamView(Context context) {
        super(context);
        init(context);
    }

    public TrafficLightCamView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        sp = context.getSharedPreferences("floating_config", Context.MODE_PRIVATE);
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        // 整体胶囊背景，由外层统一提供，避免与内嵌红绿灯胶囊重复
        setBackgroundResource(R.drawable.bg_traffic_light_capsule);
        int pad = dpToPx(6);
        setPadding(pad, pad, pad, pad);

        LayoutInflater.from(context).inflate(R.layout.traffic_light_cam, this, true);
        lightView = findViewById(R.id.tlc_light);
        camRow = findViewById(R.id.tlc_cam_row);
        camDist = findViewById(R.id.tlc_cam_dist);
        if (lightView != null) {
            lightView.setEmbedded(true);
            lightView.setVisibility(View.GONE);   // 上部红绿灯已停用
        }

        // 数据驱动显隐，初始隐藏
        setVisibility(View.GONE);
    }

    /** 上部红绿灯已停用：始终隐藏，不再响应红绿灯倒计时数据 */
    public void updateTrafficLight(int status, int dir, int countdown, boolean isNavi) {
        if (lightView != null) {
            lightView.clear();
            lightView.setVisibility(View.GONE);
        }
        refreshVisibility();
    }

    /** 下部：红绿灯违章电子眼（cameraType=12 / 2，即违章拍照/闯红灯拍照）且距离>0 时显示 (距离+20)米。
     *  粘性显示：接近途中高德偶尔不把红绿灯作为「下一个摄像头」下发，此时保留上一次有效距离，避免闪烁消失；
     *  仅当确实驶过（距离归零 / 下一个摄像头比缓存更远 / 前方无摄像头）时才隐藏。 */
    public void updateRedLightCamera(int cameraType, int cameraDist, int cameraSpeed) {
        if (camRow == null || camDist == null) return;
        boolean isRedLight = (cameraType == 12 || cameraType == 2);   // 违章拍照 / 闯红灯拍照
        if (isRedLight && cameraDist > 0) {
            cachedRedLightDist = cameraDist;
            camDist.setText((cameraDist + 20) + "米");
            camRow.setVisibility(View.VISIBLE);
        } else if (isRedLight) {
            // 距离归零：已通过该红绿灯
            cachedRedLightDist = -1;
            camRow.setVisibility(View.GONE);
        } else {
            // 非红绿灯违章类型
            if (cameraDist <= 0 || cameraDist > cachedRedLightDist) {
                // 前方已无摄像头，或下一个摄像头比缓存的红绿灯更远（说明已驶过该红绿灯）→ 隐藏
                cachedRedLightDist = -1;
                camRow.setVisibility(View.GONE);
            }
            // 否则（距离比缓存更小）视为当帧未携带红绿灯数据，保留上一次有效距离
        }
        refreshVisibility();
    }

    /** 清空全部（仅下部隐藏） */
    public void clear() {
        if (lightView != null) {
            lightView.clear();
            lightView.setVisibility(View.GONE);
        }
        cachedRedLightDist = -1;
        if (camRow != null) camRow.setVisibility(View.GONE);
        setVisibility(View.GONE);
    }

    /** 仅由下部距离行决定整体显隐 */
    private void refreshVisibility() {
        boolean down = camRow != null && camRow.getVisibility() == View.VISIBLE;
        setVisibility(down ? View.VISIBLE : View.GONE);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
