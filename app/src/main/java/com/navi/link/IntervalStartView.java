package com.navi.link;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 区间起点距离胶囊：一行两列（"区间起点" 标签 + 距离数值）。
 * 配色复用区间测速的胶囊风格（主题蓝底白字）。
 */
public class IntervalStartView extends LinearLayout {

    private TextView tvLabel, tvDist;
    private int blueColor = 0xFF0099FF;          // 默认蓝，运行时由主题覆盖
    private final GradientDrawable bg = new GradientDrawable();

    public IntervalStartView(Context c) { this(c, null); }
    public IntervalStartView(Context c, AttributeSet a) { this(c, a, 0); }
    public IntervalStartView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(18));
        bg.setStroke(dpToPx(1), 0x40FFFFFF);   // 半透明白描边，与区间测速胶囊风格统一
        bg.setColor(blueColor);
        setBackground(bg);
        setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));

        tvLabel = new TextView(c);
        tvLabel.setText("\uD83C\uDFC1 区间起点");
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(14);
        tvLabel.setPadding(0, 0, dpToPx(6), 0);
        addView(tvLabel);

        tvDist = new TextView(c);
        tvDist.setText("0米");
        tvDist.setTextColor(Color.WHITE);
        tvDist.setTextSize(18);
        tvDist.setTypeface(null, Typeface.BOLD);
        // 预留固定宽度，距离数值位数变化时胶囊不抖动伸缩
        tvDist.setWidth((int) (tvDist.getPaint().measureText("9999米") + 0.5f) + dpToPx(2));
        tvDist.setGravity(Gravity.END);
        addView(tvDist);

        setVisibility(GONE);
    }

    public void setThemeBlue(int color) {
        blueColor = color;
        bg.setColor(blueColor);
    }

    /**
     * @param meters 区间起点距离（米）。<0 表示隐藏（区间测速结束/无数据）。
     */
    public void updateIntervalStart(int meters) {
        if (meters < 0) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        tvDist.setText(meters + "米");
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
