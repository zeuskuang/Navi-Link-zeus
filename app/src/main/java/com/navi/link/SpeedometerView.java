package com.navi.link;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

public class SpeedometerView extends View {

    private static final float MAX_SPEED = 200f;
    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;
    private static final float REDLINE_FRAC = 0.85f;   // 85% 以上为红线区

    private float currentSpeed = 0f;
    private int gaugeSize = 0;
    private ValueAnimator needleAnimator;

    private int themeColor = 0;
    private boolean hasTheme = false;

    private final Paint bezelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    public SpeedometerView(Context context) {
        super(context);
        init();
    }

    public SpeedometerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bezelPaint.setStyle(Paint.Style.STROKE);
        bezelPaint.setStrokeWidth(dpToPxF(7));
        bezelPaint.setColor(0x4DFFFFFF);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dpToPxF(10));
        trackPaint.setColor(0x1FFFFFFF);

        redlinePaint.setStyle(Paint.Style.STROKE);
        redlinePaint.setStrokeWidth(dpToPxF(10));
        redlinePaint.setColor(0x40FF5252);

        progPaint.setStyle(Paint.Style.STROKE);
        progPaint.setStrokeWidth(dpToPxF(10));
        progPaint.setStrokeCap(Paint.Cap.ROUND);

        tickPaint.setStyle(Paint.Style.STROKE);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xB3FFFFFF);

        speedPaint.setTextAlign(Paint.Align.CENTER);
        speedPaint.setColor(Color.WHITE);
        speedPaint.setFakeBoldText(true);

        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setColor(0x99FFFFFF);

        needlePaint.setStyle(Paint.Style.FILL);
        needlePaint.setColor(0xFFFF4F4F);

        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setColor(0xCC222222);

        hubDotPaint.setStyle(Paint.Style.FILL);
        hubDotPaint.setColor(0xFF4F4F4F);
    }

    public void setSpeed(float speed) {
        if (speed < 0) speed = 0;
        if (speed > MAX_SPEED) speed = MAX_SPEED;

        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }

        float startSpeed = currentSpeed;
        float targetSpeed = speed;

        // 小幅度变化直接跳转，避免抖动
        if (Math.abs(targetSpeed - startSpeed) < 1f) {
            currentSpeed = targetSpeed;
            invalidate();
            return;
        }

        // 模拟真实车速表的缓动过渡
        needleAnimator = ValueAnimator.ofFloat(startSpeed, targetSpeed);
        needleAnimator.setDuration(400);
        needleAnimator.setInterpolator(new DecelerateInterpolator());
        needleAnimator.addUpdateListener(animation -> {
            currentSpeed = (float) animation.getAnimatedValue();
            invalidate();
        });
        needleAnimator.start();
    }

    /** 超速时中央数字变红，正常恢复白色 */
    public void setOverspeed(boolean overspeed) {
        speedPaint.setColor(overspeed ? Color.RED : Color.WHITE);
        invalidate();
    }

    /** 设置主题色：用作进度弧渐变的起始色（未设置则用默认 绿→黄→红） */
    public void setThemeColor(int color) {
        this.themeColor = color;
        this.hasTheme = true;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int specWidth = MeasureSpec.getSize(widthMeasureSpec);
        int specHeight = MeasureSpec.getSize(heightMeasureSpec);
        int specMode = MeasureSpec.getMode(widthMeasureSpec);
        int size = Math.min(specWidth, specHeight);

        // UNSPECIFIED 时从 LayoutParams 读取物理缩放后的实际尺寸
        if (specMode == MeasureSpec.UNSPECIFIED || size == 0) {
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null && lp.width > 0) {
                size = lp.width;
            } else {
                size = dpToPx(160);
            }
        }
        gaugeSize = size;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxR = Math.min(cx, cy);
        float scale = Math.max(maxR / dpToPxF(100), 0.5f);   // 以 100dp 仪表为基准等比缩放

        // —— 边界避让：所有元素半径都基于 maxR 向内收缩，绝不触及视图边缘 ——
        float bezelW = dpToPxF(7) * scale;
        float outerPad = dpToPxF(3);
        float R_outer = maxR - outerPad - bezelW / 2f;

        float trackW = dpToPxF(10) * scale;
        float R_track = R_outer - bezelW / 2f - trackW / 2f;

        // 1) 外圈金属边框
        bezelPaint.setStrokeWidth(bezelW);
        canvas.drawCircle(cx, cy, R_outer, bezelPaint);

        // 2) 背景轨道
        arcRect.set(cx - R_track, cy - R_track, cx + R_track, cy + R_track);
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        // 3) 红线区（高速度段）
        float redlineStart = REDLINE_FRAC * SWEEP_ANGLE;
        canvas.drawArc(arcRect, START_ANGLE + redlineStart, SWEEP_ANGLE - redlineStart, false, redlinePaint);

        // 4) 进度弧（沿弧渐变，模拟真实转速/速度表）
        float frac = Math.min(currentSpeed / MAX_SPEED, 1f);
        progPaint.setShader(buildGradient(cx, cy));
        canvas.save();
        canvas.rotate(START_ANGLE, cx, cy);   // 旋转坐标系，使渐变 0 点对齐仪表起点
        arcRect.set(cx - R_track, cy - R_track, cx + R_track, cy + R_track);
        canvas.drawArc(arcRect, 0, Math.max(frac * SWEEP_ANGLE, 0.001f), false, progPaint);
        canvas.restore();
        progPaint.setShader(null);

        // 5) 刻度 + 数字
        float tickOuter = R_track - trackW / 2f - dpToPxF(2) * scale;
        float majorLen = dpToPxF(11) * scale;
        float minorLen = dpToPxF(5) * scale;
        labelPaint.setTextSize(spToPx(11) * Math.max(scale, 0.7f));
        float labelR = tickOuter - majorLen - dpToPxF(3) * scale;
        for (int s = 0; s <= MAX_SPEED; s += 10) {
            float angle = START_ANGLE + (s / MAX_SPEED) * SWEEP_ANGLE;
            double rad = Math.toRadians(angle);
            boolean major = (s % 20 == 0);
            float len = major ? majorLen : minorLen;
            tickPaint.setStrokeWidth(major ? dpToPxF(3) : dpToPxF(2));
            tickPaint.setColor(major ? 0xE6FFFFFF : 0x80FFFFFF);
            float x1 = cx + tickOuter * (float) Math.cos(rad);
            float y1 = cy + tickOuter * (float) Math.sin(rad);
            float x2 = cx + (tickOuter - len) * (float) Math.cos(rad);
            float y2 = cy + (tickOuter - len) * (float) Math.sin(rad);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
            if (major) {
                float lx = cx + labelR * (float) Math.cos(rad);
                float ly = cy + labelR * (float) Math.sin(rad);
                canvas.drawText(String.valueOf(s), lx, ly + labelPaint.getTextSize() * 0.35f, labelPaint);
            }
        }

        // 6) 指针（三角针 + 尾针），带圆角端点
        float needleLen = R_track - trackW / 2f - dpToPxF(8) * scale;
        float tailLen = R_outer * 0.18f;
        float angle = START_ANGLE + frac * SWEEP_ANGLE;
        double rad = Math.toRadians(angle);
        float nx = cx + needleLen * (float) Math.cos(rad);
        float ny = cy + needleLen * (float) Math.sin(rad);
        float tx = cx - tailLen * (float) Math.cos(rad);
        float ty = cy - tailLen * (float) Math.sin(rad);
        float px = (float) Math.cos(rad + Math.PI / 2);
        float py = (float) Math.sin(rad + Math.PI / 2);
        float halfW = dpToPxF(3.5f) * scale;
        Path needle = new Path();
        needle.moveTo(nx, ny);
        needle.lineTo(cx + px * halfW, cy + py * halfW);
        needle.lineTo(tx, ty);
        needle.lineTo(cx - px * halfW, cy - py * halfW);
        needle.close();
        canvas.drawPath(needle, needlePaint);

        // 7) 中心轴帽
        float hubR = R_outer * 0.10f;
        canvas.drawCircle(cx, cy, hubR, hubPaint);
        canvas.drawCircle(cx, cy, hubR * 0.45f, hubDotPaint);

        // 8) 中央数字 + 单位（置于最上层，始终清晰）
        float numSize = R_outer * 0.40f;
        speedPaint.setTextSize(numSize);
        unitPaint.setTextSize(numSize * 0.24f);
        float numY = cy + numSize * 0.34f;
        canvas.drawText(String.valueOf((int) currentSpeed), cx, numY, speedPaint);
        float unitY = numY + numSize * 0.40f;
        canvas.drawText("km/h", cx, unitY, unitPaint);
    }

    private SweepGradient buildGradient(float cx, float cy) {
        int c0, c1, c2, c3;
        if (hasTheme && themeColor != 0) {
            c0 = themeColor;
            c1 = blend(themeColor, Color.YELLOW, 0.5f);
            c2 = blend(themeColor, Color.RED, 0.5f);
            c3 = Color.RED;
        } else {
            c0 = 0xFF4CAF50;   // 绿
            c1 = 0xFFFFEB3B;   // 黄
            c2 = 0xFFFF9800;   // 橙
            c3 = 0xFFF44336;   // 红
        }
        return new SweepGradient(cx, cy,
                new int[]{c0, c1, c2, c3},
                new float[]{0f, 0.55f, 0.80f, 1.0f});
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return Color.argb(0xFF, r, g, bl);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private float dpToPxF(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private int spToPx(int sp) {
        return Math.round(sp * getResources().getDisplayMetrics().scaledDensity);
    }
}
