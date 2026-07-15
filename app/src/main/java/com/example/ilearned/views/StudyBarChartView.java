package com.example.ilearned.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * StudyBarChartView
 *
 * Custom View that renders the weekly study hours bar chart.
 * Matches the reference image:
 *  - Y axis: Hours (0 to maxHours, auto-scaled)
 *  - X axis: Day labels (Mon, Tue, Wed, Thu, Fri, Sat, Sun)
 *  - Dark green bar  (#3B6255): studied ≥ half of dailySessionTarget
 *  - Light green bar (#CBDED3): studied < half of dailySessionTarget
 *  - Bars have rounded top corners
 *  - Horizontal grid lines at each Y interval
 *
 * Usage:
 *   chartView.setData(hoursArray, colorsArray, maxHours);
 */
public class StudyBarChartView extends View {

    // ── Data ──────────────────────────────────────────────────
    private float[] hoursPerDay   = new float[7];   // Mon=0 … Sun=6
    private int[]   barColors     = new int[7];
    private float   maxHours      = 10f;

    private static final String[] DAY_LABELS =
            {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    // ── Paint objects ─────────────────────────────────────────
    private final Paint barPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisLabelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Dimensions (set in onSizeChanged) ─────────────────────
    private float chartLeft, chartTop, chartRight, chartBottom;
    private float chartWidth, chartHeight;

    public StudyBarChartView(Context context) {
        super(context);
        init();
    }

    public StudyBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StudyBarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        gridPaint.setColor(0x33000000);          // light translucent grid lines
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setStyle(Paint.Style.STROKE);

        labelPaint.setColor(0xFF5A7268);          // textSecondary — day labels
        labelPaint.setTextSize(11f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        axisLabelPaint.setColor(0xFF5A7268);      // Y axis hour labels
        axisLabelPaint.setTextSize(10f * density);
        axisLabelPaint.setTextAlign(Paint.Align.RIGHT);
        axisLabelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setAntiAlias(true);
    }

    // ─────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────

    /**
     * @param hours     float[7] hours studied per day (Mon=0 … Sun=6)
     * @param colors    int[7]   bar colour per day
     * @param maxHours  Y-axis ceiling (auto-rounded up to next whole number)
     */
    public void setData(float[] hours, int[] colors, float maxHours) {
        this.hoursPerDay = hours;
        this.barColors   = colors;
        // Round max up to the nearest integer, minimum 1
        this.maxHours    = Math.max(1f, (float) Math.ceil(maxHours));
        invalidate();
    }

    // ─────────────────────────────────────────────────────────
    //  Layout
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float density = getResources().getDisplayMetrics().density;
        float paddingLeft   = 40f * density;   // space for Y labels
        float paddingRight  = 12f * density;
        float paddingTop    = 16f * density;
        float paddingBottom = 36f * density;   // space for X labels

        chartLeft   = paddingLeft;
        chartTop    = paddingTop;
        chartRight  = w - paddingRight;
        chartBottom = h - paddingBottom;
        chartWidth  = chartRight - chartLeft;
        chartHeight = chartBottom - chartTop;
    }

    // ─────────────────────────────────────────────────────────
    //  Drawing
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (chartWidth <= 0 || chartHeight <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // Number of Y grid lines (one per hour, max 10)
        int ySteps     = Math.min((int) maxHours, 10);
        float stepHrs  = maxHours / ySteps;
        float stepPx   = chartHeight / ySteps;

        // ── Horizontal grid lines + Y labels ──
        for (int i = 0; i <= ySteps; i++) {
            float y = chartBottom - (i * stepPx);

            // Grid line
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);

            // Y label (hours)
            String label = String.valueOf((int) Math.round(i * stepHrs));
            canvas.drawText(label, chartLeft - 6f * density, y + 4f * density, axisLabelPaint);
        }

        // ── Bars ──
        int numBars     = 7;
        float slotWidth = chartWidth / numBars;
        float barWidthFraction = 0.55f;              // bar occupies 55% of its slot
        float barW      = slotWidth * barWidthFraction;
        float cornerR   = 8f * density;

        for (int i = 0; i < numBars; i++) {
            float hours   = hoursPerDay[i];
            float barH    = (hours / maxHours) * chartHeight;
            float cx      = chartLeft + (i * slotWidth) + slotWidth / 2f;

            float left    = cx - barW / 2f;
            float right   = cx + barW / 2f;
            float top     = chartBottom - barH;
            float bottom  = chartBottom;

            // Clamp zero-height bars so they show as a tiny line
            if (hours == 0f) {
                top = chartBottom - 2f * density;
            }

            // Bar colour
            barPaint.setColor(barColors[i]);

            // Draw bar with rounded top corners only
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, cornerR, cornerR, barPaint);

            // Square off the bottom two corners by drawing a rectangle
            // over the bottom half of the rounded rect
            if (hours > 0f) {
                RectF bottomFix = new RectF(left, bottom - cornerR, right, bottom);
                canvas.drawRect(bottomFix, barPaint);
            }

            // Day label below X axis
            canvas.drawText(DAY_LABELS[i],
                    cx, chartBottom + 22f * density, labelPaint);
        }

        // ── Y axis title ──
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFF5A7268);
        titlePaint.setTextSize(10f * density);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        canvas.save();
        canvas.rotate(-90f, 12f * density, chartTop + chartHeight / 2f);
        canvas.drawText("Hours", 12f * density, chartTop + chartHeight / 2f, titlePaint);
        canvas.restore();
    }
}