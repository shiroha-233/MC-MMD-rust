package com.shiroha.mmdskin.ui.selector;

import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.ui.SkiaBlurBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.skia.BackendRenderTarget;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ColorSpace;
import org.jetbrains.skia.DirectContext;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMetrics;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.FontStyle;
import org.jetbrains.skia.FramebufferFormat;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceColorFormat;
import org.jetbrains.skia.SurfaceOrigin;
import org.jetbrains.skia.Typeface;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

import java.util.List;

final class SkiaVoicePackBindingRenderer {
    private static final Logger LOGGER = LogManager.getLogger();

    private boolean unavailable;
    private long retryAfterNanos;
    private DirectContext directContext;
    private FontMgr fontMgr;
    private FontStyle normalStyle;
    private Typeface uiTypeface;
    private Font titleFont;
    private Font bodyFont;
    private Font smallFont;

    boolean renderBindings(VoicePackBindingScreen screen, BindingView view) {
        return withCanvas(screen, (canvas, sceneSnapshot, scaleX, scaleY) -> drawBindings(canvas, view, sceneSnapshot, scaleX, scaleY));
    }

    void dispose() {
        closeFont(titleFont);
        closeFont(bodyFont);
        closeFont(smallFont);
        titleFont = null;
        bodyFont = null;
        smallFont = null;
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

    private boolean withCanvas(VoicePackBindingScreen screen, CanvasDraw draw) {
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
            return disableRenderer("Skia voice binding rendering failed", throwable);
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

    private void drawBindings(Canvas canvas, BindingView view, Image sceneSnapshot, float scaleX, float scaleY) {
        drawBackdrop(canvas);
        drawPanel(canvas, view, sceneSnapshot, scaleX, scaleY);
        drawHeader(canvas, view);
        drawRows(canvas, view.rows());
        drawButton(canvas, view.doneButton(), view.doneText(), view.doneHovered(), true);
        drawButton(canvas, view.cancelButton(), view.cancelText(), view.cancelHovered(), false);
    }

    private void drawBackdrop(Canvas canvas) {
        try (Paint dim = fillPaint(0x06000000)) {
            canvas.drawRect(Rect.makeLTRB(0.0f, 0.0f, 10000.0f, 10000.0f), dim);
        }
    }

    private void drawPanel(Canvas canvas, BindingView view, Image sceneSnapshot, float scaleX, float scaleY) {
        float x = view.panel().x();
        float y = view.panel().y();
        float w = view.panel().w();
        float h = view.panel().h();
        SkiaBlurBackground.drawPanel(canvas, sceneSnapshot, scaleX, scaleY, x, y, x + w, y + h, 6.0f);
    }

    private void drawHeader(Canvas canvas, BindingView view) {
        drawText(canvas, titleFont, view.title(), view.header().x(), view.header().y() + 11.0f, 0xFFF2F7FD, 0x880F1722);
        drawText(canvas, smallFont, fitText(view.modelText(), smallFont, view.header().w()),
                view.header().x(), view.header().y() + 22.0f, 0xC9D7E2EE, 0x840E1620);
    }

    private void drawRows(Canvas canvas, List<RowView> rows) {
        for (RowView row : rows) {
            drawRow(canvas, row);
        }
    }

    private void drawRow(Canvas canvas, RowView row) {
        float left = row.rect().x();
        float top = row.rect().y();
        float right = row.rect().x() + row.rect().w();
        float bottom = row.rect().y() + row.rect().h();
        int borderColor = row.assigned()
                ? (row.hovered() ? 0x54FFFFFF : 0x38FFFFFF)
                : (row.hovered() ? 0x3CFFFFFF : 0x20FFFFFF);
        int fillColor = row.assigned()
                ? (row.hovered() ? 0x18FFFFFF : 0x0EFFFFFF)
                : (row.hovered() ? 0x12FFFFFF : 0x04000000);

        RRect outer = RRect.makeLTRB(left, top, right, bottom, 3.0f);
        try (Paint border = strokePaint(borderColor, 1.0f)) {
            canvas.drawRRect(outer, border);
        }
        if (fillColor != 0) {
            try (Paint fill = fillPaint(fillColor)) {
                canvas.drawRRect(RRect.makeLTRB(left + 1.0f, top + 1.0f, right - 1.0f, bottom - 1.0f, 2.0f), fill);
            }
        }

        drawText(canvas, smallFont, fitText(row.label(), smallFont, row.rect().w() - 22.0f),
                left + 6.0f, top + 8.0f, 0xD7E5F2FF, 0x7A0F1722);
        drawText(canvas, bodyFont, fitText(row.value(), bodyFont, row.rect().w() - 22.0f),
                left + 6.0f, top + 21.0f, row.assigned() ? 0xFFF3F8FF : 0xC9D7E2EE, 0x880F1722);
        drawCenteredText(canvas, bodyFont, ">", right - 10.0f, top + row.rect().h() * 0.5f + 0.5f, 0xE4F0FCFF, 0x660F1722);
    }

    private void drawButton(Canvas canvas, VoicePackBindingScreen.UiRect rect, String text, boolean hovered, boolean primary) {
        int border = hovered ? 0x4CFFFFFF : (primary ? 0x34FFFFFF : 0x28FFFFFF);
        int fill = primary ? (hovered ? 0x18FFFFFF : 0x0CFFFFFF) : (hovered ? 0x14FFFFFF : 0x00000000);
        RRect outer = RRect.makeLTRB(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), 2.0f);
        try (Paint borderPaint = strokePaint(border, 1.0f)) {
            canvas.drawRRect(outer, borderPaint);
        }
        if (fill != 0) {
            try (Paint fillPaint = fillPaint(fill)) {
                canvas.drawRRect(RRect.makeLTRB(rect.x() + 1.0f, rect.y() + 1.0f, rect.x() + rect.w() - 1.0f, rect.y() + rect.h() - 1.0f, 1.0f), fillPaint);
            }
        }
        drawCenteredText(canvas, bodyFont, fitText(text, bodyFont, Math.max(32.0f, rect.w() - 8.0f)),
                rect.centerX(), rect.centerY() + 0.5f, 0xFFF3F8FF, 0x880F1722);
    }

    private void drawText(Canvas canvas, Font font, String text, float x, float baselineY, int color, int shadowColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try (Paint shadow = fillPaint(shadowColor); Paint fill = fillPaint(color)) {
            canvas.drawString(text, x + 0.8f, baselineY + 0.8f, font, shadow);
            canvas.drawString(text, x, baselineY, font, fill);
        }
    }

    private void drawCenteredText(Canvas canvas, Font font, String text, float centerX, float centerY, int color, int shadowColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try (Paint shadow = fillPaint(shadowColor); Paint fill = fillPaint(color)) {
            Rect bounds = font.measureText(text, fill);
            FontMetrics metrics = font.getMetrics();
            float baseline = centerY - (metrics.getAscent() + metrics.getDescent()) * 0.5f;
            float left = centerX - bounds.getWidth() * 0.5f;
            canvas.drawString(text, left + 0.8f, baseline + 0.8f, font, shadow);
            canvas.drawString(text, left, baseline, font, fill);
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

    private void ensureFonts() {
        if (titleFont != null && bodyFont != null && smallFont != null) {
            return;
        }
        fontMgr = FontMgr.Companion.getDefault();
        normalStyle = FontStyle.Companion.getNORMAL();
        uiTypeface = loadPreferredTypeface();
        if (uiTypeface == null) {
            uiTypeface = Typeface.Companion.makeDefault();
        }
        if (uiTypeface == null) {
            throw new IllegalStateException("No usable system typeface for voice binding UI");
        }

        titleFont = new Font(uiTypeface, 8.0f);
        titleFont.setSubpixel(true);
        bodyFont = new Font(uiTypeface, 5.8f);
        bodyFont.setSubpixel(true);
        smallFont = new Font(uiTypeface, 4.7f);
        smallFont.setSubpixel(true);
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

    private boolean disableRenderer(String message, Throwable throwable) {
        unavailable = true;
        retryAfterNanos = System.nanoTime() + 3_000_000_000L;
        if (throwable == null) {
            LOGGER.error("[SkiaVoicePackBindingRenderer] {}", message);
        } else {
            LOGGER.error("[SkiaVoicePackBindingRenderer] {}", message, throwable);
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

    record RowView(
            String label,
            String value,
            boolean assigned,
            boolean hovered,
            VoicePackBindingScreen.UiRect rect
    ) {
    }

    record BindingView(
            String title,
            String modelText,
            String doneText,
            String cancelText,
            VoicePackBindingScreen.UiRect panel,
            VoicePackBindingScreen.UiRect header,
            VoicePackBindingScreen.UiRect doneButton,
            VoicePackBindingScreen.UiRect cancelButton,
            boolean doneHovered,
            boolean cancelHovered,
            List<RowView> rows
    ) {
    }

    private interface CanvasDraw {
        void draw(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY);
    }
}
