package com.shiroha.mmdskin.ui;

import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.RRect;

public final class SkiaBlurBackground {
    private static final int PANEL_FILL = 0x8C101722;
    private static final int PANEL_BORDER = 0x30FFFFFF;
    private static final int PANEL_SHEEN = 0x10FFFFFF;

    private static final int WHEEL_FILL = 0x78101822;
    private static final int WHEEL_SHEEN = 0x0CFFFFFF;

    private static final int CHIP_FILL = 0x98101822;
    private static final int CHIP_SHEEN = 0x10FFFFFF;

    private SkiaBlurBackground() {
    }

    public static void drawPanel(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY,
                                 float left, float top, float right, float bottom, float radius) {
        drawFlatRRect(canvas, left, top, right, bottom, radius, PANEL_FILL, PANEL_BORDER, 1.0f, PANEL_SHEEN);
    }

    public static void drawWheelGlass(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY,
                                      float left, float top, float right, float bottom, float radius) {
        drawFlatRRect(canvas, left, top, right, bottom, radius, WHEEL_FILL, 0x00000000, 0.0f, WHEEL_SHEEN);
    }

    public static void drawChip(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY,
                                float left, float top, float right, float bottom, float radius,
                                int overlayColor, int borderColor) {
        int fillColor = alpha(overlayColor) >= 0x40 ? overlayColor : CHIP_FILL;
        int strokeColor = alpha(borderColor) >= 0x20 ? borderColor : PANEL_BORDER;
        drawFlatRRect(canvas, left, top, right, bottom, radius, fillColor, strokeColor, 1.0f, CHIP_SHEEN);
    }

    public static void drawBlurredRRect(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY,
                                        float left, float top, float right, float bottom, float radius,
                                        float sigma, int overlayColor, int borderColor, float borderWidth) {
        int fillColor = alpha(overlayColor) >= 0x40 ? overlayColor : PANEL_FILL;
        int strokeColor = alpha(borderColor) >= 0x20 ? borderColor : PANEL_BORDER;
        drawFlatRRect(canvas, left, top, right, bottom, radius, fillColor, strokeColor, borderWidth, PANEL_SHEEN);
    }

    private static void drawFlatRRect(Canvas canvas,
                                      float left, float top, float right, float bottom, float radius,
                                      int fillColor, int borderColor, float borderWidth, int sheenColor) {
        RRect shell = RRect.makeLTRB(left, top, right, bottom, radius);
        float inset = Math.min(1.0f, Math.min((right - left) * 0.5f, (bottom - top) * 0.5f));
        float innerRadius = Math.max(0.0f, radius - inset);

        if (borderColor != 0 && borderWidth > 0.0f) {
            try (Paint border = strokePaint(borderColor, borderWidth)) {
                canvas.drawRRect(shell, border);
            }
        }

        if (fillColor != 0) {
            try (Paint fill = fillPaint(fillColor)) {
                canvas.drawRRect(
                        RRect.makeLTRB(left + inset, top + inset, right - inset, bottom - inset, innerRadius),
                        fill
                );
            }
        }

        if (sheenColor != 0) {
            float sheenBottom = top + (bottom - top) * 0.36f;
            if (sheenBottom > top + inset) {
                try (Paint sheen = fillPaint(sheenColor)) {
                    canvas.save();
                    canvas.clipRRect(shell, true);
                    canvas.drawRRect(
                            RRect.makeLTRB(left + inset, top + inset, right - inset, sheenBottom, innerRadius),
                            sheen
                    );
                    canvas.restore();
                }
            }
        }
    }

    private static Paint fillPaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        return paint;
    }

    private static Paint strokePaint(int color, float width) {
        Paint paint = fillPaint(color);
        paint.setMode(PaintMode.STROKE);
        paint.setStrokeWidth(width);
        return paint;
    }

    private static int alpha(int color) {
        return color >>> 24;
    }
}
