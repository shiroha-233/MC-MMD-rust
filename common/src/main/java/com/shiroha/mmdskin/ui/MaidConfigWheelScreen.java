package com.shiroha.mmdskin.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.shiroha.mmdskin.maid.MaidActionWheelScreen;
import com.shiroha.mmdskin.maid.MaidModelSelectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 女仆配置轮盘界面
 * 对着女仆按住 B 打开，松开关闭
 * 提供模型切换/动作选择/材质控制三个入口
 */
public class MaidConfigWheelScreen extends Screen {
    
    // 轮盘参数
    private static final float WHEEL_SCREEN_RATIO = 0.45f;
    private static final float INNER_RATIO = 0.35f;
    private static final int LINE_COLOR = 0xFFD060A0;
    private static final int LINE_COLOR_DIM = 0xCCD060A0;
    private static final int HIGHLIGHT_COLOR = 0x60FFFFFF;
    private static final int CENTER_BG = 0xE0301828;
    private static final int CENTER_BORDER = 0xFFD060A0;
    private static final int TEXT_SHADOW = 0xFF000000;
    
    private final List<ConfigSlot> configSlots;
    private int selectedSlot = -1;
    private int centerX, centerY;
    private int outerRadius, innerRadius;
    
    // 女仆信息
    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;
    
    // 监控的按键
    private final int monitoredKey;
    
    public MaidConfigWheelScreen(UUID maidUUID, int maidEntityId, String maidName, int keyCode) {
        super(Component.translatable("gui.mmdskin.maid_config_wheel"));
        this.maidUUID = maidUUID;
        this.maidEntityId = maidEntityId;
        this.maidName = maidName;
        this.monitoredKey = keyCode;
        this.configSlots = new ArrayList<>();
        initConfigSlots();
    }
    
    private void initConfigSlots() {
        // 三个配置入口
        configSlots.add(new ConfigSlot("model", 
            Component.translatable("gui.mmdskin.maid.model_switch").getString(),
            "🎭", this::openMaidModelSelector));
        configSlots.add(new ConfigSlot("action", 
            Component.translatable("gui.mmdskin.maid.action_select").getString(),
            "🎬", this::openMaidActionWheel));
        configSlots.add(new ConfigSlot("material", 
            Component.translatable("gui.mmdskin.maid.material_control").getString(),
            "👕", this::openMaidMaterialVisibility));
    }

    @Override
    protected void init() {
        super.init();
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        
        int minDimension = Math.min(this.width, this.height);
        this.outerRadius = (int) (minDimension * WHEEL_SCREEN_RATIO / 2);
        this.innerRadius = (int) (this.outerRadius * INNER_RATIO);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateSelectedSlot(mouseX, mouseY);
        renderWheelSegments(guiGraphics);
        renderDividerLines(guiGraphics);
        renderOuterRing(guiGraphics);
        renderCenterCircle(guiGraphics);
        renderSlotLabels(guiGraphics);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void tick() {
        super.tick();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (!isKeyDown(window, monitoredKey)) {
            if (selectedSlot >= 0 && selectedSlot < configSlots.size()) {
                ConfigSlot slot = configSlots.get(selectedSlot);
                this.onClose();
                slot.action.run();
            } else {
                this.onClose();
            }
        }
    }
    
    private boolean isKeyDown(long window, int keyCode) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, keyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
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
        
        double segmentAngle = 360.0 / configSlots.size();
        selectedSlot = (int) (angle / segmentAngle) % configSlots.size();
    }

    private void renderWheelSegments(GuiGraphics guiGraphics) {
        if (selectedSlot < 0) return;
        
        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        double segmentAngle = 360.0 / configSlots.size();
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
            
            float iX = centerX + cosA * innerRadius;
            float iY = centerY + sinA * innerRadius;
            bufferBuilder.addVertex(matrix, iX, iY, 0).setColor(r, g, b, a / 2);
            
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
        
        double segmentAngle = 360.0 / configSlots.size();
        
        for (int i = 0; i < configSlots.size(); i++) {
            double angle = Math.toRadians(i * segmentAngle - 90);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            
            float iX = centerX + cosA * innerRadius;
            float iY = centerY + sinA * innerRadius;
            float oX = centerX + cosA * outerRadius;
            float oY = centerY + sinA * outerRadius;
            
            int lineColor = (i == selectedSlot || i == (selectedSlot + 1) % configSlots.size()) 
                ? LINE_COLOR : LINE_COLOR_DIM;
            
            drawThickLine(matrix, iX, iY, oX, oY, 3.0f, lineColor);
        }
        
        RenderSystem.disableBlend();
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
    
    private void renderOuterRing(GuiGraphics guiGraphics) {
        PoseStack poseStack = guiGraphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
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
        
        // 边框
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
        
        // 中心显示女仆名称
        String displayText = selectedSlot >= 0 ? configSlots.get(selectedSlot).name : maidName;
        int textWidth = this.font.width(displayText);
        guiGraphics.drawString(this.font, displayText, centerX - textWidth / 2 + 1, centerY - 3, TEXT_SHADOW, false);
        guiGraphics.drawString(this.font, displayText, centerX - textWidth / 2, centerY - 4, 0xFFD060A0, false);
    }

    private void renderSlotLabels(GuiGraphics guiGraphics) {
        double segmentAngle = 360.0 / configSlots.size();
        
        for (int i = 0; i < configSlots.size(); i++) {
            ConfigSlot slot = configSlots.get(i);
            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2 - 90);
            
            int textRadius = (innerRadius + outerRadius) / 2;
            int textX = centerX + (int) (Math.cos(angle) * textRadius);
            int textY = centerY + (int) (Math.sin(angle) * textRadius);
            
            int iconWidth = this.font.width(slot.icon);
            boolean isSelected = (i == selectedSlot);
            int iconColor = isSelected ? 0xFFFFFFFF : 0xFFCCDDEE;
            
            guiGraphics.drawString(this.font, slot.icon, textX - iconWidth / 2 + 1, textY - 11, TEXT_SHADOW, false);
            guiGraphics.drawString(this.font, slot.icon, textX - iconWidth / 2, textY - 12, iconColor, false);
            
            int nameWidth = this.font.width(slot.name);
            guiGraphics.drawString(this.font, slot.name, textX - nameWidth / 2 + 1, textY + 3, TEXT_SHADOW, false);
            guiGraphics.drawString(this.font, slot.name, textX - nameWidth / 2, textY + 2, iconColor, false);
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
    
    // 女仆配置操作
    private void openMaidModelSelector() {
        Minecraft.getInstance().setScreen(new MaidModelSelectorScreen(maidUUID, maidEntityId, maidName));
    }
    
    private void openMaidActionWheel() {
        Minecraft.getInstance().setScreen(new MaidActionWheelScreen(maidUUID, maidEntityId, maidName));
    }
    
    private void openMaidMaterialVisibility() {
        MaterialVisibilityScreen screen = MaterialVisibilityScreen.createForMaid(maidUUID, maidName);
        if (screen != null) {
            Minecraft.getInstance().setScreen(screen);
        } else {
            Minecraft.getInstance().gui.getChat().addMessage(
                Component.literal("§c未找到女仆模型，请先为女仆选择一个MMD模型"));
        }
    }

    @SuppressWarnings("unused")
    private static class ConfigSlot {
        final String id;
        final String name;
        final String icon;
        final Runnable action;

        ConfigSlot(String id, String name, String icon, Runnable action) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.action = action;
        }
    }
}
