/* 文件职责：提供轮盘界面的共享几何绘制、动画与基础交互。 */
package com.shiroha.mmdskin.ui.wheel;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public abstract class AbstractWheelScreen extends Screen {
    protected static final float CENTER_SAFE_RADIUS_RATIO = 0.72f;

    public record WheelStyle(
            float screenRatio,
            float innerRatio,
            int lineColor,
            int lineColorDim,
            int highlightColor,
            int centerBg,
            int centerBorder,
            int textShadow
    ) {
    }

    public record WheelEntry(String primaryText, String secondaryText) {
    }

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

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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
        double minRadius = innerRadius * CENTER_SAFE_RADIUS_RATIO;
        if (distance < minRadius || distance > outerRadius + 50.0) {
            selectedSlot = -1;
            return;
        }

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0.0) {
            angle += 360.0;
        }
        angle = (angle + 90.0) % 360.0;

        double segmentAngle = 360.0 / count;
        selectedSlot = (int) (angle / segmentAngle) % count;
    }

    protected void renderWheelBase(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, List<WheelEntry> entries) {
        updateSelectedSlot(mouseX, mouseY);
        updateAnimations(entries.size());
        renderBackdrop(guiGraphics, mouseX, mouseY, partialTick);
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

    protected void renderHighlight(GuiGraphics guiGraphics) {
        int count = getSlotCount();
        if (selectedSlot < 0 || count <= 0) {
            return;
        }
        double segmentAngle = 360.0 / count;
        drawFilledSegment(guiGraphics, selectedSlot, segmentAngle, style.highlightColor());
    }

    protected void drawFilledSegment(GuiGraphics guiGraphics, int index, double segmentAngle, int color) {
        float start = (float) (index * segmentAngle - 90.0 + SEGMENT_GAP_DEGREES * 0.5);
        float sweep = (float) Math.max(0.0, segmentAngle - SEGMENT_GAP_DEGREES);
        drawRing(guiGraphics, centerX, centerY, innerRadius, outerRadius, start, sweep, color);
    }

    protected void renderDividerLines(GuiGraphics guiGraphics) {
        int count = getSlotCount();
        if (count <= 0) {
            return;
        }
        double segmentAngle = 360.0 / count;
        for (int i = 0; i < count; i++) {
            double radians = Math.toRadians(i * segmentAngle - 90.0);
            float cos = (float) Math.cos(radians);
            float sin = (float) Math.sin(radians);
            drawThickLine(
                    guiGraphics,
                    centerX + cos * innerRadius,
                    centerY + sin * innerRadius,
                    centerX + cos * outerRadius,
                    centerY + sin * outerRadius,
                    1.4f,
                    style.lineColorDim()
            );
        }
    }

    protected void renderOuterRing(GuiGraphics guiGraphics) {
        drawRing(guiGraphics, centerX, centerY, outerRadius - 1.5f, outerRadius, 0.0f, 360.0f, style.lineColor());
    }

    protected void renderCenterCircle(GuiGraphics guiGraphics, String text, int textColor) {
        fillCircle(guiGraphics, centerX, centerY, innerRadius, style.centerBg());
        drawCircleOutline(guiGraphics, centerX, centerY, innerRadius, style.centerBorder());

        String fittedText = fitText(text, Math.max(24, innerRadius * 2 - 12));
        int textWidth = this.font.width(fittedText);
        guiGraphics.drawString(this.font, fittedText, centerX - textWidth / 2 + 1, centerY - 3, style.textShadow(), false);
        guiGraphics.drawString(this.font, fittedText, centerX - textWidth / 2, centerY - 4, textColor, false);
    }

    protected String fitText(String text, int maxWidth) {
        String value = text == null ? "" : text;
        if (value.isEmpty() || this.font.width(value) <= maxWidth) {
            return value;
        }

        while (value.length() > 1 && this.font.width(value + "..") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value.length() < (text == null ? 0 : text.length()) ? value + ".." : value;
    }

    protected void drawThickLine(GuiGraphics guiGraphics, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) {
            return;
        }
        float half = thickness * 0.5f;
        float nx = -dy / length * half;
        float ny = dx / length * half;
        drawQuad(
                guiGraphics,
                x1 - nx, y1 - ny,
                x1 + nx, y1 + ny,
                x2 + nx, y2 + ny,
                x2 - nx, y2 - ny,
                color
        );
    }

    protected void drawRectOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void renderBackdrop(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.minecraft.level == null) {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.fill(0, 0, this.width, this.height, BACKDROP_DIM);
    }

    private void renderSegmentWheel(GuiGraphics guiGraphics, List<WheelEntry> entries) {
        int count = entries.size();
        float segmentAngle = 360.0f / count;
        drawCircleArcOutline(guiGraphics, centerX, centerY, outerRadius + 3.0f, OUTLINE_DIM, 2.1f);
        drawCircleArcOutline(guiGraphics, centerX, centerY, innerRadius - 2.0f, withAlpha(style.lineColorDim(), 190), 1.8f);

        for (int i = 0; i < count; i++) {
            WheelEntry entry = entries.get(i);
            float pop = slotPop[i];
            float start = i * segmentAngle - 90.0f + SEGMENT_GAP_DEGREES * 0.5f;
            float sweep = Math.max(8.0f, segmentAngle - SEGMENT_GAP_DEGREES);
            float expandedOuter = outerRadius + pop * 5.0f + openProgress * 2.5f;
            float expandedInner = Math.max(12.0f, innerRadius - pop * 2.0f);

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
            int textX = Math.round(centerX + cos * textRadius);
            int textY = Math.round(centerY + sin * textRadius);
            float halfSweepRadians = (float) Math.toRadians(sweep * 0.5f);
            int maxTextWidth = Math.round(Mth.clamp(2.0f * textRadius * (float) Math.sin(halfSweepRadians) * 0.90f, 86.0f, 210.0f));

            if (entry.secondaryText() == null || entry.secondaryText().isEmpty()) {
                renderSingleEntry(guiGraphics, entry.primaryText(), textX, textY, pop, maxTextWidth);
            } else {
                renderDualEntry(guiGraphics, entry.primaryText(), entry.secondaryText(), textX, textY, pop, maxTextWidth);
            }
        }
    }

    private void renderCenterDecor(GuiGraphics guiGraphics) {
        int haloRadius = Math.max(18, Math.round(innerRadius * 0.86f + openProgress * 4.0f));
        drawCircleOutline(guiGraphics, centerX, centerY, haloRadius, withAlpha(style.lineColorDim(), 42));
        drawCircleOutline(guiGraphics, centerX, centerY, Math.max(12, haloRadius - 6), withAlpha(0xFFFFFF, 10));
    }

    private void renderSingleEntry(GuiGraphics guiGraphics, String text, int textX, int textY, float pop, int maxTextWidth) {
        String display = fitText(text, Math.max(62, maxTextWidth));
        int width = this.font.width(display);
        int color = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.36f + pop * 0.64f);
        guiGraphics.drawString(this.font, display, textX - width / 2 + 1, textY - 4, style.textShadow(), false);
        guiGraphics.drawString(this.font, display, textX - width / 2, textY - 5, color, false);
    }

    private void renderDualEntry(GuiGraphics guiGraphics, String icon, String label, int textX, int textY, float pop, int maxTextWidth) {
        String displayIcon = icon == null ? "" : icon;
        int iconWidth = this.font.width(displayIcon);
        int iconColor = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.42f + pop * 0.58f);
        guiGraphics.drawString(this.font, displayIcon, textX - iconWidth / 2 + 1, textY - 10, style.textShadow(), false);
        guiGraphics.drawString(this.font, displayIcon, textX - iconWidth / 2, textY - 11, iconColor, false);

        String displayLabel = fitText(label, Math.max(56, Math.round(maxTextWidth * 0.76f)));
        int labelWidth = this.font.width(displayLabel);
        int labelColor = blendColors(TEXT_MUTED, TEXT_SECONDARY, 0.52f + pop * 0.38f);
        guiGraphics.drawString(this.font, displayLabel, textX - labelWidth / 2 + 1, textY + 4, style.textShadow(), false);
        guiGraphics.drawString(this.font, displayLabel, textX - labelWidth / 2, textY + 3, labelColor, false);
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

    protected int blendColors(int from, int to, float progress) {
        float clamped = Mth.clamp(progress, 0.0f, 1.0f);
        int alpha = Mth.floor(Mth.lerp(clamped, alpha(from), alpha(to)));
        int red = Mth.floor(Mth.lerp(clamped, red(from), red(to)));
        int green = Mth.floor(Mth.lerp(clamped, green(from), green(to)));
        int blue = Mth.floor(Mth.lerp(clamped, blue(from), blue(to)));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    protected int withAlpha(int color, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | (color & 0x00FFFFFF);
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

    private void drawSeparator(GuiGraphics guiGraphics, float cx, float cy, float inner, float outer, float angleDegrees, int color) {
        float radians = (float) Math.toRadians(angleDegrees);
        float dx = Mth.cos(radians);
        float dy = Mth.sin(radians);
        float nx = -dy * 0.9f;
        float ny = dx * 0.9f;
        drawQuad(
                guiGraphics,
                cx + dx * inner - nx, cy + dy * inner - ny,
                cx + dx * inner + nx, cy + dy * inner + ny,
                cx + dx * outer + nx, cy + dy * outer + ny,
                cx + dx * outer - nx, cy + dy * outer - ny,
                color
        );
    }

    private void drawAnnularSegment(GuiGraphics guiGraphics, float cx, float cy, float inner, float outer, float startDegrees, float sweepDegrees, int color) {
        drawRing(guiGraphics, cx, cy, inner, outer, startDegrees, sweepDegrees, color);
    }

    private void drawAnnularSegmentOutline(
            GuiGraphics guiGraphics,
            float cx,
            float cy,
            float inner,
            float outer,
            float startDegrees,
            float sweepDegrees,
            int color,
            float width
    ) {
        drawRing(guiGraphics, cx, cy, outer - width, outer, startDegrees, sweepDegrees, color);
        drawRing(guiGraphics, cx, cy, inner, inner + width, startDegrees, sweepDegrees, color);
        drawSeparator(guiGraphics, cx, cy, inner, outer, startDegrees, color);
        drawSeparator(guiGraphics, cx, cy, inner, outer, startDegrees + sweepDegrees, color);
    }

    private void drawRing(GuiGraphics guiGraphics, float cx, float cy, float inner, float outer, float startDegrees, float sweepDegrees, int color) {
        if (outer <= 0.0f || sweepDegrees <= 0.0f) {
            return;
        }
        float innerRadius = Math.max(0.0f, inner);
        if (innerRadius >= outer) {
            return;
        }
        float outerSq = outer * outer;
        float innerSq = innerRadius * innerRadius;
        boolean fullCircle = sweepDegrees >= 360.0f;
        float normStart = ((startDegrees % 360.0f) + 360.0f) % 360.0f;
        float endDeg = normStart + Math.min(sweepDegrees, 360.0f);

        int yStart = (int) Math.floor(cy - outer);
        int yEnd = (int) Math.ceil(cy + outer);

        for (int y = yStart; y <= yEnd; y++) {
            float dy = y + 0.5f - cy;
            float dySq = dy * dy;
            if (dySq > outerSq) {
                continue;
            }
            int xStart = (int) Math.floor(cx - outer);
            int xEnd = (int) Math.ceil(cx + outer);
            int spanStart = -1;
            for (int x = xStart; x <= xEnd; x++) {
                float dx = x + 0.5f - cx;
                float distSq = dx * dx + dySq;
                boolean inRing = distSq >= innerSq && distSq <= outerSq;
                boolean inside = false;
                if (inRing) {
                    if (fullCircle) {
                        inside = true;
                    } else {
                        float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
                        if (ang < 0.0f) {
                            ang += 360.0f;
                        }
                        if (ang >= normStart && ang <= endDeg) {
                            inside = true;
                        } else if (endDeg > 360.0f && ang + 360.0f <= endDeg) {
                            inside = true;
                        }
                    }
                }
                if (inside) {
                    if (spanStart < 0) {
                        spanStart = x;
                    }
                } else if (spanStart >= 0) {
                    guiGraphics.fill(spanStart, y, x, y + 1, color);
                    spanStart = -1;
                }
            }
            if (spanStart >= 0) {
                guiGraphics.fill(spanStart, y, xEnd + 1, y + 1, color);
            }
        }
    }

    private void drawQuad(
            GuiGraphics guiGraphics,
            float ax,
            float ay,
            float bx,
            float by,
            float cx,
            float cy,
            float dx,
            float dy,
            int color
    ) {
        float[] xs = {ax, bx, cx, dx};
        float[] ys = {ay, by, cy, dy};
        float minY = Math.min(Math.min(ay, by), Math.min(cy, dy));
        float maxY = Math.max(Math.max(ay, by), Math.max(cy, dy));
        int yStart = (int) Math.floor(minY);
        int yEnd = (int) Math.ceil(maxY);

        for (int y = yStart; y <= yEnd; y++) {
            float yc = y + 0.5f;
            float spanMin = Float.POSITIVE_INFINITY;
            float spanMax = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                float yi = ys[i];
                float yj = ys[j];
                boolean crosses = (yi <= yc && yj > yc) || (yj <= yc && yi > yc);
                if (!crosses) {
                    continue;
                }
                float t = (yc - yi) / (yj - yi);
                float x = xs[i] + t * (xs[j] - xs[i]);
                if (x < spanMin) {
                    spanMin = x;
                }
                if (x > spanMax) {
                    spanMax = x;
                }
            }
            if (spanMin <= spanMax) {
                int x1 = (int) Math.floor(spanMin);
                int x2 = (int) Math.ceil(spanMax);
                if (x2 > x1) {
                    guiGraphics.fill(x1, y, x2, y + 1, color);
                }
            }
        }
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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
