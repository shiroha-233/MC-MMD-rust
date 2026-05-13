package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/** 轮盘界面基类，负责原生 GuiGraphics 轮盘渲染与交互动画。 */
public abstract class AbstractWheelScreen extends Screen {

    public record WheelStyle(
            float screenRatio, float innerRatio,
            int lineColor, int lineColorDim, int highlightColor,
            int centerBg, int centerBorder, int textShadow
    ) {}

    public record WheelEntry(String primaryText, String secondaryText) {}

    private static final int TEXT_PRIMARY = TranslucentTrayChrome.TITLE_TEXT;
    private static final int TEXT_SECONDARY = TranslucentTrayChrome.BODY_TEXT;
    private static final int TEXT_MUTED = TranslucentTrayChrome.SUBTITLE_TEXT;
    private static final int OUTLINE_DIM = 0x26FFFFFF;
    private static final int BACKDROP_DIM = TranslucentTrayChrome.OVERLAY;
    private static final float SEGMENT_GAP_DEGREES = 1.8f;
    private static final float CENTER_BUBBLE_SCALE = 0.74f;

    protected final WheelStyle style;
    protected int centerX;
    protected int centerY;
    protected int outerRadius;
    protected int innerRadius;
    protected int selectedSlot = -1;

    private float[] slotPop = new float[0];
    private float[] slotVelocity = new float[0];
    private float centerPop;
    private float centerVelocity;
    private float openProgress;
    private float openVelocity;

    protected AbstractWheelScreen(Component title, WheelStyle style) {
        super(title);
        this.style = style;
    }

    protected static WheelStyle createTranslucentWheelStyle(float screenRatio, float innerRatio) {
        return new WheelStyle(
                screenRatio,
                innerRatio,
                0xFFF2F5F8,
                0xB8D5DCE3,
                0x30FFFFFF,
                0xFF20262D,
                0xFFDDE3E9,
                0xD0000000
        );
    }

    protected abstract int getSlotCount();

    protected void initWheelLayout() {
        this.centerX = this.width / 2;
        this.centerY = this.height / 2 + Math.round(this.height * 0.02f);
        int minDim = Math.min(this.width, this.height);
        this.outerRadius = (int) (minDim * style.screenRatio() / 2);
        this.innerRadius = (int) (this.outerRadius * style.innerRatio());
    }

    protected void updateSelectedSlot(int mouseX, int mouseY) {
        int count = getSlotCount();
        if (count <= 0) {
            selectedSlot = -1;
            return;
        }

        int dx = mouseX - centerX;
        int dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        float minRadius = innerRadius * 0.72f;
        float maxRadius = outerRadius + 50.0f;
        if (distance < minRadius || distance > maxRadius) {
            selectedSlot = -1;
            return;
        }

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) {
            angle += 360;
        }
        angle = (angle + 90.0) % 360.0;

        double segmentAngle = 360.0 / count;
        selectedSlot = (int) (angle / segmentAngle) % count;
    }

    protected void renderWheelBase(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, List<WheelEntry> entries) {
        updateSelectedSlot(mouseX, mouseY);
        updateAnimations(entries.size());
        renderBackdrop(guiGraphics);
        if (!entries.isEmpty()) {
            renderSegmentWheel(guiGraphics, entries);
        }
        renderCenterDecor(guiGraphics);
    }

    protected void renderCenterBubble(GuiGraphics guiGraphics, String text, int textColor) {
        float bubbleScale = 0.98f + centerPop * 0.06f;
        int bubbleRadius = Math.max(30, Math.round(innerRadius * CENTER_BUBBLE_SCALE * bubbleScale));
        fillCircle(guiGraphics, centerX, centerY, bubbleRadius + 2, withAlpha(style.centerBorder(), 72));
        drawCircleOutline(guiGraphics, centerX, centerY, bubbleRadius + 1, withAlpha(0xFFFFFF, 16));

        String display = fitText(text, Math.max(88, Math.round(innerRadius * 1.4f)));
        int textWidth = this.font.width(display);
        guiGraphics.drawString(this.font, display, centerX - textWidth / 2 + 1, centerY - 4, style.textShadow(), false);
        guiGraphics.drawString(this.font, display, centerX - textWidth / 2, centerY - 5, textColor, false);
    }

    protected void renderEmptyState(GuiGraphics guiGraphics, Component hint) {
        int width = Math.max(180, this.font.width(hint) + 48);
        int height = 36;
        int x = centerX - width / 2;
        int y = centerY + outerRadius / 2 + 18;
        fillRoundedRect(guiGraphics, x, y, width, height, 12, withAlpha(style.centerBorder(), 228));
        fillRoundedRect(guiGraphics, x + 1, y + 1, width - 2, height - 2, 11, withAlpha(style.centerBg(), 196));
        guiGraphics.drawCenteredString(this.font, hint, centerX, y + 13, TEXT_PRIMARY);
    }

    protected Button createWheelIconButton(Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress)
                .bounds(this.width - 36, this.height - 34, 28, 24)
                .build();
    }

    private void renderBackdrop(GuiGraphics guiGraphics) {
        if (this.minecraft == null || this.minecraft.level == null) {
            this.renderBackground(guiGraphics);
        }
        guiGraphics.fill(0, 0, this.width, this.height, BACKDROP_DIM);
    }

    private void renderSegmentWheel(GuiGraphics guiGraphics, List<WheelEntry> entries) {
        int count = entries.size();
        float segmentAngle = 360.0f / count;
        int ringInner = innerRadius;
        int ringOuter = outerRadius;

        drawCircleArcOutline(guiGraphics, centerX, centerY, ringOuter + 3, OUTLINE_DIM, 2.1f);
        drawCircleArcOutline(guiGraphics, centerX, centerY, ringInner - 2, withAlpha(style.lineColorDim(), 190), 1.8f);

        for (int i = 0; i < count; i++) {
            WheelEntry entry = entries.get(i);
            float pop = slotPop[i];
            float start = i * segmentAngle - 90.0f + SEGMENT_GAP_DEGREES * 0.5f;
            float sweep = Math.max(8.0f, segmentAngle - SEGMENT_GAP_DEGREES);
            float expandedOuter = ringOuter + pop * 5.0f + openProgress * 2.5f;
            float expandedInner = Math.max(12.0f, ringInner - pop * 2.0f);

            int fillColor = i == selectedSlot
                    ? blendColors(withAlpha(style.centerBg(), 106), withAlpha(0xFFFFFF, 24), 0.12f + pop * 0.16f)
                    : blendColors(withAlpha(style.centerBg(), 92), withAlpha(style.lineColorDim(), 28), 0.06f + openProgress * 0.08f);
            int edgeColor = i == selectedSlot
                    ? blendColors(withAlpha(style.centerBorder(), 138), withAlpha(0xFFFFFF, 92), pop * 0.20f)
                    : withAlpha(style.lineColorDim(), 88);

            drawAnnularSegment(guiGraphics, centerX, centerY, expandedInner, expandedOuter, start, sweep, fillColor);
            drawAnnularSegmentOutline(guiGraphics, centerX, centerY, expandedInner, expandedOuter, start, sweep, edgeColor, 1.8f);
            drawSeparator(guiGraphics, centerX, centerY, expandedInner + 4.0f, expandedOuter - 4.0f, start, withAlpha(0xFFFFFF, 34));

            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2.0 - 90.0);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float textRadius = expandedInner + (expandedOuter - expandedInner) * 0.54f + pop * 2.0f;
            int cx = Math.round(centerX + cos * textRadius);
            int cy = Math.round(centerY + sin * textRadius);
            float halfSweepRad = (float) Math.toRadians(sweep * 0.5f);
            int maxTextWidth = Math.round(Mth.clamp(2.0f * textRadius * (float) Math.sin(halfSweepRad) * 0.90f, 86.0f, 210.0f));

            if (entry.secondaryText() == null || entry.secondaryText().isEmpty()) {
                renderSingleEntry(guiGraphics, entry.primaryText(), cx, cy, pop, maxTextWidth);
            } else {
                renderDualEntry(guiGraphics, entry.primaryText(), entry.secondaryText(), cx, cy, pop, maxTextWidth);
            }
        }
    }

    private void renderCenterDecor(GuiGraphics guiGraphics) {
        int haloRadius = Math.max(18, Math.round(innerRadius * 0.86f + openProgress * 4.0f));
        drawCircleOutline(guiGraphics, centerX, centerY, haloRadius, withAlpha(style.lineColorDim(), 42));
        drawCircleOutline(guiGraphics, centerX, centerY, Math.max(12, haloRadius - 6), withAlpha(0xFFFFFF, 10));
    }

    private void renderSingleEntry(GuiGraphics guiGraphics, String text, int centerTextX, int centerTextY, float pop, int maxWidth) {
        String display = fitText(text, Math.max(62, maxWidth));
        int width = this.font.width(display);
        int color = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.36f + pop * 0.64f);
        guiGraphics.drawString(this.font, display, centerTextX - width / 2 + 1, centerTextY - 4, style.textShadow(), false);
        guiGraphics.drawString(this.font, display, centerTextX - width / 2, centerTextY - 5, color, false);
    }

    private void renderDualEntry(GuiGraphics guiGraphics, String icon, String label, int centerTextX, int centerTextY, float pop, int maxWidth) {
        String displayIcon = icon == null ? "" : icon;
        int iconWidth = this.font.width(displayIcon);
        int iconColor = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.42f + pop * 0.58f);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerTextX, centerTextY - 10.0f, 0.0f);
        guiGraphics.pose().scale(1.0f + pop * 0.08f, 1.0f + pop * 0.08f, 1.0f);
        guiGraphics.drawString(this.font, displayIcon, -iconWidth / 2 + 1, 0, style.textShadow(), false);
        guiGraphics.drawString(this.font, displayIcon, -iconWidth / 2, -1, iconColor, false);
        guiGraphics.pose().popPose();

        String displayLabel = fitText(label, Math.max(56, Math.round(maxWidth * 0.76f)));
        int labelWidth = this.font.width(displayLabel);
        int labelColor = blendColors(TEXT_MUTED, TEXT_SECONDARY, 0.52f + pop * 0.38f);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerTextX, centerTextY + 4.0f, 0.0f);
        guiGraphics.pose().scale(0.90f + pop * 0.02f, 0.90f + pop * 0.02f, 1.0f);
        guiGraphics.drawString(this.font, displayLabel, -labelWidth / 2 + 1, 0, style.textShadow(), false);
        guiGraphics.drawString(this.font, displayLabel, -labelWidth / 2, -1, labelColor, false);
        guiGraphics.pose().popPose();
    }

    private void updateAnimations(int count) {
        ensureAnimationCapacity(count);
        float openTarget = 1.03f;
        openVelocity += (openTarget - openProgress) * 0.24f;
        openVelocity *= 0.74f;
        openProgress = Mth.clamp(openProgress + openVelocity, 0.0f, 1.22f);

        for (int i = 0; i < count; i++) {
            float target = i == selectedSlot ? 1.08f : -0.05f;
            slotVelocity[i] += (target - slotPop[i]) * 0.40f;
            slotVelocity[i] *= 0.80f;
            slotPop[i] = Mth.clamp(slotPop[i] + slotVelocity[i], -0.22f, 1.42f);
        }

        float centerTarget = selectedSlot >= 0 ? 1.06f : -0.04f;
        centerVelocity += (centerTarget - centerPop) * 0.34f;
        centerVelocity *= 0.78f;
        centerPop = Mth.clamp(centerPop + centerVelocity, -0.20f, 1.30f);
    }

    private void ensureAnimationCapacity(int count) {
        if (slotPop.length == count) {
            return;
        }
        slotPop = new float[count];
        slotVelocity = new float[count];
        centerPop = 0.0f;
        centerVelocity = 0.0f;
        openProgress = 0.0f;
        openVelocity = 0.0f;
    }

    protected String fitText(String text, int maxWidth) {
        if (text == null || text.isEmpty() || this.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && this.font.width(trimmed + "..") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    protected int blendColors(int from, int to, float progress) {
        float clamped = Mth.clamp(progress, 0.0f, 1.0f);
        int a = Mth.floor(Mth.lerp(clamped, alpha(from), alpha(to)));
        int r = Mth.floor(Mth.lerp(clamped, red(from), red(to)));
        int g = Mth.floor(Mth.lerp(clamped, green(from), green(to)));
        int b = Mth.floor(Mth.lerp(clamped, blue(from), blue(to)));
        return a << 24 | r << 16 | g << 8 | b;
    }

    protected int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private void fillRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int color) {
        int clampedRadius = Math.min(radius, Math.min(width, height) / 2);
        for (int row = 0; row < height; row++) {
            int inset = row < clampedRadius
                    ? roundedInset(clampedRadius, row)
                    : row >= height - clampedRadius
                    ? roundedInset(clampedRadius, height - 1 - row)
                    : 0;
            guiGraphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private void fillCircle(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.sqrt(radius * radius - dy * dy);
            guiGraphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        if (radius <= 1) {
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int outer = (int) Math.sqrt(radius * radius - dy * dy);
            int innerRadius = Math.max(0, radius - 2);
            int inner = innerRadius == 0 ? 0 : (int) Math.sqrt(Math.max(0, innerRadius * innerRadius - dy * dy));
            guiGraphics.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
            guiGraphics.fill(cx + inner + 1, cy + dy, cx + outer + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleArcOutline(GuiGraphics guiGraphics, float cx, float cy, float radius, int color, float width) {
        drawRing(guiGraphics, cx, cy, Math.max(0.0f, radius - width), radius, 0.0f, 360.0f, color);
    }

    private void drawSeparator(GuiGraphics guiGraphics, float cx, float cy, float inner, float outer, float angleDeg, int color) {
        float radians = (float) Math.toRadians(angleDeg);
        float dx = Mth.cos(radians);
        float dy = Mth.sin(radians);
        float nx = -dy * 0.9f;
        float ny = dx * 0.9f;
        drawQuad(guiGraphics,
                cx + dx * inner - nx, cy + dy * inner - ny,
                cx + dx * inner + nx, cy + dy * inner + ny,
                cx + dx * outer + nx, cy + dy * outer + ny,
                cx + dx * outer - nx, cy + dy * outer - ny,
                color);
    }

    private void drawAnnularSegment(GuiGraphics guiGraphics, float cx, float cy,
                                    float inner, float outer, float startDeg, float sweepDeg, int color) {
        drawRing(guiGraphics, cx, cy, inner, outer, startDeg, sweepDeg, color);
    }

    private void drawAnnularSegmentOutline(GuiGraphics guiGraphics, float cx, float cy,
                                           float inner, float outer, float startDeg, float sweepDeg,
                                           int color, float width) {
        drawRing(guiGraphics, cx, cy, outer - width, outer, startDeg, sweepDeg, color);
        drawRing(guiGraphics, cx, cy, inner, inner + width, startDeg, sweepDeg, color);
        drawSeparator(guiGraphics, cx, cy, inner, outer, startDeg, color);
        drawSeparator(guiGraphics, cx, cy, inner, outer, startDeg + sweepDeg, color);
    }

    private void drawRing(GuiGraphics guiGraphics, float cx, float cy,
                          float inner, float outer, float startDeg, float sweepDeg, int color) {
        int segments = Math.max(12, Math.round(Math.abs(sweepDeg) / 5.5f));
        float startRad = (float) Math.toRadians(startDeg);
        float stepRad = (float) Math.toRadians(sweepDeg / segments);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        PoseStack.Pose pose = guiGraphics.pose().last();
        Matrix4f matrix = pose.pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = startRad + stepRad * i;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            addVertex(builder, matrix, cx + cos * outer, cy + sin * outer, color);
            addVertex(builder, matrix, cx + cos * inner, cy + sin * inner, color);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private void drawQuad(GuiGraphics guiGraphics,
                          float ax, float ay,
                          float bx, float by,
                          float cx, float cy,
                          float dx, float dy,
                          int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        PoseStack.Pose pose = guiGraphics.pose().last();
        Matrix4f matrix = pose.pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        addVertex(builder, matrix, ax, ay, color);
        addVertex(builder, matrix, bx, by, color);
        addVertex(builder, matrix, cx, cy, color);
        addVertex(builder, matrix, dx, dy, color);
        BufferUploader.drawWithShader(builder.end());
    }

    private void addVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, int color) {
        builder.vertex(matrix, x, y, 0.0f)
                .color(red(color), green(color), blue(color), alpha(color))
                .endVertex();
    }

    private int roundedInset(int radius, int row) {
        if (radius <= 0) {
            return 0;
        }
        double dy = radius - row - 0.5;
        double inside = Math.max(0.0, radius * radius - dy * dy);
        return Math.max(0, radius - (int) Math.floor(Math.sqrt(inside)) - 1);
    }

    private int alpha(int color) {
        return color >>> 24;
    }

    private int red(int color) {
        return color >> 16 & 0xFF;
    }

    private int green(int color) {
        return color >> 8 & 0xFF;
    }

    private int blue(int color) {
        return color & 0xFF;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
