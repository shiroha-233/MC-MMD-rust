package com.shiroha.mmdskin.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.shiroha.mmdskin.renderer.render.MmdSkinRendererPlayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/**
 * 动作选择轮盘界面
 * 按 Alt+Z 打开，通过鼠标方向选择动作
 * 
 * 重构版：线分隔设计，透明背景，更精致的视觉效果
 */
public class ActionWheelScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 轮盘参数（动态计算，占屏幕80%）
    private static final float WHEEL_SCREEN_RATIO = 0.80f;   // 占屏幕比例
    private static final float INNER_RATIO = 0.25f;          // 内圈占外圈比例
    private static final int LINE_COLOR = 0xFF60A0D0;        // 分隔线颜色（青蓝色，不透明）
    private static final int LINE_COLOR_DIM = 0xCC60A0D0;    // 较暗的分隔线
    private static final int HIGHLIGHT_COLOR = 0x60FFFFFF;   // 选中高亮
    private static final int CENTER_BG = 0xE0182030;         // 中心圆背景
    private static final int CENTER_BORDER = 0xFF60A0D0;     // 中心圆边框
    private static final int TEXT_SHADOW = 0xFF000000;       // 文字阴影
    
    private final List<ActionSlot> actionSlots;
    private int selectedSlot = -1;
    private int centerX, centerY;
    private int outerRadius, innerRadius;                    // 动态计算的半径

    public ActionWheelScreen() {
        super(Component.translatable("gui.mmdskin.action_wheel"));
        this.actionSlots = new ArrayList<>();
        initActionSlots();
    }

    private void initActionSlots() {
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        List<ActionWheelConfig.ActionEntry> actions = config.getDisplayedActions();
        
        for (int i = 0; i < actions.size(); i++) {
            ActionWheelConfig.ActionEntry action = actions.get(i);
            actionSlots.add(new ActionSlot(i, action.name, action.animId));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        
        // 动态计算轮盘大小（占屏幕较小边的80%）
        int minDimension = Math.min(this.width, this.height);
        this.outerRadius = (int) (minDimension * WHEEL_SCREEN_RATIO / 2);
        this.innerRadius = (int) (this.outerRadius * INNER_RATIO);
        
        // 配置按钮（右下角，更精致的样式）
        this.addRenderableWidget(Button.builder(Component.literal("⚙"), btn -> {
            this.minecraft.setScreen(new ActionWheelConfigScreen(this));
        }).bounds(this.width - 28, this.height - 28, 22, 22).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制全屏背景，保持透明
        
        if (actionSlots.isEmpty()) {
            // 没有配置动作时显示提示
            renderEmptyHint(guiGraphics);
        } else {
            updateSelectedSlot(mouseX, mouseY);
            renderWheelSegments(guiGraphics);
            renderDividerLines(guiGraphics);
            renderOuterRing(guiGraphics);
            renderCenterCircle(guiGraphics);
            renderActionLabels(guiGraphics);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderEmptyHint(GuiGraphics guiGraphics) {
        // 绘制提示背景
        int boxWidth = 220;
        int boxHeight = 40;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;
        
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC0182030);
        drawRectOutline(guiGraphics, boxX, boxY, boxWidth, boxHeight, LINE_COLOR);
        
        Component hint = Component.literal("没有配置动作，点击 ⚙ 配置");
        guiGraphics.drawCenteredString(this.font, hint, centerX, centerY - 4, 0xFFFFFF);
    }

    private void updateSelectedSlot(int mouseX, int mouseY) {
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
        
        double segmentAngle = 360.0 / actionSlots.size();
        selectedSlot = (int) (angle / segmentAngle) % actionSlots.size();
    }

    private void renderWheelSegments(GuiGraphics guiGraphics) {
        if (selectedSlot < 0) return;
        
        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        double segmentAngle = 360.0 / actionSlots.size();
        drawHighlightSegment(matrix, selectedSlot, segmentAngle, HIGHLIGHT_COLOR);
        
        RenderSystem.disableBlend();
    }
    
    private void drawHighlightSegment(Matrix4f matrix, int index, double segmentAngle, int color) {
        double startAngle = Math.toRadians(index * segmentAngle - 90);
        double endAngle = Math.toRadians((index + 1) * segmentAngle - 90);
        
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        
        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            double angle = startAngle + (endAngle - startAngle) * i / steps;
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            
            // 内圈点
            float iX = centerX + cosA * innerRadius;
            float iY = centerY + sinA * innerRadius;
            bufferBuilder.addVertex(matrix, iX, iY, 0).setColor(r, g, b, a / 2);
            
            // 外圈点
            float oX = centerX + cosA * outerRadius;
            float oY = centerY + sinA * outerRadius;
            bufferBuilder.addVertex(matrix, oX, oY, 0).setColor(r, g, b, a);
        }
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
    }

    private void renderDividerLines(GuiGraphics guiGraphics) {
        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        double segmentAngle = 360.0 / actionSlots.size();
        
        // 用三角形带绘制粗线条（每条线宽度 3 像素）
        for (int i = 0; i < actionSlots.size(); i++) {
            double angle = Math.toRadians(i * segmentAngle - 90);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            
            float iX = centerX + cosA * innerRadius;
            float iY = centerY + sinA * innerRadius;
            float oX = centerX + cosA * outerRadius;
            float oY = centerY + sinA * outerRadius;
            
            int lineColor = (i == selectedSlot || i == (selectedSlot + 1) % actionSlots.size()) 
                ? LINE_COLOR : LINE_COLOR_DIM;
            
            // 绘制粗线（用矩形模拟）
            drawThickLine(matrix, iX, iY, oX, oY, 3.0f, lineColor);
        }
        
        RenderSystem.disableBlend();
    }
    
    private void drawThickLine(Matrix4f matrix, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        
        // 垂直于线段方向的单位向量
        float px = -dy / len * thickness * 0.5f;
        float py = dx / len * thickness * 0.5f;
        
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.addVertex(matrix, x1 + px, y1 + py, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x1 - px, y1 - py, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x2 + px, y2 + py, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x2 - px, y2 - py, 0).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
    }
    
    private void renderOuterRing(GuiGraphics guiGraphics) {
        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        // 用三角形带绘制粗圆环
        int steps = 64;
        float thickness = 3.0f;
        
        int r = (LINE_COLOR_DIM >> 16) & 0xFF;
        int g = (LINE_COLOR_DIM >> 8) & 0xFF;
        int b = LINE_COLOR_DIM & 0xFF;
        int a = (LINE_COLOR_DIM >> 24) & 0xFF;
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(i * 360.0 / steps);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            
            float innerX = centerX + cosA * (outerRadius - thickness);
            float innerY = centerY + sinA * (outerRadius - thickness);
            float outerX = centerX + cosA * (outerRadius + thickness);
            float outerY = centerY + sinA * (outerRadius + thickness);
            
            bufferBuilder.addVertex(matrix, innerX, innerY, 0).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, outerX, outerY, 0).setColor(r, g, b, a);
        }
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void renderCenterCircle(GuiGraphics guiGraphics) {
        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        // 填充中心圆
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        
        int bgR = (CENTER_BG >> 16) & 0xFF;
        int bgG = (CENTER_BG >> 8) & 0xFF;
        int bgB = CENTER_BG & 0xFF;
        int bgA = (CENTER_BG >> 24) & 0xFF;
        
        bufferBuilder.addVertex(matrix, centerX, centerY, 0).setColor(bgR, bgG, bgB, bgA);
        
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(i * 360.0 / steps);
            float x = centerX + (float) (Math.cos(angle) * innerRadius);
            float y = centerY + (float) (Math.sin(angle) * innerRadius);
            bufferBuilder.addVertex(matrix, x, y, 0).setColor(bgR, bgG, bgB, bgA);
        }
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        
        // 绘制边框（用三角形带绘制粗圆环）
        float thickness = 3.0f;
        int borderR = (CENTER_BORDER >> 16) & 0xFF;
        int borderG = (CENTER_BORDER >> 8) & 0xFF;
        int borderB = CENTER_BORDER & 0xFF;
        int borderA = (CENTER_BORDER >> 24) & 0xFF;
        
        bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(i * 360.0 / steps);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            
            float iX = centerX + cosA * (innerRadius - thickness);
            float iY = centerY + sinA * (innerRadius - thickness);
            float oX = centerX + cosA * (innerRadius + thickness);
            float oY = centerY + sinA * (innerRadius + thickness);
            
            bufferBuilder.addVertex(matrix, iX, iY, 0).setColor(borderR, borderG, borderB, borderA);
            bufferBuilder.addVertex(matrix, oX, oY, 0).setColor(borderR, borderG, borderB, borderA);
        }
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        RenderSystem.disableBlend();
        
        // 中心文字（带阴影）
        String text = selectedSlot >= 0 ? "◆ 点击选择" : "选择动作";
        int textWidth = this.font.width(text);
        // 阴影
        guiGraphics.drawString(this.font, text, centerX - textWidth / 2 + 1, centerY - 3, TEXT_SHADOW, false);
        guiGraphics.drawString(this.font, text, centerX - textWidth / 2, centerY - 4, 0xFF60A0D0, false);
    }

    private void renderActionLabels(GuiGraphics guiGraphics) {
        double segmentAngle = 360.0 / actionSlots.size();
        
        // 计算最大允许文字宽度（基于扇区大小）
        int maxTextWidth = (int) (outerRadius * 0.6);
        
        for (int i = 0; i < actionSlots.size(); i++) {
            ActionSlot slot = actionSlots.get(i);
            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2 - 90);
            
            // 文字位置在扇形中间
            int textRadius = (innerRadius + outerRadius) / 2 + 5;
            int textX = centerX + (int) (Math.cos(angle) * textRadius);
            int textY = centerY + (int) (Math.sin(angle) * textRadius);
            
            String displayName = slot.name;
            // 截断过长的名称
            if (this.font.width(displayName) > maxTextWidth) {
                while (this.font.width(displayName + "..") > maxTextWidth && displayName.length() > 3) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "..";
            }
            
            Component text = Component.literal(displayName);
            int textWidth = this.font.width(text);
            
            // 选中时高亮显示
            boolean isSelected = (i == selectedSlot);
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFCCDDEE;
            
            // 所有文字都绘制阴影（提高可读性）
            guiGraphics.drawString(this.font, text, textX - textWidth / 2 + 1, textY - 3, TEXT_SHADOW, false);
            guiGraphics.drawString(this.font, text, textX - textWidth / 2 - 1, textY - 5, TEXT_SHADOW, false);
            guiGraphics.drawString(this.font, text, textX - textWidth / 2, textY - 4, textColor, false);
        }
    }
    
    private void drawRectOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);               // 上
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color); // 下
        guiGraphics.fill(x, y, x + 1, y + height, color);              // 左
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);  // 右
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && selectedSlot >= 0 && selectedSlot < actionSlots.size()) {
            ActionSlot slot = actionSlots.get(selectedSlot);
            executeAction(slot);
            this.onClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void executeAction(ActionSlot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            MmdSkinRendererPlayerHelper.CustomAnim(mc.player, slot.animId);
            ActionWheelNetworkHandler.sendActionToServer(slot.animId);
            logger.info("执行动作: {} ({})", slot.name, slot.animId);
        }
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

    private static class ActionSlot {
        @SuppressWarnings("unused")
        final int index;
        final String name;
        final String animId;

        ActionSlot(int index, String name, String animId) {
            this.index = index;
            this.name = name;
            this.animId = animId;
        }
    }
}
