package com.example.ilearned.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CircularTimerView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float strokeWidth;
    private float progress = 1f;

    public CircularTimerView(Context context) {
        super(context);
        init(context);
    }

    public CircularTimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CircularTimerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        strokeWidth = 8f * density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.parseColor("#EAF1EA"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.parseColor("#1F6B3A"));

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#DDF1E1"));
    }

    public void setProgress(float p) {
        this.progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        float pad = strokeWidth / 2f + 4f;

        float left = (w - size) / 2f + pad;
        float top = (h - size) / 2f + pad;
        float right = left + size - pad * 2f;
        float bottom = top + size - pad * 2f;
        arcRect.set(left, top, right, bottom);

        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        float radius = (right - left) / 2f - strokeWidth / 2f;

        progressPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeWidth(strokeWidth);

        canvas.drawCircle(cx, cy, radius, fillPaint);
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint);

        float sweep = 360f * progress;
        if (sweep > 0f) {
            canvas.drawArc(arcRect, -90f, sweep, false, progressPaint);
        }
    }
}
