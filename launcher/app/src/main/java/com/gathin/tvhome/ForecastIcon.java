package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A small daytime icon for one forecast column: sun, cloud, rain, snow, or a storm. */
final class ForecastIcon extends View {
    private static final int SUN = 0xFFF5A623;
    private static final int CLOUD = 0xFFE6EAEF;
    private static final int RAIN = 0xFF4A90E2;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String kind = "";

    ForecastIcon(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setKind(String kind) {
        this.kind = kind == null ? "" : kind;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        if ("clear".equals(kind)) {
            sun(canvas, w * 0.5f, h * 0.5f, Math.min(w, h) * 0.22f);
        } else if ("partly".equals(kind)) {
            sun(canvas, w * 0.66f, h * 0.34f, Math.min(w, h) * 0.14f);
            cloud(canvas, w * 0.08f, h * 0.28f, w * 0.78f, h * 0.55f);
        } else if ("rain".equals(kind)) {
            cloud(canvas, w * 0.08f, h * 0.08f, w * 0.84f, h * 0.55f);
            drops(canvas, w, h);
        } else if ("snow".equals(kind)) {
            cloud(canvas, w * 0.08f, h * 0.08f, w * 0.84f, h * 0.55f);
            flakes(canvas, w, h);
        } else if ("storm".equals(kind)) {
            cloud(canvas, w * 0.08f, h * 0.06f, w * 0.84f, h * 0.5f);
            bolt(canvas, w * 0.46f, h * 0.48f, w * 0.22f, h * 0.42f);
        } else if ("fog".equals(kind)) {
            fog(canvas, w, h);
        } else if (!kind.isEmpty()) {
            cloud(canvas, w * 0.08f, h * 0.18f, w * 0.84f, h * 0.62f);
        }
    }

    private void sun(Canvas canvas, float cx, float cy, float radius) {
        paint.setColor(SUN);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, radius * 0.28f));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            float near = radius * 1.45f;
            float far = radius * 2.05f;
            canvas.drawLine(cx + (float) Math.cos(angle) * near, cy + (float) Math.sin(angle) * near,
                    cx + (float) Math.cos(angle) * far, cy + (float) Math.sin(angle) * far, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private void cloud(Canvas canvas, float left, float top, float width, float height) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CLOUD);
        float base = top + height * 0.62f;
        canvas.drawCircle(left + width * 0.32f, base - height * 0.16f, height * 0.28f, paint);
        canvas.drawCircle(left + width * 0.55f, base - height * 0.28f, height * 0.36f, paint);
        canvas.drawCircle(left + width * 0.76f, base - height * 0.12f, height * 0.24f, paint);
        canvas.drawRoundRect(left + width * 0.16f, base - height * 0.02f,
                left + width * 0.9f, base + height * 0.28f, height * 0.22f, height * 0.22f, paint);
    }

    private void drops(Canvas canvas, float width, float height) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(RAIN);
        float r = Math.min(width, height) * 0.045f;
        canvas.drawCircle(width * 0.32f, height * 0.78f, r, paint);
        canvas.drawCircle(width * 0.5f, height * 0.86f, r, paint);
        canvas.drawCircle(width * 0.68f, height * 0.78f, r, paint);
    }

    private void flakes(Canvas canvas, float width, float height) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        float r = Math.min(width, height) * 0.04f;
        canvas.drawCircle(width * 0.34f, height * 0.8f, r, paint);
        canvas.drawCircle(width * 0.5f, height * 0.88f, r, paint);
        canvas.drawCircle(width * 0.66f, height * 0.8f, r, paint);
    }

    private void bolt(Canvas canvas, float left, float top, float width, float height) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SUN);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(left + width * 0.55f, top);
        path.lineTo(left + width * 0.15f, top + height * 0.55f);
        path.lineTo(left + width * 0.48f, top + height * 0.55f);
        path.lineTo(left + width * 0.28f, top + height);
        path.lineTo(left + width * 0.95f, top + height * 0.38f);
        path.lineTo(left + width * 0.58f, top + height * 0.38f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void fog(Canvas canvas, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(CLOUD);
        paint.setStrokeWidth(Math.max(1f, height * 0.08f));
        canvas.drawLine(width * 0.18f, height * 0.32f, width * 0.82f, height * 0.32f, paint);
        canvas.drawLine(width * 0.28f, height * 0.5f, width * 0.78f, height * 0.5f, paint);
        canvas.drawLine(width * 0.18f, height * 0.68f, width * 0.7f, height * 0.68f, paint);
    }
}
