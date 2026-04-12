package com.shiroha.mmdskin.ui.wheel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** 轮盘界面基类。 */
public abstract class AbstractWheelScreen extends Screen {

    public record WheelStyle(
            float screenRatio, float innerRatio,
            int lineColor, int lineColorDim, int highlightColor,
            int centerBg, int centerBorder, int textShadow
    ) {}

    public record WheelEntry(String primaryText, String secondaryText) {}

    private static final int TEXT_PRIMARY = 0xFFF7FBFF;
    private static final int TEXT_SECONDARY = 0xD6DCE7F5;
    private static final int TEXT_MUTED = 0xAEB8C7D8;

    protected final WheelStyle style;
    protected int centerX;
    protected int centerY;
    protected int outerRadius;
    protected int innerRadius;
    protected int selectedSlot = -1;

    private final SkiaWheelRenderer skiaRenderer = new SkiaWheelRenderer();

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
        if (skiaRenderer.renderWheelBase(this, entries, selectedSlot, slotPop, openProgress)) {
            return;
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            this.renderBackground(guiGraphics);
        }
        if (!entries.isEmpty()) {
            renderSegmentFallback(guiGraphics, entries);
        }
    }

    protected void renderCenterBubble(GuiGraphics guiGraphics, String text, int textColor) {
        if (skiaRenderer.renderCenterBubble(this, text, textColor, centerPop)) {
            return;
        }

        String display = fitText(text, Math.max(88, Math.round(innerRadius * 1.4f)));
        int textWidth = this.font.width(display);
        guiGraphics.drawString(this.font, display, centerX - textWidth / 2 + 1, centerY - 4, style.textShadow(), false);
        guiGraphics.drawString(this.font, display, centerX - textWidth / 2, centerY - 5, textColor, false);
    }

    protected void renderEmptyState(GuiGraphics guiGraphics, Component hint) {
        if (skiaRenderer.renderEmptyState(this, hint)) {
            return;
        }

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

    private void renderSegmentFallback(GuiGraphics guiGraphics, List<WheelEntry> entries) {
        int count = entries.size();
        double segmentAngle = 360.0 / count;
        int ringOuter = outerRadius;
        int ringInner = innerRadius;
        double gap = 1.2;

        for (int i = 0; i < count; i++) {
            WheelEntry entry = entries.get(i);
            float pop = slotPop[i];
            double start = i * segmentAngle - 90.0 + gap * 0.5;
            double sweep = Math.max(10.0, segmentAngle - gap);
            int fillColor = i == selectedSlot
                    ? blendColors(withAlpha(0x68B7FF, 156), withAlpha(0x8CCBFF, 182), pop * 0.45f)
                    : withAlpha(0xFFFFFF, 82);
            fillAnnularSector(guiGraphics, centerX, centerY, ringInner, ringOuter, start, start + sweep, fillColor);

            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2.0 - 90.0);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float textRadius = ringInner + (ringOuter - ringInner) * 0.54f + pop * 2.0f;
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

    private void fillAnnularSector(GuiGraphics guiGraphics, int cx, int cy, int inner, int outer,
                                   double startAngleDeg, double endAngleDeg, int color) {
        double normalizedStart = (startAngleDeg % 360.0 + 360.0) % 360.0;
        double normalizedEnd = (endAngleDeg % 360.0 + 360.0) % 360.0;
        int innerSq = inner * inner;
        int outerSq = outer * outer;
        for (int y = -outer; y <= outer; y++) {
            for (int x = -outer; x <= outer; x++) {
                int r2 = x * x + y * y;
                if (r2 < innerSq || r2 > outerSq) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(y, x));
                if (angle < 0) {
                    angle += 360.0;
                }
                boolean inRange = normalizedStart <= normalizedEnd
                        ? (angle >= normalizedStart && angle <= normalizedEnd)
                        : (angle >= normalizedStart || angle <= normalizedEnd);
                if (inRange) {
                    guiGraphics.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        skiaRenderer.dispose();
        super.removed();
    }
}
