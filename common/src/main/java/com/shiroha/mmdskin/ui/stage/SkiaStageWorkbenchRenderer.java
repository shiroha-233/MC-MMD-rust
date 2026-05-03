package com.shiroha.mmdskin.ui.stage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.ui.SkiaBlurBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
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
import java.util.Locale;

/** 舞台工作台 Skia 渲染器。 */
final class SkiaStageWorkbenchRenderer {
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

    boolean renderWorkbench(StageWorkbenchScreen screen, WorkbenchView view) {
        return withCanvas(screen, (canvas, sceneSnapshot, scaleX, scaleY) -> drawWorkbench(canvas, view, sceneSnapshot, scaleX, scaleY));
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

    record WorkbenchView(
            String leftTitle,
            String leftSubtitle,
            String packSectionTitle,
            String motionSectionTitle,
            String rightTitle,
            String rightSubtitle,
            String refreshText,
            String sessionButtonText,
            String primaryActionText,
            String secondaryActionText,
            String footerStatus,
            List<String> detailLines,
            StageWorkbenchLayout.UiRect leftPanel,
            StageWorkbenchLayout.UiRect rightPanel,
            StageWorkbenchLayout.UiRect leftHeader,
            StageWorkbenchLayout.UiRect packList,
            StageWorkbenchLayout.UiRect refreshButton,
            StageWorkbenchLayout.UiRect motionList,
            StageWorkbenchLayout.UiRect detailsArea,
            StageWorkbenchLayout.UiRect customMotionToggle,
            StageWorkbenchLayout.UiRect useHostCameraToggle,
            StageWorkbenchLayout.UiRect cinematicToggle,
            StageWorkbenchLayout.UiRect cameraSlider,
            StageWorkbenchLayout.UiRect primaryButton,
            StageWorkbenchLayout.UiRect secondaryButton,
            StageWorkbenchLayout.UiRect rightHeader,
            StageWorkbenchLayout.UiRect sessionButton,
            StageWorkbenchLayout.UiRect sessionList,
            boolean refreshHovered,
            boolean customMotionHovered,
            boolean useHostCameraHovered,
            boolean cinematicHovered,
            boolean cameraSliderHovered,
            boolean primaryHovered,
            boolean secondaryHovered,
            boolean sessionButtonHovered,
            int hoveredPackIndex,
            int hoveredMotionIndex,
            int hoveredSessionIndex,
            boolean guestMode,
            boolean hostMode,
            boolean showCustomMotionToggle,
            boolean showUseHostCameraToggle,
            boolean showSessionButton,
            boolean cinematicEnabled,
            boolean useHostCameraEnabled,
            boolean customMotionEnabled,
            float cameraHeightNormalized,
            float packScrollOffset,
            float motionScrollOffset,
            float sessionScrollOffset,
            List<StageWorkbenchUiStateSnapshot.PackRow> packRows,
            List<StageWorkbenchUiStateSnapshot.MotionRow> motionRows,
            List<StageWorkbenchUiStateSnapshot.SessionRow> sessionRows,
            int listPadding,
            int packRowHeight,
            int motionRowHeight,
            int sessionRowHeight,
            int rowGap
    ) {
    }

    private boolean withCanvas(StageWorkbenchScreen screen, CanvasDraw draw) {
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
            return disableRenderer("Skia stage workbench rendering failed", throwable);
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

    private void drawWorkbench(Canvas canvas, WorkbenchView view, Image sceneSnapshot, float scaleX, float scaleY) {
        drawPanel(canvas, sceneSnapshot, scaleX, scaleY, view.leftPanel());
        drawPanel(canvas, sceneSnapshot, scaleX, scaleY, view.rightPanel());
        drawLeftPanel(canvas, view);
        drawRightPanel(canvas, view);
    }

    private void drawPanel(Canvas canvas, Image sceneSnapshot, float scaleX, float scaleY, StageWorkbenchLayout.UiRect rect) {
        float x = rect.x();
        float y = rect.y();
        float w = rect.w();
        float h = rect.h();
        SkiaBlurBackground.drawPanel(canvas, sceneSnapshot, scaleX, scaleY, x, y, x + w, y + h, 6.0f);
    }

    private void drawLeftPanel(Canvas canvas, WorkbenchView view) {
        drawText(canvas, titleFont, view.leftTitle(), view.leftHeader().x(), view.leftHeader().y() + 10.0f, 0xFFF2F7FD, 0x880F1722);
        drawText(canvas, smallFont, fitText(view.leftSubtitle(), smallFont, Math.max(48.0f, view.leftHeader().w() - 50.0f)),
                view.leftHeader().x(), view.leftHeader().y() + 20.0f, 0xC9D7E2EE, 0x840E1620);

        drawSectionLabel(canvas, view.packSectionTitle(), view.packList().x(), view.packList().y() - 8.0f);
        drawButton(canvas, view.refreshButton(), view.refreshText(), view.refreshHovered(), false, true);
        drawListOutline(canvas, view.packList());
        drawPackRows(canvas, view);

        drawSectionLabel(canvas, view.motionSectionTitle(), view.motionList().x(), view.motionList().y() - 8.0f);
        drawListOutline(canvas, view.motionList());
        drawMotionRows(canvas, view);

        drawListOutline(canvas, view.detailsArea());
        drawDetailLines(canvas, view);

        if (view.showCustomMotionToggle()) {
            drawToggleRow(canvas, view.customMotionToggle(), "本地动作覆盖", view.customMotionEnabled(), view.customMotionHovered());
        }
        if (view.showUseHostCameraToggle()) {
            drawToggleRow(canvas, view.useHostCameraToggle(), "跟随房主镜头", view.useHostCameraEnabled(), view.useHostCameraHovered());
        }
        drawToggleRow(canvas, view.cinematicToggle(), "影院模式", view.cinematicEnabled(), view.cinematicHovered());

        if (!view.guestMode()) {
            drawSlider(canvas, view.cameraSlider(), "镜高", view.cameraHeightNormalized(), view.cameraSliderHovered());
        }

        drawStatus(canvas, view.footerStatus(), view.motionList().x(), view.primaryButton().y() - 4.0f,
                Math.max(64.0f, view.motionList().w() - 4.0f));
        drawButton(canvas, view.primaryButton(), view.primaryActionText(), view.primaryHovered(), true, true);
        drawButton(canvas, view.secondaryButton(), view.secondaryActionText(), view.secondaryHovered(), false, true);
    }

    private void drawRightPanel(Canvas canvas, WorkbenchView view) {
        drawText(canvas, titleFont, view.rightTitle(), view.rightHeader().x(), view.rightHeader().y() + 10.0f, 0xFFF2F7FD, 0x880F1722);
        drawText(canvas, smallFont, fitText(view.rightSubtitle(), smallFont, Math.max(42.0f, view.rightHeader().w() - 4.0f)),
                view.rightHeader().x(), view.rightHeader().y() + 20.0f, 0xC9D7E2EE, 0x840E1620);

        if (view.showSessionButton()) {
            drawButton(canvas, view.sessionButton(), view.sessionButtonText(), view.sessionButtonHovered(), false, true);
        }

        drawListOutline(canvas, view.sessionList());
        drawSessionRows(canvas, view);
    }

    private void drawSectionLabel(Canvas canvas, String text, float x, float baselineY) {
        drawText(canvas, smallFont, text, x, baselineY, 0xE6ECF5FF, 0x7A0F1722);
    }

    private void drawStatus(Canvas canvas, String text, float x, float baselineY, float maxWidth) {
        drawText(canvas, smallFont, fitText(text, smallFont, maxWidth), x, baselineY, 0xBDD2E1EF, 0x7A0F1722);
    }

    private void drawListOutline(Canvas canvas, StageWorkbenchLayout.UiRect rect) {
        RRect outer = RRect.makeLTRB(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), 3.0f);
        try (Paint border = strokePaint(0x18FFFFFF, 1.0f)) {
            canvas.drawRRect(outer, border);
        }
    }

    private void drawPackRows(Canvas canvas, WorkbenchView view) {
        float left = view.packList().x() + 3.0f;
        float right = view.packList().x() + view.packList().w() - 3.0f;
        float y = view.packList().y() + view.listPadding() - view.packScrollOffset();
        float visibleTop = view.packList().y() + 2.0f;
        float visibleBottom = view.packList().y() + view.packList().h() - 2.0f;

        canvas.save();
        canvas.clipRect(Rect.makeLTRB(view.packList().x(), view.packList().y(), view.packList().x() + view.packList().w(), view.packList().y() + view.packList().h()));
        for (StageWorkbenchUiStateSnapshot.PackRow row : view.packRows()) {
            float bottom = y + view.packRowHeight();
            if (bottom >= visibleTop && y <= visibleBottom) {
                drawSimpleRow(canvas, left, y, right, bottom, row.label(), "", row.selected(), row.index() == view.hoveredPackIndex(), false, "");
            }
            y += view.packRowHeight() + view.rowGap();
        }
        canvas.restore();
    }

    private void drawMotionRows(Canvas canvas, WorkbenchView view) {
        float left = view.motionList().x() + 3.0f;
        float right = view.motionList().x() + view.motionList().w() - 3.0f;
        float y = view.motionList().y() + view.listPadding() - view.motionScrollOffset();
        float visibleTop = view.motionList().y() + 2.0f;
        float visibleBottom = view.motionList().y() + view.motionList().h() - 2.0f;

        canvas.save();
        canvas.clipRect(Rect.makeLTRB(view.motionList().x(), view.motionList().y(), view.motionList().x() + view.motionList().w(), view.motionList().y() + view.motionList().h()));
        for (StageWorkbenchUiStateSnapshot.MotionRow row : view.motionRows()) {
            float bottom = y + view.motionRowHeight();
            if (bottom >= visibleTop && y <= visibleBottom) {
                drawSimpleRow(canvas, left, y, right, bottom, row.label(), row.subtitle(), row.selected(), row.index() == view.hoveredMotionIndex(), false, "");
            }
            y += view.motionRowHeight() + view.rowGap();
        }
        canvas.restore();
    }

    private void drawSessionRows(Canvas canvas, WorkbenchView view) {
        float left = view.sessionList().x() + 3.0f;
        float right = view.sessionList().x() + view.sessionList().w() - 3.0f;
        float y = view.sessionList().y() + view.listPadding() - view.sessionScrollOffset();
        float visibleTop = view.sessionList().y() + 2.0f;
        float visibleBottom = view.sessionList().y() + view.sessionList().h() - 2.0f;

        canvas.save();
        canvas.clipRect(Rect.makeLTRB(view.sessionList().x(), view.sessionList().y(), view.sessionList().x() + view.sessionList().w(), view.sessionList().y() + view.sessionList().h()));
        for (StageWorkbenchUiStateSnapshot.SessionRow row : view.sessionRows()) {
            float bottom = y + view.sessionRowHeight();
            if (bottom >= visibleTop && y <= visibleBottom) {
                drawSimpleRow(canvas, left, y, right, bottom, row.label(), row.subtitle(), row.selected(), row.index() == view.hoveredSessionIndex(), row.actionable(), row.actionText());
            }
            y += view.sessionRowHeight() + view.rowGap();
        }
        canvas.restore();
    }

    private void drawSimpleRow(Canvas canvas, float left, float top, float right, float bottom,
                               String title, String subtitle, boolean selected, boolean hovered,
                               boolean actionable, String actionText) {
        RRect outer = RRect.makeLTRB(left, top, right, bottom, 3.0f);
        int fillColor = selected ? (hovered ? 0x1AFFFFFF : 0x12FFFFFF) : (hovered ? 0x10FFFFFF : 0x00000000);
        int borderColor = selected ? (hovered ? 0x44FFFFFF : 0x30FFFFFF) : (hovered ? 0x28FFFFFF : 0x10FFFFFF);
        boolean showActionPill = actionable && actionText != null && !actionText.isBlank();
        float pillWidth = showActionPill ? Math.min(56.0f, Math.max(34.0f, measureTextWidth(actionText, smallFont) + 10.0f)) : 0.0f;
        float pillLeft = right - pillWidth - 4.0f;
        float textMaxWidth = Math.max(12.0f, (showActionPill ? pillLeft - 6.0f : right - 4.0f) - (left + 4.0f));

        try (Paint border = strokePaint(borderColor, 1.0f)) {
            canvas.drawRRect(outer, border);
        }
        if (fillColor != 0) {
            try (Paint fill = fillPaint(fillColor)) {
                canvas.drawRRect(RRect.makeLTRB(left + 1.0f, top + 1.0f, right - 1.0f, bottom - 1.0f, 2.0f), fill);
            }
        }

        boolean hasSubtitle = subtitle != null && !subtitle.isBlank();
        float titleY = hasSubtitle ? top + 6.5f : top + (bottom - top) * 0.64f;
        drawText(canvas, bodyFont, fitText(title, bodyFont, textMaxWidth), left + 4.0f, titleY, 0xFFEEF5FF, 0x7A0F1722);
        if (hasSubtitle) {
            drawText(canvas, smallFont, fitText(subtitle, smallFont, textMaxWidth), left + 4.0f, top + 14.5f, 0xBFD3E3F2, 0x7A0F1722);
        }

        if (showActionPill) {
            float pillTop = top + 3.0f;
            RRect pill = RRect.makeLTRB(pillLeft, pillTop, pillLeft + pillWidth, pillTop + 10.0f, 2.0f);
            try (Paint border = strokePaint(0x30FFFFFF, 1.0f); Paint fill = fillPaint(0x0CFFFFFF)) {
                canvas.drawRRect(pill, border);
                canvas.drawRRect(RRect.makeLTRB(pillLeft + 1.0f, pillTop + 1.0f, pillLeft + pillWidth - 1.0f, pillTop + 9.0f, 1.0f), fill);
            }
            drawCenteredText(canvas, smallFont, actionText, pillLeft + pillWidth * 0.5f, pillTop + 5.5f, 0xFFE6EEF8, 0x660F1722);
        }
    }

    private void drawDetailLines(Canvas canvas, WorkbenchView view) {
        float y = view.detailsArea().y() + 7.0f;
        float maxWidth = Math.max(48.0f, view.detailsArea().w() - 8.0f);
        for (String line : view.detailLines()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            drawText(canvas, smallFont, fitText(line, smallFont, maxWidth), view.detailsArea().x() + 4.0f, y, 0xC3D6E4F1, 0x7A0F1722);
            y += 9.0f;
            if (y > view.detailsArea().y() + view.detailsArea().h() - 2.0f) {
                break;
            }
        }
    }

    private void drawToggleRow(Canvas canvas, StageWorkbenchLayout.UiRect rect, String label, boolean enabled, boolean hovered) {
        float toggleWidth = 26.0f;
        float toggleHeight = 10.0f;
        float toggleX = rect.x() + rect.w() - toggleWidth;
        float toggleY = rect.y() + 2.0f;
        drawText(canvas, bodyFont, fitText(label, bodyFont, Math.max(24.0f, toggleX - rect.x() - 8.0f)),
                rect.x(), rect.y() + 9.0f, 0xFFE9F1FA, 0x7A0F1722);
        int borderColor = hovered ? 0x4CFFFFFF : 0x28FFFFFF;
        int fillColor = enabled ? 0x12FFFFFF : 0x00000000;

        RRect outer = RRect.makeLTRB(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, 2.0f);
        try (Paint border = strokePaint(borderColor, 1.0f); Paint knob = fillPaint(0xE8F2FBFF)) {
            canvas.drawRRect(outer, border);
            if (fillColor != 0) {
                try (Paint fill = fillPaint(fillColor)) {
                    canvas.drawRRect(RRect.makeLTRB(toggleX + 1.0f, toggleY + 1.0f, toggleX + toggleWidth - 1.0f, toggleY + toggleHeight - 1.0f, 1.0f), fill);
                }
            }
            float knobX = enabled ? toggleX + toggleWidth - 7.0f : toggleX + 5.0f;
            canvas.drawCircle(knobX, toggleY + toggleHeight * 0.5f, 3.0f, knob);
        }
    }

    private void drawSlider(Canvas canvas, StageWorkbenchLayout.UiRect rect, String label, float normalized, boolean hovered) {
        String labelText = Component.translatable("gui.mmdskin.stage.workbench.camera_height").getString();
        if (labelText == null || labelText.isBlank()) {
            labelText = label;
        }
        String valueText = String.format(Locale.ROOT, "%+.2f", -2.0f + 4.0f * normalized);
        float valueWidth = measureTextWidth(valueText, smallFont);
        float labelMaxWidth = Math.max(24.0f, rect.w() - valueWidth - 8.0f);
        drawText(canvas, smallFont, fitText(labelText, smallFont, labelMaxWidth),
                rect.x(), rect.y() - 2.0f, 0xC8D5DFEC, 0x7A0F1722);
        drawText(canvas, smallFont, valueText,
                rect.x() + rect.w() - valueWidth, rect.y() - 2.0f, 0xDCEAF7FF, 0x7A0F1722);
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

    private void drawButton(Canvas canvas, StageWorkbenchLayout.UiRect rect, String text, boolean hovered, boolean primary, boolean enabled) {
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
                rect.centerX(), rect.centerY() + 0.5f, enabled ? 0xFFF3F8FF : 0xA7C0D1E3, 0x880F1722);
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
            throw new IllegalStateException("No usable system typeface for stage workbench UI");
        }

        titleFont = new Font(uiTypeface, 7.2f);
        titleFont.setSubpixel(true);
        bodyFont = new Font(uiTypeface, 5.2f);
        bodyFont.setSubpixel(true);
        smallFont = new Font(uiTypeface, 4.2f);
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
            LOGGER.error("[SkiaStageWorkbenchRenderer] {}", message);
        } else {
            LOGGER.error("[SkiaStageWorkbenchRenderer] {}", message, throwable);
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
