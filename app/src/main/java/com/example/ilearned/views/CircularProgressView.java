package com.example.ilearned.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

//CircularProgressView

public class CircularProgressView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private RectF  oval;
    private int    progress = 0;     // 0–100
    private float  strokeWidth;

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidth = dpToPx(14);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(Color.parseColor("#3A3A3A")); // dark grey track

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.parseColor("#4CAF50")); // green

        oval = new RectF();
    }

    public void setProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
        invalidate(); // redraw
    }

    public int getProgress() {
        return progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = strokeWidth / 2f + 4;
        oval.set(padding, padding,
                getWidth() - padding, getHeight() - padding);

        // Draw full grey track
        canvas.drawOval(oval, trackPaint);

        // Draw green arc — starts at top (-90°), sweeps clockwise
        float sweepAngle = (progress / 100f) * 360f;
        canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint);
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
}