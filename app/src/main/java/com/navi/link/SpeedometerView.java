package com.navi.link;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

public class SpeedometerView extends View {

    private static final float MAX_SPEED = 200f;
    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private float currentSpeed = 0f;
    private int gaugeSize = 0;
    private ValueAnimator needleAnimator;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dpToPxF(8));
        arcPaint.setColor(0x33FFFFFF);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dpToPxF(3));
        tickPaint.setColor(0xAAFFFFFF);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0x99FFFFFF);

        speedPaint.setTextAlign(Paint.Align.CENTER);
        speedPaint.setColor(Color.WHITE);
        speedPaint.setFakeBoldText(true);

        needlePaint.setStyle(Paint.Style.FILL);
        needlePaint.setStrokeWidth(dpToPxF(3));
        needlePaint.setColor(0xFFFF4F4F);

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(0xAAFFFFFF);
    }

    public void setSpeed(float speed) {
        if (speed < 0) speed = 0;
        if (speed > MAX_SPEED) speed = MAX_SPEED;

        // 取消正在运行的动画
        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }

        float startSpeed = currentSpeed;
        float targetSpeed = speed;

        // 小幅度变化直接跳转
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

    public void setThemeColor(int color) {}

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
        // 外圈加大：紧贴视图边界
        float radius = Math.min(cx, cy);
        float textScale = radius / dpToPxF(60);

        labelPaint.setTextSize(spToPx(11) * textScale);
        speedPaint.setTextSize(spToPx(36) * textScale);

        // 弧形外圈（加粗）
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, arcPaint);

        // 刻度线缩短(从10dp→7dp)+ 刻度数字移到端点附近，全部往外扩大，超过指针定点最大圆
        float tickLen = dpToPx(7) * textScale;
        float labelR = radius - tickLen - dpToPx(8) * textScale; // 数字最大外圈在刻度线内，不重叠
        for (int s = 0; s <= MAX_SPEED; s += 10) {
            float angle = START_ANGLE + (s / MAX_SPEED) * SWEEP_ANGLE;
            double rad = Math.toRadians(angle);
            float x1 = cx + radius * (float) Math.cos(rad);
            float y1 = cy + radius * (float) Math.sin(rad);
            float x2 = cx + (radius - tickLen) * (float) Math.cos(rad);
            float y2 = cy + (radius - tickLen) * (float) Math.sin(rad);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
            if (s % 20 == 0) {
                float lx = cx + labelR * (float) Math.cos(rad);
                float ly = cy + labelR * (float) Math.sin(rad);
                canvas.drawText(String.valueOf(s), lx, ly + spToPx(4) * textScale, labelPaint);
            }
        }

        // 指针加长到接近刻度数字
        float angle = START_ANGLE + (currentSpeed / MAX_SPEED) * SWEEP_ANGLE;
        double rad = Math.toRadians(angle);
        float needleLen = radius * 0.72f;
        float nx = cx + needleLen * (float) Math.cos(rad);
        float ny = cy + needleLen * (float) Math.sin(rad);
        canvas.drawLine(cx, cy, nx, ny, needlePaint);

        // 中心圆点
        canvas.drawCircle(cx, cy, dpToPx(3) * textScale, centerPaint);

        // 中央速度数字（在仪表内部）
        float innerTextR = radius * 0.55f;
        canvas.drawText(String.valueOf((int) currentSpeed),
                cx, cy + innerTextR * 0.15f, speedPaint);
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
