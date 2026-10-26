/* 文件职责：提供轮盘界面的共享几何绘制、动画与基础交互。 */
package com.shiroha.mmdskin.ui.wheel;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    /**
     * 一条分区标签的最终排版结果：显示文字 + 等比缩放系数。
     * 宽度按未缩放前的文字算，渲染时再整体乘上缩放系数。
     */
    private static final class LabelLayout {
        String text;
        float scale;
        int width;

        LabelLayout(String text, int width) {
            this.text = text;
            this.width = width;
            this.scale = 1.0f;
        }
    }

    private static final int TEXT_PRIMARY = TranslucentTrayChrome.TITLE_TEXT;
    private static final int TEXT_SECONDARY = TranslucentTrayChrome.BODY_TEXT;
    private static final int TEXT_MUTED = TranslucentTrayChrome.SUBTITLE_TEXT;
    private static final int OUTLINE_DIM = 0x26FFFFFF;
    private static final int BACKDROP_DIM = TranslucentTrayChrome.OVERLAY;
    private static final float SEGMENT_GAP_DEGREES = 1.8f;
    private static final float CENTER_BUBBLE_SCALE = 0.74f;
    /** 相邻分区标签之间必须留出的横向间隙（GUI 像素），否则两段文字会连成一串分不清。 */
    private static final float LABEL_GUTTER = 8.0f;
    /** 标签等比缩放的下限；再挤就改成截断文字，也不能让字缩到看不清。 */
    private static final float LABEL_MIN_SCALE = 0.72f;
    /** 竖直方向错开超过这个距离就不会横向压字（标签实际渲染高度约 9~10 像素）。 */
    private static final float LABEL_TEXT_HEIGHT = 12.0f;

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
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
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

    protected void renderWheelBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, List<WheelEntry> entries) {
        updateSelectedSlot(mouseX, mouseY);
        updateAnimations(entries.size());
        renderBackdrop(guiGraphics, mouseX, mouseY, partialTick);
        if (!entries.isEmpty()) {
            renderSegmentWheel(guiGraphics, entries);
        }
        renderCenterDecor(guiGraphics);
    }

    protected void renderCenterBubble(GuiGraphicsExtractor guiGraphics, String text, int textColor) {
        float bubbleScale = 0.98f + centerPop * 0.06f;
        int bubbleRadius = Math.max(30, Math.round(innerRadius * CENTER_BUBBLE_SCALE * bubbleScale));
        fillCircle(guiGraphics, centerX, centerY, bubbleRadius + 2, withAlpha(style.centerBorder(), 72));
        drawCircleOutline(guiGraphics, centerX, centerY, bubbleRadius + 1, withAlpha(0xFFFFFF, 16));

        String display = fitText(text, Math.max(88, Math.round(innerRadius * 1.4f)));
        int textWidth = this.font.width(display);
        guiGraphics.text(this.font, display, centerX - textWidth / 2 + 1, centerY - 4, style.textShadow(), false);
        guiGraphics.text(this.font, display, centerX - textWidth / 2, centerY - 5, textColor, false);
    }

    protected void renderEmptyState(GuiGraphicsExtractor guiGraphics, Component hint) {
        int width = Math.max(180, this.font.width(hint) + 48);
        int height = 36;
        int x = centerX - width / 2;
        int y = centerY + outerRadius / 2 + 18;
        fillRoundedRect(guiGraphics, x, y, width, height, 12, withAlpha(style.centerBorder(), 228));
        fillRoundedRect(guiGraphics, x + 1, y + 1, width - 2, height - 2, 11, withAlpha(style.centerBg(), 196));
        guiGraphics.centeredText(this.font, hint, centerX, y + 13, TEXT_PRIMARY);
    }

    protected Button createWheelIconButton(Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress)
                .bounds(this.width - 36, this.height - 34, 28, 24)
                .build();
    }

    protected void renderHighlight(GuiGraphicsExtractor guiGraphics) {
        int count = getSlotCount();
        if (selectedSlot < 0 || count <= 0) {
            return;
        }

        double segmentAngle = 360.0 / count;
        drawFilledSegment(guiGraphics, selectedSlot, segmentAngle, style.highlightColor());
    }

    protected void drawFilledSegment(GuiGraphicsExtractor guiGraphics, int index, double segmentAngle, int color) {
        drawRing(guiGraphics, centerX, centerY, innerRadius, outerRadius,
                (float) (index * segmentAngle - 90.0), (float) segmentAngle, color);
    }

    protected void renderDividerLines(GuiGraphicsExtractor guiGraphics) {
        int count = getSlotCount();
        if (count <= 0) {
            return;
        }

        double segmentAngle = 360.0 / count;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(i * segmentAngle - 90.0);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            float innerX = centerX + cos * innerRadius;
            float innerY = centerY + sin * innerRadius;
            float outerX = centerX + cos * outerRadius;
            float outerY = centerY + sin * outerRadius;

            int lineColor = i == selectedSlot || i == (selectedSlot + 1) % count
                    ? style.lineColor()
                    : style.lineColorDim();
            drawThickLine(guiGraphics, innerX, innerY, outerX, outerY, 3.0f, lineColor);
        }
    }

    protected void renderOuterRing(GuiGraphicsExtractor guiGraphics) {
        float thickness = 3.0f;
        drawRing(guiGraphics, centerX, centerY, outerRadius - thickness, outerRadius + thickness,
                0.0F, 360.0F, style.lineColorDim());
    }

    protected void renderCenterCircle(GuiGraphicsExtractor guiGraphics, String text, int textColor) {
        drawDisk(guiGraphics, centerX, centerY, innerRadius, style.centerBg());
        float thickness = 3.0f;
        drawRing(guiGraphics, centerX, centerY, innerRadius - thickness, innerRadius + thickness,
                0.0F, 360.0F, style.centerBorder());

        String fittedText = fitText(text, Math.max(24, innerRadius * 2 - 12));
        int textWidth = this.font.width(fittedText);
        guiGraphics.text(this.font, fittedText, centerX - textWidth / 2 + 1, centerY - 3, style.textShadow(), false);
        guiGraphics.text(this.font, fittedText, centerX - textWidth / 2, centerY - 4, textColor, false);
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

    protected void drawThickLine(GuiGraphicsExtractor guiGraphics, float x1, float y1, float x2, float y2, float thickness, int color) {
        fillRotatedRect(guiGraphics, x1, y1, x2, y2, thickness, color);
    }

    protected void drawRectOutline(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void renderBackdrop(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.minecraft.level == null) {
            super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.fill(0, 0, this.width, this.height, BACKDROP_DIM);
    }

    private void renderSegmentWheel(GuiGraphicsExtractor guiGraphics, List<WheelEntry> entries) {
        int count = entries.size();
        float segmentAngle = 360.0f / count;
        drawCircleArcOutline(guiGraphics, centerX, centerY, outerRadius + 3.0f, OUTLINE_DIM, 2.1f);
        drawCircleArcOutline(guiGraphics, centerX, centerY, innerRadius - 2.0f, withAlpha(style.lineColorDim(), 190), 1.8f);

        // 先算好每条标签的排版（可能需要缩放或截断），再画扇区和文字
        LabelLayout[] layouts = computeLabelLayouts(entries, segmentAngle);

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
            float textRadius = labelRadius(i);
            int textX = Math.round(centerX + cos * textRadius);
            int textY = Math.round(centerY + sin * textRadius);
            float halfSweepRadians = (float) Math.toRadians(sweep * 0.5f);
            int maxTextWidth = Math.round(Mth.clamp(2.0f * textRadius * (float) Math.sin(halfSweepRadians) * 0.90f, 86.0f, 210.0f));

            if (entry.secondaryText() == null || entry.secondaryText().isEmpty()) {
                renderSingleEntry(guiGraphics, layouts[i], textX, textY, pop);
            } else {
                renderDualEntry(guiGraphics, layouts[i], entry.secondaryText(), textX, textY, pop, maxTextWidth);
            }
        }
    }

    /**
     * 第 index 个分区的标签锚点半径。排版和绘制必须用同一个半径，否则算出来的间距会偏。
     */
    private float labelRadius(int index) {
        float pop = slotPop[index];
        float expandedOuter = outerRadius + pop * 5.0f + openProgress * 2.5f;
        float expandedInner = Math.max(12.0f, innerRadius - pop * 2.0f);
        return expandedInner + (expandedOuter - expandedInner) * 0.54f + pop * 2.0f;
    }

    /**
     * 算出每条标签的显示文字与缩放系数，保证相邻分区的标签不会横向压在一起。
     *
     * 轮盘标签是水平排版、锚点落在各自扇区中线上的。两个相邻锚点如果竖直方向离得够开，
     * 即使横向有重叠也不会真的压到一起；只有像 12 点方向那种上下对齐的相邻扇区
     * （"模组设置" 与 "模型切换"）会横向贴死，渲染成一串分不清的字。
     *
     * 所以这里只对"会横向相撞"的相邻对做限制：缩放之后要满足
     * (w1 + w2) / 2 + 间隙 &lt;= 两个锚点的水平间距。优先整体等比缩小（字形完整，
     * 比截断成"模组设.."好认），缩到下限还放不下才截断文字。
     */
    private LabelLayout[] computeLabelLayouts(List<WheelEntry> entries, float segmentAngle) {
        int count = entries.size();
        LabelLayout[] layouts = new LabelLayout[count];
        float[] anchorX = new float[count];
        float[] anchorY = new float[count];

        for (int i = 0; i < count; i++) {
            WheelEntry entry = entries.get(i);
            String text = entry.primaryText() == null ? "" : entry.primaryText();
            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2.0 - 90.0);
            float radius = labelRadius(i);
            anchorX[i] = (float) (centerX + Math.cos(angle) * radius);
            anchorY[i] = (float) (centerY + Math.sin(angle) * radius);

            // 先按扇区宽度做一次常规截断（和原来的行为一致），下面再处理相邻标签互相挤压
            float sweep = Math.max(8.0f, segmentAngle - SEGMENT_GAP_DEGREES);
            float halfSweepRadians = (float) Math.toRadians(sweep * 0.5f);
            int maxTextWidth = Math.round(Mth.clamp(2.0f * radius * (float) Math.sin(halfSweepRadians) * 0.90f, 86.0f, 210.0f));
            text = fitText(text, Math.max(62, maxTextWidth));

            // 双行布局（图标 + 文字）以文字那行为准
            int width = this.font.width(text);
            if (entry.secondaryText() != null && !entry.secondaryText().isEmpty()) {
                String secondary = fitText(entry.secondaryText(), Math.max(56, Math.round(maxTextWidth * 0.76f)));
                width = Math.max(width, this.font.width(secondary));
            }
            layouts[i] = new LabelLayout(text, width);
        }
        if (count < 2) {
            return layouts;
        }

        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            if (Math.abs(anchorY[i] - anchorY[j]) >= LABEL_TEXT_HEIGHT) {
                continue;   // 上下错开，横向贴在一起也压不到
            }
            float chord = Math.abs(anchorX[i] - anchorX[j]);
            if (chord <= LABEL_GUTTER) {
                continue;   // 槽位太多、扇区本身就不够宽，几何上没法再分开，保持原样
            }
            float budget = 2.0f * (chord - LABEL_GUTTER);
            float pairWidth = layouts[i].width + layouts[j].width;
            if (pairWidth <= budget) {
                continue;   // 本来就放得下
            }
            float scale = budget / pairWidth;
            if (scale >= LABEL_MIN_SCALE) {
                layouts[i].scale = Math.min(layouts[i].scale, scale);
                layouts[j].scale = Math.min(layouts[j].scale, scale);
            } else {
                // 缩到下限就会看不清，改为截断文字：按宽度比例分额度
                int budgetI = (int) (budget * layouts[i].width / pairWidth);
                int budgetJ = (int) (budget * layouts[j].width / pairWidth);
                layouts[i].text = fitText(layouts[i].text, budgetI);
                layouts[i].width = this.font.width(layouts[i].text);
                layouts[j].text = fitText(layouts[j].text, budgetJ);
                layouts[j].width = this.font.width(layouts[j].text);
            }
        }
        return layouts;
    }

    private void renderCenterDecor(GuiGraphicsExtractor guiGraphics) {
        int haloRadius = Math.max(18, Math.round(innerRadius * 0.86f + openProgress * 4.0f));
        drawCircleOutline(guiGraphics, centerX, centerY, haloRadius, withAlpha(style.lineColorDim(), 42));
        drawCircleOutline(guiGraphics, centerX, centerY, Math.max(12, haloRadius - 6), withAlpha(0xFFFFFF, 10));
    }

    private void renderSingleEntry(GuiGraphicsExtractor guiGraphics, LabelLayout layout, int textX, int textY, float pop) {
        String display = layout.text;
        int width = layout.width;
        int color = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.36f + pop * 0.64f);
        if (layout.scale >= 0.999f) {
            guiGraphics.text(this.font, display, textX - width / 2 + 1, textY - 4, style.textShadow(), false);
            guiGraphics.text(this.font, display, textX - width / 2, textY - 5, color, false);
            return;
        }
        // 挤在一起的标签整体等比缩小：字形完整，比截断成"模组设.."好认
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(textX, textY);
        guiGraphics.pose().scale(layout.scale, layout.scale);
        guiGraphics.text(this.font, display, -width / 2 + 1, -4, style.textShadow(), false);
        guiGraphics.text(this.font, display, -width / 2, -5, color, false);
        guiGraphics.pose().popMatrix();
    }

    private void renderDualEntry(GuiGraphicsExtractor guiGraphics, LabelLayout layout, String label, int textX, int textY, float pop, int maxTextWidth) {
        String displayIcon = layout.text == null ? "" : layout.text;
        int iconWidth = this.font.width(displayIcon);
        int iconColor = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.42f + pop * 0.58f);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(textX, textY - 10.0f);
        guiGraphics.pose().scale(layout.scale * (1.0f + pop * 0.08f), layout.scale * (1.0f + pop * 0.08f));
        guiGraphics.text(this.font, displayIcon, -iconWidth / 2 + 1, 0, style.textShadow(), false);
        guiGraphics.text(this.font, displayIcon, -iconWidth / 2, -1, iconColor, false);
        guiGraphics.pose().popMatrix();

        String displayLabel = fitText(label, Math.max(56, Math.round(maxTextWidth * 0.76f)));
        int labelWidth = this.font.width(displayLabel);
        int labelColor = blendColors(TEXT_MUTED, TEXT_SECONDARY, 0.52f + pop * 0.38f);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(textX, textY + 4.0f);
        guiGraphics.pose().scale(layout.scale * (0.90f + pop * 0.02f), layout.scale * (0.90f + pop * 0.02f));
        guiGraphics.text(this.font, displayLabel, -labelWidth / 2 + 1, 0, style.textShadow(), false);
        guiGraphics.text(this.font, displayLabel, -labelWidth / 2, -1, labelColor, false);
        guiGraphics.pose().popMatrix();
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

    private void fillRoundedRect(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int radius, int color) {
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

    private void fillCircle(GuiGraphicsExtractor guiGraphics, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.sqrt(radius * radius - dy * dy);
            guiGraphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphicsExtractor guiGraphics, int cx, int cy, int radius, int color) {
        if (radius <= 1) {
            return;
        }
        drawRing(guiGraphics, cx, cy, Math.max(0.0f, radius - 2.0f), radius, 0.0f, 360.0f, color);
    }

    private void drawCircleArcOutline(GuiGraphicsExtractor guiGraphics, float cx, float cy, float radius, int color, float width) {
        drawRing(guiGraphics, cx, cy, Math.max(0.0f, radius - width), radius, 0.0f, 360.0f, color);
    }

    private void drawSeparator(GuiGraphicsExtractor guiGraphics, float cx, float cy, float inner, float outer, float angleDegrees, int color) {
        float radians = (float) Math.toRadians(angleDegrees);
        float dx = Mth.cos(radians);
        float dy = Mth.sin(radians);
        fillRotatedRect(
                guiGraphics,
                cx + dx * inner, cy + dy * inner,
                cx + dx * outer, cy + dy * outer,
                1.8f,
                color
        );
    }

    private void drawAnnularSegment(GuiGraphicsExtractor guiGraphics, float cx, float cy, float inner, float outer, float startDegrees, float sweepDegrees, int color) {
        drawRing(guiGraphics, cx, cy, inner, outer, startDegrees, sweepDegrees, color);
    }

    private void drawAnnularSegmentOutline(
            GuiGraphicsExtractor guiGraphics,
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

    /**
     * 26.3 的 GUI 只收集“矩形”，不再提供自定义几何体，因此环形扇段按角度切成若干条
     * 以半径方向为轴的旋转矩形来近似（外沿/内沿的误差小于 1 像素）。
     */
    private void drawRing(GuiGraphicsExtractor guiGraphics, float cx, float cy, float inner, float outer, float startDegrees, float sweepDegrees, int color) {
        if (outer <= inner) {
            return;
        }
        if (inner <= 0.0f && Math.abs(sweepDegrees) >= 360.0f) {
            // 内半径退化为 0 时整圈就是一个圆盘，直接按行填充即可
            fillCircle(guiGraphics, Math.round(cx), Math.round(cy), Math.round(outer), color);
            return;
        }
        int segments = Math.max(12, Math.round(Math.abs(sweepDegrees) / 5.5f));
        float startRadians = (float) Math.toRadians(startDegrees);
        float stepRadians = (float) Math.toRadians(sweepDegrees / segments);
        float midRadius = (inner + outer) * 0.5f;
        float sliceWidth = midRadius * (float) Math.sin(stepRadians * 0.5f) * 2.0f;
        for (int i = 0; i < segments; i++) {
            float angle = startRadians + stepRadians * (i + 0.5f);
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            fillRotatedRect(
                    guiGraphics,
                    cx + cos * inner, cy + sin * inner,
                    cx + cos * outer, cy + sin * outer,
                    sliceWidth,
                    color
            );
        }
    }

    private void drawDisk(GuiGraphicsExtractor guiGraphics, float cx, float cy, float radius, int color) {
        fillCircle(guiGraphics, Math.round(cx), Math.round(cy), Math.round(radius), color);
    }

    /**
     * 以 (x0,y0)-(x1,y1) 为轴线、厚度为 thickness 的旋转矩形。
     * 借助 2D 姿态矩阵（Matrix3x2fStack）实现，矩形仍以 fill() 收集进 GuiRenderState。
     */
    private void fillRotatedRect(GuiGraphicsExtractor guiGraphics, float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f || thickness <= 0.0f) {
            return;
        }

        float angle = (float) Math.atan2(dy, dx);
        float midX = (x0 + x1) * 0.5f;
        float midY = (y0 + y1) * 0.5f;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(midX, midY);
        guiGraphics.pose().rotate(angle);
        guiGraphics.pose().scale(length * 0.5f, thickness * 0.5f);
        guiGraphics.fill(-1, -1, 1, 1, color);
        guiGraphics.pose().popMatrix();
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
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
