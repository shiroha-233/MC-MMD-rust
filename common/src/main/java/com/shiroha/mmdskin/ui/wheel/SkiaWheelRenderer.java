package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.skia.BackendRenderTarget;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ColorSpace;
import org.jetbrains.skia.DirectContext;
import org.jetbrains.skia.FilterTileMode;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMetrics;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.FontStyle;
import org.jetbrains.skia.FramebufferFormat;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.ImageFilter;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.PaintStrokeCap;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceColorFormat;
import org.jetbrains.skia.SurfaceOrigin;
import org.jetbrains.skia.Typeface;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

import java.util.List;

final class SkiaWheelRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TEXT_PRIMARY = 0xFFF7FBFF;

    private boolean unavailable;
    private long retryAfterNanos;
    private DirectContext directContext;

    private FontMgr fontMgr;
    private FontStyle normalStyle;
    private Typeface uiTypeface;
    private Font bodyFont;
    private Font centerFont;

    boolean renderWheelBase(AbstractWheelScreen screen,
                            List<AbstractWheelScreen.WheelEntry> entries,
                            int selectedSlot,
                            float[] slotPop,
                            float openProgress) {
        return withCanvas(screen, (canvas, sceneSnapshot, scaleX, scaleY) -> {
            drawBackdrop(canvas, screen);
            drawWheelGlass(canvas, screen, sceneSnapshot, scaleX, scaleY);
            drawSegments(canvas, screen, entries, selectedSlot, slotPop);
        });
    }

    boolean renderCenterBubble(AbstractWheelScreen screen, String text, int textColor, float centerPop) {
        return withCanvas(screen, (canvas, sceneSnapshot, scaleX, scaleY) -> {
            float maxWidth = Math.max(64.0f, screen.innerRadius * 1.18f);
            drawCenteredText(
                    canvas,
                    centerFont,
                    fitText(text, centerFont, maxWidth),
                    screen.centerX,
                    screen.centerY,
                    textColor,
                    screen.style.textShadow(),
                    0.94f + centerPop * 0.06f
            );
        });
    }

    boolean renderEmptyState(AbstractWheelScreen screen, Component hint) {
        return withCanvas(screen, (canvas, sceneSnapshot, scaleX, scaleY) -> {
            float width = Math.max(150.0f, measureTextWidth(hint.getString(), bodyFont) + 24.0f);
            float height = 24.0f;
            float x = screen.centerX - width / 2.0f;
            float y = screen.centerY + screen.outerRadius * 0.46f;
            RRect shell = RRect.makeLTRB(x, y, x + width, y + height, 9.0f);
            try (Paint fill = fillPaint(0x12000000);
                 Paint border = fillPaint(0x26FFFFFF)) {
                canvas.drawRRect(shell, border);
                canvas.drawRRect(RRect.makeLTRB(x + 1.0f, y + 1.0f, x + width - 1.0f, y + height - 1.0f, 8.0f), fill);
            }
            drawCenteredText(canvas, bodyFont, hint.getString(), screen.centerX, y + height * 0.58f, TEXT_PRIMARY, screen.style.textShadow(), 1.0f);
        });
    }

    void dispose() {
        closeFont(bodyFont);
        closeFont(centerFont);
        bodyFont = null;
        centerFont = null;

        closeTypeface(uiTypeface);
        uiTypeface = null;
        fontMgr = null;
        normalStyle = null;

        if (directContext != null) {
            try {
                directContext.close();
            } catch (Throwable ignored) {
            }
            directContext = null;
        }
        unavailable = false;
    }

    private boolean withCanvas(AbstractWheelScreen screen, CanvasDraw draw) {
        if (unavailable && System.nanoTime() < retryAfterNanos) {
            return false;
        }
        if (unavailable) {
            unavailable = false;
        }

        BackendRenderTarget renderTarget = null;
        Surface surface = null;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            ensureFonts();
            if (directContext == null) {
                directContext = DirectContext.Companion.makeGL();
            }

            int framebufferWidth = minecraft.getWindow().getWidth();
            int framebufferHeight = minecraft.getWindow().getHeight();
            float scaleX = screen.width > 0 ? (float) framebufferWidth / (float) screen.width : 1.0f;
            float scaleY = screen.height > 0 ? (float) framebufferHeight / (float) screen.height : 1.0f;
            int framebufferId = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);

            renderTarget = BackendRenderTarget.Companion.makeGL(
                    framebufferWidth,
                    framebufferHeight,
                    0,
                    8,
                    framebufferId,
                    FramebufferFormat.GR_GL_RGBA8
            );
            surface = Surface.Companion.makeFromBackendRenderTarget(
                    directContext,
                    renderTarget,
                    SurfaceOrigin.BOTTOM_LEFT,
                    SurfaceColorFormat.RGBA_8888,
                    ColorSpace.Companion.getSRGB(),
                    null
            );
            if (surface == null) {
                return disableRenderer("surface == null", null);
            }

            Canvas canvas = surface.getCanvas();
            try (Image sceneSnapshot = surface.makeImageSnapshot()) {
                canvas.save();
                canvas.scale(scaleX, scaleY);
                draw.draw(canvas, sceneSnapshot, scaleX, scaleY);
                canvas.restore();
            }
            surface.flushAndSubmit();
            directContext.flush();
            directContext.resetGLAll();
            restoreMinecraftRenderState(minecraft);
            return true;
        } catch (Throwable throwable) {
            return disableRenderer("Skia wheel rendering failed", throwable);
        } finally {
            if (surface != null) {
                try {
                    surface.close();
                } catch (Throwable ignored) {
                }
            }
            if (renderTarget != null) {
                try {
                    renderTarget.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void drawBackdrop(Canvas canvas, AbstractWheelScreen screen) {
        try (Paint dim = fillPaint(0x06000000)) {
            canvas.drawRect(Rect.makeLTRB(0.0f, 0.0f, screen.width, screen.height), dim);
        }
    }

    private void drawWheelGlass(Canvas canvas, AbstractWheelScreen screen, Image sceneSnapshot, float scaleX, float scaleY) {
        float radius = screen.outerRadius;
        float left = screen.centerX - radius;
        float top = screen.centerY - radius;
        float right = screen.centerX + radius;
        float bottom = screen.centerY + radius;

        Rect src = Rect.makeLTRB(left * scaleX, top * scaleY, right * scaleX, bottom * scaleY);
        Rect dst = Rect.makeLTRB(left, top, right, bottom);
        try (ImageFilter blurFilter = ImageFilter.Companion.makeBlur(4.0f, 4.0f, FilterTileMode.CLAMP, null, null);
             Paint blurPaint = new Paint();
             Paint tint = fillPaint(0x06FFFFFF)) {
            blurPaint.setAntiAlias(true);
            blurPaint.setImageFilter(blurFilter);
            canvas.save();
            canvas.clipRRect(RRect.makeLTRB(left, top, right, bottom, radius));
            canvas.drawImageRect(sceneSnapshot, src, dst, blurPaint);
            canvas.drawRect(dst, tint);
            canvas.restore();
        }
    }

    private void drawSegments(Canvas canvas,
                              AbstractWheelScreen screen,
                              List<AbstractWheelScreen.WheelEntry> entries,
                              int selectedSlot,
                              float[] slotPop) {
        int count = entries.size();
        if (count <= 0) {
            return;
        }

        float segmentAngle = 360.0f / count;
        float gapAngle = 1.2f;
        float visibleSweep = Math.max(8.0f, segmentAngle - gapAngle);
        float ringOuter = screen.outerRadius;
        float ringThickness = ringOuter - screen.innerRadius;

        for (int i = 0; i < count; i++) {
            AbstractWheelScreen.WheelEntry entry = entries.get(i);
            float pop = i < slotPop.length ? slotPop[i] : 0.0f;
            float startAngle = i * segmentAngle - 90.0f + gapAngle * 0.5f;
            float endAngle = startAngle + visibleSweep;
            int fillColor = i == selectedSlot
                    ? screen.blendColors(screen.withAlpha(0x66B8FF, 144), screen.withAlpha(0x8FD2FF, 168), 0.38f + pop * 0.42f)
                    : screen.withAlpha(0xFFFFFF, 58);

            drawSector(canvas, screen.centerX, screen.centerY, screen.innerRadius, ringOuter + pop * 4.0f, startAngle, endAngle, fillColor);

            float textRadius = screen.innerRadius + (ringOuter - screen.innerRadius) * 0.52f + pop * 2.0f;
            float radians = (float) Math.toRadians(startAngle + visibleSweep * 0.5f);
            float tx = screen.centerX + (float) Math.cos(radians) * textRadius;
            float ty = screen.centerY + (float) Math.sin(radians) * textRadius;
            float textWidth = clamp(2.0f * textRadius * (float) Math.sin(Math.toRadians(visibleSweep * 0.5f)) * 0.82f, 48.0f, 140.0f);

            drawCenteredText(
                    canvas,
                    bodyFont,
                    fitText(entry.primaryText(), bodyFont, textWidth),
                    tx,
                    ty,
                    TEXT_PRIMARY,
                    screen.style.textShadow(),
                    1.0f + pop * 0.06f
            );
        }
    }

    private void drawSector(Canvas canvas, float cx, float cy, float innerR, float outerR,
                            float startDeg, float endDeg, int color) {
        float midR = (innerR + outerR) * 0.5f;
        float width = outerR - innerR;
        Rect arcRect = Rect.makeLTRB(cx - midR, cy - midR, cx + midR, cy + midR);
        try (Paint fill = arcPaint(color, width, PaintStrokeCap.BUTT)) {
            canvas.drawArc(arcRect.getLeft(), arcRect.getTop(), arcRect.getRight(), arcRect.getBottom(),
                    startDeg, endDeg - startDeg, false, fill);
        }
    }

    private String fitText(String text, Font font, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (measureTextWidth(text, font) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && measureTextWidth(trimmed + "..", font) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    private float measureTextWidth(String text, Font font) {
        try (Paint paint = new Paint()) {
            return font.measureText(text == null ? "" : text, paint).getWidth();
        }
    }

    private void drawCenteredText(Canvas canvas, Font font, String text, float centerX, float centerY, int color, int shadowColor, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.scale(scale, scale);
        try (Paint shadow = fillPaint(shadowColor);
             Paint fill = fillPaint(color)) {
            Rect bounds = font.measureText(text, fill);
            FontMetrics metrics = font.getMetrics();
            float baseline = -(metrics.getAscent() + metrics.getDescent()) * 0.5f;
            float left = -bounds.getWidth() * 0.5f;
            canvas.drawString(text, left + 0.8f, baseline + 0.8f, font, shadow);
            canvas.drawString(text, left, baseline, font, fill);
        }
        canvas.restore();
    }

    private void ensureFonts() {
        if (bodyFont != null && centerFont != null) {
            return;
        }
        fontMgr = FontMgr.Companion.getDefault();
        normalStyle = FontStyle.Companion.getNORMAL();
        uiTypeface = loadPreferredTypeface();
        if (uiTypeface == null) {
            uiTypeface = Typeface.Companion.makeDefault();
        }
        if (uiTypeface == null) {
            throw new IllegalStateException("No usable system typeface for wheel UI");
        }

        bodyFont = new Font(uiTypeface, 9.4f);
        bodyFont.setSubpixel(true);
        centerFont = new Font(uiTypeface, 15.0f);
        centerFont.setSubpixel(true);
    }

    private Typeface loadPreferredTypeface() {
        String[] families = {
                "Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI",
                "PingFang SC", "Noto Sans CJK SC", "Noto Sans"
        };
        for (String family : families) {
            try {
                Typeface typeface = fontMgr.matchFamilyStyle(family, normalStyle);
                if (typeface != null) {
                    return typeface;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Paint fillPaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setAntiAlias(true);
        return paint;
    }

    private Paint strokePaint(int color, float width) {
        Paint paint = fillPaint(color);
        paint.setMode(PaintMode.STROKE);
        paint.setStrokeWidth(width);
        return paint;
    }

    private Paint arcPaint(int color, float width, PaintStrokeCap cap) {
        Paint paint = strokePaint(color, width);
        paint.setStrokeCap(cap);
        return paint;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean disableRenderer(String message, Throwable throwable) {
        unavailable = true;
        retryAfterNanos = System.nanoTime() + 3_000_000_000L;
        if (throwable == null) {
            LOGGER.error("[SkiaWheelRenderer] {}", message);
        } else {
            LOGGER.error("[SkiaWheelRenderer] {}", message, throwable);
        }
        dispose();
        unavailable = true;
        return false;
    }

    private void restoreMinecraftRenderState(Minecraft minecraft) {
        minecraft.getMainRenderTarget().bindWrite(false);
        RenderSystem.viewport(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        GL11C.glDisable(GL11C.GL_STENCIL_TEST);
        GL11C.glColorMask(true, true, true, true);
        GL11C.glDepthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(1.0f);
    }

    private void closeFont(Font font) {
        if (font != null) {
            try {
                font.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void closeTypeface(Typeface typeface) {
        if (typeface != null) {
            try {
                typeface.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private interface CanvasDraw {
        void draw(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY);
    }
}
