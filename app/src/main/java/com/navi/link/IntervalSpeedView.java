package com.navi.link;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 区间测速胶囊：一行三列（当前车速 / 平均车速 / 剩余区间距离）。
 * 配色规则：
 * - 胶囊背景：平均车速 > 限制速度×1.1 → 红；否则主题蓝。
 * - 当前车速数字：胶囊为红时显示白色；胶囊为蓝时按原逻辑（> 限制速度 → 红/超速，否则提亮蓝）。
 */
public class IntervalSpeedView extends LinearLayout {

    private TextView tvTitle, tvCur, tvAvg, tvRemain;
    private int blueColor = 0xFF0099FF;          // 默认蓝，运行时由主题覆盖
    private final int redColor = Color.RED;
    private final int curBlueText = 0xFF80D8FF;  // 提亮蓝，保证在蓝底上可见
    private final GradientDrawable bg = new GradientDrawable();
    private boolean mRed = false;     // 胶囊是否处于红色（均速超限）状态
    private boolean mCurOver = false; // 当前车速是否超速

    public IntervalSpeedView(Context c) { this(c, null); }

    public IntervalSpeedView(Context c, AttributeSet a) { this(c, a, 0); }

    public IntervalSpeedView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        setOrientation(VERTICAL);
        LayoutInflater.from(c).inflate(R.layout.layout_interval_speed, this, true);
        tvTitle = findViewById(R.id.tv_interval_title);
        tvCur = findViewById(R.id.tv_interval_cur);
        tvAvg = findViewById(R.id.tv_interval_avg);
        tvRemain = findViewById(R.id.tv_interval_remain);
        tvTitle.setText("\uD83D\uDEA7 区间测速");
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(18));
        bg.setStroke(dpToPx(1), 0x40FFFFFF);   // 半透明白描边，提升胶囊质感
        setBackground(bg);
        setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        // 给数字预留固定宽度，区间测速出现时悬浮窗不因数字位数变化而抖动伸缩
        tvCur.setWidth((int) (tvCur.getPaint().measureText("888") + 0.5f) + dpToPx(2));
        tvCur.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvAvg.setWidth((int) (tvAvg.getPaint().measureText("888") + 0.5f) + dpToPx(2));
        tvAvg.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvRemain.setWidth((int) (tvRemain.getPaint().measureText("2.3公里") + 0.5f) + dpToPx(2));
        refreshColors();
    }

    public void setThemeBlue(int color) {
        blueColor = color;
        // 主题色若过暗则叠加提亮蓝作为背景，避免深色不可读
        refreshColors();
    }

    /**
     * @param curSpeed        当前车速（复用导航缓存车速）
     * @param avgSpeed        区间平均车速
     * @param limitSpeed      区间限制速度
     * @param endDistanceText 剩余区间距离文本，如 "2.3公里"
     */
    public void updateIntervalSpeed(int curSpeed, int avgSpeed, int limitSpeed, String endDistanceText) {
        boolean empty = (endDistanceText == null || endDistanceText.isEmpty()) && avgSpeed <= 0;
        if (empty) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        if (tvCur != null) tvCur.setText(String.valueOf(curSpeed));
        if (tvAvg != null) tvAvg.setText(String.valueOf(avgSpeed));
        if (tvRemain != null) tvRemain.setText(endDistanceText == null || endDistanceText.isEmpty() ? "--" : endDistanceText);
        mCurOver = limitSpeed > 0 && curSpeed > limitSpeed;
        mRed = limitSpeed > 0 && avgSpeed > limitSpeed * 1.1f;
        refreshColors();
    }

    private void refreshColors() {
        bg.setColor(mRed ? redColor : blueColor);
        int t = Color.WHITE;
        if (tvTitle != null) tvTitle.setTextColor(t);
        if (tvAvg != null) tvAvg.setTextColor(t);
        if (tvRemain != null) tvRemain.setTextColor(t);
        // 红状态：车速文字变白；蓝状态：恢复原来显示逻辑（超速红，正常提亮蓝）
        if (tvCur != null) {
            tvCur.setTextColor(mRed ? Color.WHITE : (mCurOver ? redColor : curBlueText));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
