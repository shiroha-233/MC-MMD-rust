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
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceColorFormat;
import org.jetbrains.skia.SurfaceOrigin;
import org.jetbrains.skia.Typeface;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

import java.util.List;

final class SkiaModelSettingsRenderer {
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

    boolean renderSettings(ModelSettingsScreen screen, SettingsView view) {
        return withCanvas(screen, (canvas, sceneSnapshot, scaleX, scaleY) -> drawSettings(canvas, view, sceneSnapshot, scaleX, scaleY));
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

    private boolean withCanvas(ModelSettingsScreen screen, CanvasDraw draw) {
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

            renderTarget = BackendRenderTarget.Companion.makeGL(framebufferWidth, framebufferHeight, 0, 8, framebufferId, FramebufferFormat.GR_GL_RGBA8);
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
            return disableRenderer("Skia model settings rendering failed", throwable);
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

    private void drawSettings(Canvas canvas, SettingsView view, Image sceneSnapshot, float scaleX, float scaleY) {
        drawBackdrop(canvas);
        drawPanel(canvas, view, sceneSnapshot, scaleX, scaleY);
        drawHeader(canvas, view);
        drawCard(canvas, view.eyeCard(), view.eyeSectionTitle());
        drawCard(canvas, view.scaleCard(), view.scaleSectionTitle());
        drawCard(canvas, view.quickCard(), view.quickSectionTitle());
        drawToggle(canvas, view);
        drawSlider(canvas, view.eyeSlider(), view.eyeAngleLabel(), view.eyeAngleNormalized(), view.eyeSliderHovered());
        drawSlider(canvas, view.scaleSlider(), view.modelScaleLabel(), view.modelScaleNormalized(), view.scaleSliderHovered());
        drawQuickSlots(canvas, view);
        drawButton(canvas, view.saveButton(), view.saveText(), view.saveHovered(), true);
        drawButton(canvas, view.resetButton(), view.resetText(), view.resetHovered(), false);
        drawButton(canvas, view.animButton(), view.animText(), view.animHovered(), false);
        drawButton(canvas, view.voiceButton(), view.voiceText(), view.voiceHovered(), false);
        drawButton(canvas, view.doneButton(), view.doneText(), view.doneHovered(), false);
    }

    private void drawBackdrop(Canvas canvas) {
        try (Paint dim = fillPaint(0x06000000)) {
            canvas.drawRect(Rect.makeLTRB(0.0f, 0.0f, 10000.0f, 10000.0f), dim);
        }
    }

    private void drawPanel(Canvas canvas, SettingsView view, Image sceneSnapshot, float scaleX, float scaleY) {
        float x = view.panel().x();
        float y = view.panel().y();
        float w = view.panel().w();
        float h = view.panel().h();
        SkiaBlurBackground.drawPanel(canvas, sceneSnapshot, scaleX, scaleY, x, y, x + w, y + h, 6.0f);
    }

    private void drawHeader(Canvas canvas, SettingsView view) {
        drawText(canvas, titleFont, view.title(), view.header().x(), view.header().y() + 11.0f, 0xFFF2F7FD, 0x880F1722);
        drawText(canvas, smallFont, view.modelName(), view.header().x(), view.header().y() + 22.0f, 0xC9D7E2EE, 0x840E1620);
    }

    private void drawCard(Canvas canvas, ModelSettingsScreen.UiRect rect, String title) {
        RRect outer = RRect.makeLTRB(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), 3.0f);
        try (Paint border = strokePaint(0x20FFFFFF, 1.0f)) {
            canvas.drawRRect(outer, border);
        }
        drawText(canvas, smallFont, title, rect.x() + 4.0f, rect.y() + 8.0f, 0xFFE9F1FA, 0x7A0F1722);
    }

    private void drawToggle(Canvas canvas, SettingsView view) {
        drawText(canvas, bodyFont, view.eyeToggleText(), view.eyeCard().x() + 4.0f, view.eyeCard().y() + 18.0f, 0xFFE9F1FA, 0x7A0F1722);
        int borderColor = view.eyeToggleHovered() ? 0x4CFFFFFF : 0x28FFFFFF;
        int fillColor = view.eyeTrackingEnabled() ? 0x12FFFFFF : 0x00000000;
        RRect outer = RRect.makeLTRB(view.eyeToggle().x(), view.eyeToggle().y(), view.eyeToggle().x() + view.eyeToggle().w(), view.eyeToggle().y() + view.eyeToggle().h(), 2.0f);
        try (Paint border = strokePaint(borderColor, 1.0f); Paint knob = fillPaint(0xE8F2FBFF)) {
            canvas.drawRRect(outer, border);
            if (fillColor != 0) {
                try (Paint fill = fillPaint(fillColor)) {
                    canvas.drawRRect(RRect.makeLTRB(
                            view.eyeToggle().x() + 1.0f,
                            view.eyeToggle().y() + 1.0f,
                            view.eyeToggle().x() + view.eyeToggle().w() - 1.0f,
                            view.eyeToggle().y() + view.eyeToggle().h() - 1.0f,
                            1.0f
                    ), fill);
                }
            }
            float knobX = view.eyeTrackingEnabled() ? view.eyeToggle().x() + view.eyeToggle().w() - 8.0f : view.eyeToggle().x() + 4.0f;
            canvas.drawCircle(knobX, view.eyeToggle().y() + view.eyeToggle().h() * 0.5f, 3.0f, knob);
        }
    }

    private void drawSlider(Canvas canvas, ModelSettingsScreen.UiRect rect, String label, float normalized, boolean hovered) {
        drawText(canvas, smallFont, label, rect.x(), rect.y() - 2.0f, 0xC8D5DFEC, 0x7A0F1722);
        RRect outer = RRect.makeLTRB(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), 2.0f);
        float fillRight = rect.x() + 1.0f + (rect.w() - 2.0f) * normalized;
        RRect fillRect = RRect.makeLTRB(rect.x() + 1.0f, rect.y() + 1.0f, Math.max(rect.x() + 1.0f, fillRight), rect.y() + rect.h() - 1.0f, 1.0f);
        try (Paint border = strokePaint(hovered ? 0x44FFFFFF : 0x20FFFFFF, 1.0f);
             Paint progress = fillPaint(0x58FFFFFF);
             Paint knob = fillPaint(0xF4F8FDFF)) {
            canvas.drawRRect(outer, border);
            if (normalized > 0.0f) {
                canvas.drawRRect(fillRect, progress);
            }
            canvas.drawCircle(fillRight, rect.y() + rect.h() * 0.5f, 2.6f, knob);
        }
    }

    private void drawQuickSlots(Canvas canvas, SettingsView view) {
        for (QuickSlotView slot : view.quickSlots()) {
            int borderColor = slot.boundToCurrentModel()
                    ? (slot.hovered() ? 0x54FFFFFF : 0x40FFFFFF)
                    : (slot.hovered() ? 0x34FFFFFF : 0x18FFFFFF);
            int fillColor = slot.boundToCurrentModel()
                    ? (slot.hovered() ? 0x20FFFFFF : 0x14FFFFFF)
                    : (slot.hovered() ? 0x12FFFFFF : 0x00000000);
            RRect outer = RRect.makeLTRB(slot.rect().x(), slot.rect().y(), slot.rect().x() + slot.rect().w(), slot.rect().y() + slot.rect().h(), 2.0f);
            try (Paint border = strokePaint(borderColor, 1.0f)) {
                canvas.drawRRect(outer, border);
            }
            if (fillColor != 0) {
                try (Paint fill = fillPaint(fillColor)) {
                    canvas.drawRRect(RRect.makeLTRB(
                            slot.rect().x() + 1.0f,
                            slot.rect().y() + 1.0f,
                            slot.rect().x() + slot.rect().w() - 1.0f,
                            slot.rect().y() + slot.rect().h() - 1.0f,
                            1.0f
                    ), fill);
                }
            }
            drawCenteredText(canvas, smallFont, slot.slotLabel(), slot.rect().centerX(), slot.rect().y() + 6.0f, 0xFFF1F6FD, 0x880F1722);
            String bound = slot.boundModel() == null || slot.boundModel().isBlank() ? "-" : slot.boundModel();
            drawCenteredText(canvas, smallFont, bound, slot.rect().centerX(), slot.rect().y() + 12.0f, 0xC8D5DFEC, 0x7A0F1722);
        }
    }

    private void drawButton(Canvas canvas, ModelSettingsScreen.UiRect rect, String text, boolean hovered, boolean primary) {
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
        drawCenteredText(canvas, bodyFont, text, rect.centerX(), rect.centerY() + 0.5f, 0xFFF3F8FF, 0x880F1722);
    }

    private void drawText(Canvas canvas, Font font, String text, float x, float baselineY, int color, int shadowColor) {
        if (text == null || text.isEmpty()) return;
        try (Paint shadow = fillPaint(shadowColor); Paint fill = fillPaint(color)) {
            canvas.drawString(text, x + 0.8f, baselineY + 0.8f, font, shadow);
            canvas.drawString(text, x, baselineY, font, fill);
        }
    }

    private void drawCenteredText(Canvas canvas, Font font, String text, float centerX, float centerY, int color, int shadowColor) {
        if (text == null || text.isEmpty()) return;
        try (Paint shadow = fillPaint(shadowColor); Paint fill = fillPaint(color)) {
            Rect bounds = font.measureText(text, fill);
            FontMetrics metrics = font.getMetrics();
            float baseline = centerY - (metrics.getAscent() + metrics.getDescent()) * 0.5f;
            float left = centerX - bounds.getWidth() * 0.5f;
            canvas.drawString(text, left + 0.8f, baseline + 0.8f, font, shadow);
            canvas.drawString(text, left, baseline, font, fill);
        }
    }

    private void ensureFonts() {
        if (titleFont != null && bodyFont != null && smallFont != null) return;
        fontMgr = FontMgr.Companion.getDefault();
        normalStyle = FontStyle.Companion.getNORMAL();
        uiTypeface = loadPreferredTypeface();
        if (uiTypeface == null) uiTypeface = Typeface.Companion.makeDefault();
        if (uiTypeface == null) throw new IllegalStateException("No usable system typeface for model settings UI");
        titleFont = new Font(uiTypeface, 8.0f);
        titleFont.setSubpixel(true);
        bodyFont = new Font(uiTypeface, 5.8f);
        bodyFont.setSubpixel(true);
        smallFont = new Font(uiTypeface, 4.7f);
        smallFont.setSubpixel(true);
    }

    private Typeface loadPreferredTypeface() {
        String[] families = {"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "PingFang SC", "Noto Sans CJK SC", "Noto Sans"};
        for (String family : families) {
            try {
                Typeface typeface = fontMgr.matchFamilyStyle(family, normalStyle);
                if (typeface != null) return typeface;
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
        if (throwable == null) LOGGER.error("[SkiaModelSettingsRenderer] {}", message); else LOGGER.error("[SkiaModelSettingsRenderer] {}", message, throwable);
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
        if (font != null) try { font.close(); } catch (Throwable ignored) {}
    }

    private void closeTypeface(Typeface typeface) {
        if (typeface != null) try { typeface.close(); } catch (Throwable ignored) {}
    }

    record QuickSlotView(String slotLabel, String boundModel, boolean boundToCurrentModel, boolean hovered, int index, ModelSettingsScreen.UiRect rect) {}

    record SettingsView(
            String title,
            String modelName,
            String eyeSectionTitle,
            String eyeToggleText,
            String eyeAngleLabel,
            String scaleSectionTitle,
            String modelScaleLabel,
            String quickSectionTitle,
            String saveText,
            String resetText,
            String animText,
            String voiceText,
            String doneText,
            ModelSettingsScreen.UiRect panel,
            ModelSettingsScreen.UiRect header,
            ModelSettingsScreen.UiRect eyeCard,
            ModelSettingsScreen.UiRect eyeToggle,
            ModelSettingsScreen.UiRect eyeSlider,
            ModelSettingsScreen.UiRect scaleCard,
            ModelSettingsScreen.UiRect scaleSlider,
            ModelSettingsScreen.UiRect quickCard,
            ModelSettingsScreen.UiRect saveButton,
            ModelSettingsScreen.UiRect resetButton,
            ModelSettingsScreen.UiRect animButton,
            ModelSettingsScreen.UiRect voiceButton,
            ModelSettingsScreen.UiRect doneButton,
            boolean eyeTrackingEnabled,
            float eyeAngleNormalized,
            float modelScaleNormalized,
            boolean eyeToggleHovered,
            boolean eyeSliderHovered,
            boolean scaleSliderHovered,
            boolean saveHovered,
            boolean resetHovered,
            boolean animHovered,
            boolean voiceHovered,
            boolean doneHovered,
            List<QuickSlotView> quickSlots
    ) {}

    private interface CanvasDraw {
        void draw(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY);
    }
}
