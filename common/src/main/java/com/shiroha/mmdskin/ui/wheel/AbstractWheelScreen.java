package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** 轮盘界面基类。 */
public abstract class AbstractWheelScreen extends Screen {

    public record WheelStyle(
            float screenRatio, float innerRatio,
            int lineColor, int lineColorDim, int highlightColor,
            int centerBg, int centerBorder, int textShadow
    ) {}

    protected final WheelStyle style;
    protected int centerX, centerY;
    protected int outerRadius, innerRadius;
    protected int selectedSlot = -1;

    protected AbstractWheelScreen(Component title, WheelStyle style) {
        super(title);
        this.style = style;
    }

    protected abstract int getSlotCount();

    protected void initWheelLayout() {
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        int minDim = Math.min(this.width, this.height);
        this.outerRadius = (int) (minDim * style.screenRatio() / 2);
        this.innerRadius = (int) (this.outerRadius * style.innerRatio());
    }

    protected void updateSelectedSlot(int mouseX, int mouseY) {
        int count = getSlotCount();
        if (count <= 0) { selectedSlot = -1; return; }

        int dx = mouseX - centerX;
        int dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < innerRadius || distance > outerRadius + 50) {
            selectedSlot = -1;
            return;
        }

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;
        angle = (angle + 90) % 360;

        double segmentAngle = 360.0 / count;
        selectedSlot = (int) (angle / segmentAngle) % count;
    }

    protected void renderHighlight(GuiGraphics g) {
        int count = getSlotCount();
        if (selectedSlot < 0 || count <= 0) return;

        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        double segmentAngle = 360.0 / count;
        drawFilledSegment(matrix, selectedSlot, segmentAngle, style.highlightColor());

        RenderSystem.disableBlend();
    }

    protected void drawFilledSegment(Matrix4f matrix, int index, double segmentAngle, int color) {
        double startAngle = Math.toRadians(index * segmentAngle - 90);
        double endAngle = Math.toRadians((index + 1) * segmentAngle - 90);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            double angle = startAngle + (endAngle - startAngle) * i / steps;
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            buf.vertex(matrix, centerX + cosA * innerRadius, centerY + sinA * innerRadius, 0)
                    .color(r, g, b, a / 2).endVertex();
            buf.vertex(matrix, centerX + cosA * outerRadius, centerY + sinA * outerRadius, 0)
                    .color(r, g, b, a).endVertex();
        }

        BufferUploader.drawWithShader(buf.end());
    }

    protected void renderDividerLines(GuiGraphics g) {
        int count = getSlotCount();
        if (count <= 0) return;

        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        double segmentAngle = 360.0 / count;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(i * segmentAngle - 90);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            float iX = centerX + cosA * innerRadius;
            float iY = centerY + sinA * innerRadius;
            float oX = centerX + cosA * outerRadius;
            float oY = centerY + sinA * outerRadius;

            int lineColor = (i == selectedSlot || i == (selectedSlot + 1) % count)
                    ? style.lineColor() : style.lineColorDim();
            drawThickLine(matrix, iX, iY, oX, oY, 3.0f, lineColor);
        }

        RenderSystem.disableBlend();
    }

    protected void renderOuterRing(GuiGraphics g) {
        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        int steps = 64;
        float thickness = 3.0f;
        int r = (style.lineColorDim() >> 16) & 0xFF;
        int gC = (style.lineColorDim() >> 8) & 0xFF;
        int b = style.lineColorDim() & 0xFF;
        int a = (style.lineColorDim() >> 24) & 0xFF;

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(i * 360.0 / steps);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            buf.vertex(matrix, centerX + cosA * (outerRadius - thickness),
                    centerY + sinA * (outerRadius - thickness), 0).color(r, gC, b, a).endVertex();
            buf.vertex(matrix, centerX + cosA * (outerRadius + thickness),
                    centerY + sinA * (outerRadius + thickness), 0).color(r, gC, b, a).endVertex();
        }

        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    protected void renderCenterCircle(GuiGraphics g, String text, int textColor) {
        Matrix4f matrix = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        int bgR = (style.centerBg() >> 16) & 0xFF;
        int bgG = (style.centerBg() >> 8) & 0xFF;
        int bgB = style.centerBg() & 0xFF;
        int bgA = (style.centerBg() >> 24) & 0xFF;

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(matrix, centerX, centerY, 0).color(bgR, bgG, bgB, bgA).endVertex();

        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(i * 360.0 / steps);
            buf.vertex(matrix,
                    centerX + (float) (Math.cos(angle) * innerRadius),
                    centerY + (float) (Math.sin(angle) * innerRadius), 0)
                    .color(bgR, bgG, bgB, bgA).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());

        float thickness = 3.0f;
        int bR = (style.centerBorder() >> 16) & 0xFF;
        int bG = (style.centerBorder() >> 8) & 0xFF;
        int bB = style.centerBorder() & 0xFF;
        int bA = (style.centerBorder() >> 24) & 0xFF;

        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(i * 360.0 / steps);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);

            buf.vertex(matrix, centerX + cosA * (innerRadius - thickness),
                    centerY + sinA * (innerRadius - thickness), 0).color(bR, bG, bB, bA).endVertex();
            buf.vertex(matrix, centerX + cosA * (innerRadius + thickness),
                    centerY + sinA * (innerRadius + thickness), 0).color(bR, bG, bB, bA).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();

        int textWidth = this.font.width(text);
        g.drawString(this.font, text, centerX - textWidth / 2 + 1, centerY - 3, style.textShadow(), false);
        g.drawString(this.font, text, centerX - textWidth / 2, centerY - 4, textColor, false);
    }

    protected void drawThickLine(Matrix4f matrix, float x1, float y1, float x2, float y2,
                                  float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;

        float px = -dy / len * thickness * 0.5f;
        float py = dx / len * thickness * 0.5f;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(matrix, x1 + px, y1 + py, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1 - px, y1 - py, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2 + px, y2 + py, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2 - px, y2 - py, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    protected void drawRectOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
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
