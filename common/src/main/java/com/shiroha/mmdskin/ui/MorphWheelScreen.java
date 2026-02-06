package com.shiroha.mmdskin.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 表情选择轮盘界面
 * 与动作轮盘保持一致的UI风格
 */
public class MorphWheelScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 轮盘尺寸比例
    private static final float WHEEL_SCREEN_RATIO = 0.80f;
    private static final float INNER_RATIO = 0.25f;
    
    // 颜色常量
    private static final int LINE_COLOR = 0xFF60A0D0;
    private static final int HIGHLIGHT_COLOR = 0x60FFFFFF;
    private static final int CENTER_BG = 0xE0182030;
    private static final int CENTER_BORDER = 0xFF60A0D0;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0x80000000;
    
    private final int triggerKeyCode;
    private int centerX, centerY;
    private int outerRadius, innerRadius;
    private int selectedSlot = -1;
    
    private List<MorphSlot> morphSlots = new ArrayList<>();
    
    private static class MorphSlot {
        String displayName;
        String morphName;
        String filePath;
        
        MorphSlot(String displayName, String morphName, String filePath) {
            this.displayName = displayName;
            this.morphName = morphName;
            this.filePath = filePath;
        }
    }
    
    public MorphWheelScreen(int triggerKeyCode) {
        super(Component.translatable("gui.mmdskin.morph_wheel"));
        this.triggerKeyCode = triggerKeyCode;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 计算轮盘尺寸
        int size = (int) (Math.min(width, height) * WHEEL_SCREEN_RATIO);
        outerRadius = size / 2;
        innerRadius = (int) (outerRadius * INNER_RATIO);
        centerX = width / 2;
        centerY = height / 2;
        
        // 加载表情槽位
        initMorphSlots();
        
        // 配置按钮（右下角）
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            net.minecraft.network.chat.Component.literal("⚙"), btn -> {
                this.minecraft.setScreen(new MorphWheelConfigScreen(this));
            }).bounds(this.width - 28, this.height - 28, 22, 22).build());
    }
    
    private void initMorphSlots() {
        morphSlots.clear();
        
        MorphWheelConfig config = MorphWheelConfig.getInstance();
        List<MorphWheelConfig.MorphEntry> displayed = config.getDisplayedMorphs();
        
        for (MorphWheelConfig.MorphEntry entry : displayed) {
            // 构建文件路径
            String filePath = getMorphFilePath(entry);
            morphSlots.add(new MorphSlot(entry.displayName, entry.morphName, filePath));
        }
        
        // 添加"重置表情"选项
        morphSlots.add(new MorphSlot("重置表情", "__reset__", null));
        
        logger.info("表情轮盘: 加载 {} 个槽位", morphSlots.size());
    }
    
    private String getMorphFilePath(MorphWheelConfig.MorphEntry entry) {
        switch (entry.source) {
            case "DEFAULT":
                return PathConstants.getDefaultMorphPath(entry.morphName);
            case "CUSTOM":
                return PathConstants.getCustomMorphPath(entry.morphName);
            case "MODEL":
                if (entry.modelName != null) {
                    return PathConstants.getModelMorphPath(entry.modelName, entry.morphName);
                }
            default:
                return PathConstants.getCustomMorphPath(entry.morphName);
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 半透明背景
        guiGraphics.fill(0, 0, width, height, 0x80000000);
        
        // 更新选中状态
        updateSelectedSlot(mouseX, mouseY);
        
        // 绘制轮盘
        if (!morphSlots.isEmpty()) {
            renderWheel(guiGraphics, mouseX, mouseY);
        } else {
            // 无表情时显示提示
            String hint = "暂无可用表情";
            int hintWidth = font.width(hint);
            guiGraphics.drawString(font, hint, centerX - hintWidth / 2, centerY - 4, TEXT_COLOR);
        }
        
        // 绘制中心圆
        renderCenterCircle(guiGraphics);
        
        // 绘制标题
        String title = "表情选择";
        int titleWidth = font.width(title);
        guiGraphics.drawString(font, title, centerX - titleWidth / 2, centerY - 6, TEXT_COLOR);
        
        // 绘制选中的表情名称
        if (selectedSlot >= 0 && selectedSlot < morphSlots.size()) {
            MorphSlot slot = morphSlots.get(selectedSlot);
            int nameWidth = font.width(slot.displayName);
            guiGraphics.drawString(font, slot.displayName, centerX - nameWidth / 2, centerY + 6, 0xFFFFFF00);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void updateSelectedSlot(int mouseX, int mouseY) {
        if (morphSlots.isEmpty()) {
            selectedSlot = -1;
            return;
        }
        
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
        
        double segmentAngle = 360.0 / morphSlots.size();
        selectedSlot = (int) (angle / segmentAngle) % morphSlots.size();
    }
    
    private void renderWheel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int segments = morphSlots.size();
        double segmentAngle = 360.0 / segments;
        
        Matrix4f matrix = guiGraphics.pose().last().pose();
        
        // 绘制高亮扇区
        if (selectedSlot >= 0) {
            double startAngle = Math.toRadians(-90 + selectedSlot * segmentAngle);
            double endAngle = Math.toRadians(-90 + (selectedSlot + 1) * segmentAngle);
            renderFilledArc(matrix, centerX, centerY, innerRadius + 5, outerRadius - 5, 
                startAngle, endAngle, HIGHLIGHT_COLOR);
        }
        
        // 绘制分隔线
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        for (int i = 0; i < segments; i++) {
            double angle = Math.toRadians(-90 + i * segmentAngle);
            float x1 = centerX + (float) (Math.cos(angle) * innerRadius);
            float y1 = centerY + (float) (Math.sin(angle) * innerRadius);
            float x2 = centerX + (float) (Math.cos(angle) * outerRadius);
            float y2 = centerY + (float) (Math.sin(angle) * outerRadius);
            
            drawThickLine(matrix, x1, y1, x2, y2, 2.0f, LINE_COLOR);
        }
        
        // 绘制外圈
        renderOuterRing(guiGraphics);
        
        // 绘制标签
        for (int i = 0; i < segments; i++) {
            MorphSlot slot = morphSlots.get(i);
            double midAngle = Math.toRadians(-90 + (i + 0.5) * segmentAngle);
            int labelRadius = (innerRadius + outerRadius) / 2;
            
            int labelX = centerX + (int) (Math.cos(midAngle) * labelRadius);
            int labelY = centerY + (int) (Math.sin(midAngle) * labelRadius);
            
            String label = slot.displayName;
            if (label.length() > 8) {
                label = label.substring(0, 7) + "..";
            }
            
            int textWidth = font.width(label);
            int textColor = (i == selectedSlot) ? 0xFFFFFF00 : TEXT_COLOR;
            
            // 绘制阴影
            guiGraphics.drawString(font, label, labelX - textWidth / 2 + 1, labelY - 4 + 1, TEXT_SHADOW, false);
            guiGraphics.drawString(font, label, labelX - textWidth / 2, labelY - 4, textColor, false);
        }
    }
    
    private void drawThickLine(Matrix4f matrix, float x1, float y1, float x2, float y2, float thickness, int color) {
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
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.addVertex(matrix, x1 + px, y1 + py, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x1 - px, y1 - py, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x2 + px, y2 + py, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x2 - px, y2 - py, 0).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
    }
    
    private void renderFilledArc(Matrix4f matrix, int cx, int cy, int innerR, int outerR, 
                                  double startAngle, double endAngle, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        
        int steps = 32;
        double angleStep = (endAngle - startAngle) / steps;
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        
        for (int i = 0; i <= steps; i++) {
            double angle = startAngle + i * angleStep;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            
            bufferBuilder.addVertex(matrix, cx + cos * innerR, cy + sin * innerR, 0).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, cx + cos * outerR, cy + sin * outerR, 0).setColor(r, g, b, a);
        }
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
    }
    
    private void renderOuterRing(GuiGraphics guiGraphics) {
        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        int steps = 64;
        float thickness = 3.0f;
        
        int r = (LINE_COLOR >> 16) & 0xFF;
        int g = (LINE_COLOR >> 8) & 0xFF;
        int b = LINE_COLOR & 0xFF;
        int a = (LINE_COLOR >> 24) & 0xFF;
        
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
        
        // 绘制边框
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
            
            float innerX = centerX + cosA * (innerRadius - thickness);
            float innerY = centerY + sinA * (innerRadius - thickness);
            float outerX = centerX + cosA * (innerRadius + thickness);
            float outerY = centerY + sinA * (innerRadius + thickness);
            
            bufferBuilder.addVertex(matrix, innerX, innerY, 0).setColor(borderR, borderG, borderB, borderA);
            bufferBuilder.addVertex(matrix, outerX, outerY, 0).setColor(borderR, borderG, borderB, borderA);
        }
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        RenderSystem.disableBlend();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && selectedSlot >= 0 && selectedSlot < morphSlots.size()) {
            executeMorph(morphSlots.get(selectedSlot));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == triggerKeyCode) {
            // 松开按键时执行选中的表情
            if (selectedSlot >= 0 && selectedSlot < morphSlots.size()) {
                executeMorph(morphSlots.get(selectedSlot));
            }
            this.onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    
    private void executeMorph(MorphSlot slot) {
        logger.info("执行表情: {}", slot.displayName);
        
        // 获取当前玩家的模型
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // 从配置获取玩家选择的模型
        String playerName = mc.player.getName().getString();
        String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(playerName);
        
        // 如果是默认渲染，不处理
        if (selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME) || selectedModel.isEmpty()) {
            logger.warn("当前使用默认渲染，无法应用表情");
            return;
        }
        
        MMDModelManager.Model m = MMDModelManager.GetModel(selectedModel, playerName);
        if (m == null) {
            logger.warn("未找到玩家模型: {}", selectedModel);
            return;
        }
        
        long modelHandle = m.model.GetModelLong();
        NativeFunc nf = NativeFunc.GetInst();
        
        if ("__reset__".equals(slot.morphName)) {
            // 重置所有表情
            nf.ResetAllMorphs(modelHandle);
            logger.info("已重置所有表情");
        } else {
            // 应用 VPD 表情/姿势
            if (slot.filePath != null) {
                int result = nf.ApplyVpdMorph(modelHandle, slot.filePath);
                if (result >= 0) {
                    // 解码返回值: 高16位为骨骼数，低16位为 Morph 数
                    int boneCount = (result >> 16) & 0xFFFF;
                    int morphCount = result & 0xFFFF;
                    logger.info("应用表情成功: {}, 匹配 {} 个 Morph, {} 个 Bone", slot.filePath, morphCount, boneCount);
                } else if (result == -1) {
                    logger.error("VPD 文件加载失败: {}", slot.filePath);
                } else if (result == -2) {
                    logger.error("模型不存在, handle={}", modelHandle);
                }
            }
        }
        
        // 发送网络同步（如果需要）
        MorphWheelNetworkHandler.sendMorphToServer(slot.morphName);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
