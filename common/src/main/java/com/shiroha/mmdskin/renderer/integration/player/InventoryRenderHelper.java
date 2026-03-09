package com.shiroha.mmdskin.renderer.integration.player;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 库存屏幕渲染辅助类。
 */
public class InventoryRenderHelper {

    public static boolean isInventoryScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;
        String className = mc.screen.getClass().getName();
        return className.contains("InventoryScreen") || className.contains("class_490");
    }

    public static void renderInInventory(AbstractClientPlayer player, IMMDModel model, float entityYaw,
                                        float tickDelta, PoseStack matrixStack, int packedLight, float[] size) {
        Minecraft mc = Minecraft.getInstance();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();

        int posX, posY;
        if (mc.gameMode.getPlayerMode() != GameType.CREATIVE && mc.screen instanceof InventoryScreen) {
            InventoryScreen invScreen = (InventoryScreen) mc.screen;
            posX = invScreen.getRecipeBookComponent().updateScreenPosition(mc.screen.width, 176);
            posY = (mc.screen.height - 166) / 2;
            modelViewStack.translate(posX + 51, posY + 75, 50);
            modelViewStack.scale(1.5f, 1.5f, 1.5f);
        } else {
            posX = (mc.screen.width - 121) / 2;
            posY = (mc.screen.height - 195) / 2;
            modelViewStack.translate(posX + 51, posY + 75, 50.0);
        }

        float inventorySize = size[1];
        modelViewStack.scale(inventorySize, inventorySize, inventorySize);
        modelViewStack.scale(20.0f, 20.0f, -20.0f);

        Quaternionf rotation = calculateRotation(player);
        modelViewStack.mulPose(rotation);

        RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
        model.render(player, entityYaw, 0.0f, new Vector3f(0.0f), tickDelta, modelViewStack, packedLight, RenderContext.INVENTORY);

        modelViewStack.popPose();

        Quaternionf bodyRotation = new Quaternionf().rotateY(-player.yBodyRot * ((float)Math.PI / 180F));
        matrixStack.mulPose(bodyRotation);
        matrixStack.scale(inventorySize, inventorySize, inventorySize);
        matrixStack.scale(0.09f, 0.09f, 0.09f);
    }

    private static Quaternionf calculateRotation(AbstractClientPlayer player) {
        Quaternionf quaternion = new Quaternionf().rotateZ((float)Math.PI);
        Quaternionf pitch = new Quaternionf().rotateX(-player.getXRot() * ((float)Math.PI / 180F));
        Quaternionf yaw = new Quaternionf().rotateY(-player.yBodyRot * ((float)Math.PI / 180F));

        quaternion.mul(pitch);
        quaternion.mul(yaw);

        return quaternion;
    }

}
