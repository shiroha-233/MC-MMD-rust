/** 文件职责：渲染玩家MMD模型的左上角正视面HUD，始终保持面向镜头。 */
package com.shiroha.mmdskin.debug.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

public class PlayerFrontViewHud {
    private static final int HUD_SIZE = 120;
    private static final int PADDING = 10;
    private static final float MODEL_SCALE = 0.8f;

    public static void render(GuiGraphics guiGraphics, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        
        // 检查配置是否启用
        if (!ConfigManager.isPlayerFrontViewEnabled()) {
            return;
        }
        
        // 检查是否有本地玩家
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        
        // 检查是否隐藏GUI或显示调试屏幕
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        
        // 获取玩家当前使用的MMD模型
        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        if (modelName == null || modelName.isEmpty()) {
            return;
        }
        
        MMDModelManager.Model modelData = MMDModelManager.GetModel(modelName, player.getUUID().toString());
        if (modelData == null) {
            return;
        }
        
        // 获取配置参数
        float scale = ConfigManager.getPlayerFrontViewScale();
        int offsetX = ConfigManager.getPlayerFrontViewOffsetX();
        int offsetY = ConfigManager.getPlayerFrontViewOffsetY();
        
        // 计算HUD位置（左上角）
        int hudX = offsetX;
        int hudY = offsetY;
        int hudWidth = (int)(HUD_SIZE * scale);
        int hudHeight = (int)(HUD_SIZE * scale);
        
        // 不再绘制黑色背景和文字
        
        // 渲染3D模型
        renderModel(guiGraphics, modelData, player, tickDelta, 
            hudX + hudWidth / 2, 
            hudY + hudHeight / 2, // 居中显示
            hudWidth * MODEL_SCALE);
    }
    
    private static void renderModel(GuiGraphics guiGraphics, MMDModelManager.Model modelData, 
                                   LocalPlayer player, float tickDelta, 
                                   int centerX, int centerY, float size) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        try {
            // 移动到HUD中心
            poseStack.translate(centerX, centerY, 100.0f);
            
            // 缩放模型
            poseStack.scale(size, size, -size);
            
            // 关键修正：修复头脚颠倒问题
            // 在Minecraft的坐标系统中，需要绕Z轴旋转180度来校正方向
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f));
            
            // 关键：强制模型面向镜头（不受玩家实际朝向影响）
            // 参考Ayame-PaperDoll的RotationMode.LOCK实现（第248-255行）
            // 必须临时修改player实体的yBodyRot和yHeadRot属性，否则底层渲染会从实体读取
            
            // 保存玩家原始的旋转角度
            float originalYBodyRot = player.yBodyRot;
            float originalYBodyRotO = player.yBodyRotO;
            float originalYHeadRot = player.yHeadRot;
            float originalYHeadRotO = player.yHeadRotO;
            float originalXRot = player.getXRot();
            float originalXRotO = player.xRotO;
            
            try {
                // 锁定玩家旋转角度，使模型始终面向镜头
                // 这与Ayame-PaperDoll的RotationMode.LOCK完全一致
                player.yBodyRot = player.yBodyRotO = 180.0f;
                player.yHeadRot = player.yHeadRotO = 180.0f;
                player.setXRot(0.0f);  // 俯仰角设为0
                
                // 保存当前的光照设置
                Lighting.setupForEntityInInventory();
                
                // 获取渲染系统
                MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                
                // 计算固定的实体yaw（始终为180度，使模型面向镜头）
                float fixedYaw = 180.0f;
                float fixedPitch = 0.0f;
                Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);
                
                // 关键修复：临时禁用第一人称模式，确保显示完整的头部模型
                // 第一人称模式下头部会被隐藏（防止遮挡视野），但HUD预览需要显示完整模型
                long modelHandle = modelData.model.getModelHandle();
                boolean wasFirstPersonEnabled = false;
                if (modelHandle != 0) {
                    wasFirstPersonEnabled = com.shiroha.mmdskin.player.runtime.FirstPersonManager.isActive();
                    if (wasFirstPersonEnabled) {
                        // 临时禁用第一人称模式，让头部显示出来
                        com.shiroha.mmdskin.NativeFunc.GetInst().SetFirstPersonMode(modelHandle, false);
                    }
                }
                
                try {
                    // 渲染模型 - 现在player实体的旋转已被锁定，模型将始终面向镜头
                    modelData.model.render(
                        player,
                        fixedYaw,      // 使用固定yaw
                        fixedPitch,    // 俯仰角设为0
                        translation,   // 无偏移
                        tickDelta,
                        poseStack,
                        0xF000F0,      // 最大亮度
                        RenderContext.WORLD  // 使用WORLD上下文
                    );
                } finally {
                    // 恢复第一人称模式状态
                    if (modelHandle != 0 && wasFirstPersonEnabled) {
                        com.shiroha.mmdskin.NativeFunc.GetInst().SetFirstPersonMode(modelHandle, true);
                    }
                }
                
                // 结束批处理
                bufferSource.endBatch();
                
            } finally {
                // 关键：恢复玩家原始的旋转角度
                // 这确保不会影响实际游戏中的玩家朝向
                player.yBodyRot = originalYBodyRot;
                player.yBodyRotO = originalYBodyRotO;
                player.yHeadRot = originalYHeadRot;
                player.yHeadRotO = originalYHeadRotO;
                player.setXRot(originalXRot);
                player.xRotO = originalXRotO;
            }
            
        } catch (Exception e) {
            MmdSkinClient.logger.error("Failed to render player front view model", e);
        } finally {
            poseStack.popPose();
            // 恢复光照设置
            Lighting.setupFor3DItems();
        }
    }
}