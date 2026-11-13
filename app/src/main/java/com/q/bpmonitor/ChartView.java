package com.q.bpmonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 自绘折线图（零依赖，Canvas 实现） */
public class ChartView extends View {

    public static class Series {
        public final int color;
        public final List<float[]> pts = new ArrayList<>(); // 每项 float[2] = [xIndex, value]
        public Series(int color) { this.color = color; }
    }

    private int xCount = 0;
    private String[] xLabels = new String[0];
    private final List<Series> series = new ArrayList<>();

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density = 1f;

    public ChartView(Context c) { super(c); init(); }
    public ChartView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density);
        gridPaint.setColor(0xFFEDEFF3);

        axisTextPaint.setTextSize(10 * density);
        axisTextPaint.setColor(0xFF8A93A2);

        emptyPaint.setTextSize(14 * density);
        emptyPaint.setColor(0xFF8A93A2);
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.4f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        dotStrokePaint.setStyle(Paint.Style.STROKE);
        dotStrokePaint.setStrokeWidth(2f * density);
        dotStrokePaint.setColor(0xFFFFFFFF);

        areaPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(int xCount, String[] xLabels, List<Series> series) {
        this.xCount = xCount;
        this.xLabels = xLabels == null ? new String[0] : xLabels;
        this.series.clear();
        if (series != null) this.series.addAll(series);
        invalidate();
    }

    public boolean isEmpty() {
        for (Series s : series) if (!s.pts.isEmpty()) return false;
        return true;
    }

    private float dp(float v) { return v * density; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float pdL = dp(40), pdR = dp(12), pdT = dp(14), pdB = dp(26);
        float plotW = w - pdL - pdR, plotH = h - pdT - pdB;

        if (isEmpty() || xCount < 1) {
            emptyPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据", w / 2f, h / 2f, emptyPaint);
            return;
        }

        // 计算 Y 轴范围
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (Series s : series)
            for (float[] p : s.pts) { min = Math.min(min, p[1]); max = Math.max(max, p[1]); }
        float span = max - min;
        if (span < 12) { float mid = (min + max) / 2f; min = mid - 6; max = mid + 6; }
        float pad = span * 0.12f;
        min -= pad; max += pad;

        // 横向网格 + Y 标签
        int rows = 4;
        for (int i = 0; i <= rows; i++) {
            float t = i / (float) rows;
            float y = pdT + plotH * (1 - t);
            canvas.drawLine(pdL, y, w - pdR, y, gridPaint);
            int val = Math.round(min + (max - min) * t);
            axisTextPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.valueOf(val), pdL - dp(6), y + dp(4), axisTextPaint);
        }

        // X 标签（最多约 7 个）
        int labelStep = Math.max(1, (int) Math.ceil(xCount / 7.0));
        axisTextPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < xCount; i += labelStep) {
            String lb = xLabels[i];
            if (lb == null) continue;
            canvas.drawText(lb, xToPx(i, pdL, plotW), h - dp(8), axisTextPaint);
        }

        // 各系列折线
        for (Series s : series) {
            if (s.pts.isEmpty()) continue;
            linePaint.setColor(s.color);
            dotPaint.setColor(s.color);

            if (s.pts.size() == 1) {
                float[] p = s.pts.get(0);
                drawDot(canvas, xToPx(p[0], pdL, plotW), yToPx(p[1], pdT, plotH, min, max), s.color);
                continue;
            }
            Path path = new Path();
            boolean first = true;
            for (float[] p : s.pts) {
                float x = xToPx(p[0], pdL, plotW), y = yToPx(p[1], pdT, plotH, min, max);
                if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
            }
            canvas.drawPath(path, linePaint);
            for (float[] p : s.pts)
                drawDot(canvas, xToPx(p[0], pdL, plotW), yToPx(p[1], pdT, plotH, min, max), s.color);
        }
    }

    private void drawDot(Canvas c, float x, float y, int color) {
        c.drawCircle(x, y, dp(3.6f), dotPaint);
        c.drawCircle(x, y, dp(3.6f), dotStrokePaint);
        dotPaint.setColor(color);
    }

    private float xToPx(float idx, float pdL, float plotW) {
        if (xCount <= 1) return pdL + plotW / 2f;
        return pdL + plotW * (idx / (xCount - 1));
    }

    private float yToPx(float v, float pdT, float plotH, float min, float max) {
        float t = (v - min) / (max - min);
        return pdT + plotH * (1 - Math.max(0f, Math.min(1f, t)));
    }
}
