package com.shiroha.mmdskin.renderer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 库存屏幕渲染辅助类
 * 负责在库存界面中渲染 3D 模型
 */
public class InventoryRenderHelper {
    
    private static Boolean isInventoryScreen = null;
    private static long lastCheckTime = 0;
    private static final long CACHE_DURATION = 100;
    
    /**
     * 检查当前是否在库存屏幕（带缓存）
     */
    public static boolean isInventoryScreen() {
        long currentTime = System.currentTimeMillis();
        
        if (isInventoryScreen != null && (currentTime - lastCheckTime) < CACHE_DURATION) {
            return isInventoryScreen;
        }
        
        isInventoryScreen = checkInventoryScreen();
        lastCheckTime = currentTime;
        return isInventoryScreen;
    }
    
    private static boolean checkInventoryScreen() {
        StackTraceElement[] steArray = Thread.currentThread().getStackTrace();
        if (steArray.length <= 6) {
            return false;
        }
        String className = steArray[6].getClassName();
        return className.contains("InventoryScreen") || className.contains("class_490");
    }
    
    /**
     * 在库存屏幕中渲染模型
     * MC 1.21.1: 使用传入的 PoseStack 而非 RenderSystem.getModelViewStack()
     */
    public static void renderInInventory(AbstractClientPlayer player, IMMDModel model, float entityYaw, 
                                        float tickDelta, PoseStack matrixStack, int packedLight, float[] size) {
        matrixStack.pushPose();
        
        float inventorySize = size[1];
        matrixStack.scale(inventorySize, inventorySize, inventorySize);
        matrixStack.scale(20.0f, 20.0f, -20.0f);
        
        Quaternionf rotation = calculateRotation(player);
        matrixStack.mulPose(rotation);
        
        RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
        model.render(player, entityYaw, 0.0f, new Vector3f(0.0f), tickDelta, matrixStack, packedLight, RenderContext.INVENTORY);
        
        matrixStack.popPose();
    }
    
    private static Quaternionf calculateRotation(AbstractClientPlayer player) {
        Quaternionf quaternion = new Quaternionf().rotateZ((float)Math.PI);
        Quaternionf pitch = new Quaternionf().rotateX(-player.getXRot() * ((float)Math.PI / 180F));
        Quaternionf yaw = new Quaternionf().rotateY(-player.yBodyRot * ((float)Math.PI / 180F));
        
        quaternion.mul(pitch);
        quaternion.mul(yaw);
        
        return quaternion;
    }
    
    public static void clearCache() {
        isInventoryScreen = null;
        lastCheckTime = 0;
    }
}
