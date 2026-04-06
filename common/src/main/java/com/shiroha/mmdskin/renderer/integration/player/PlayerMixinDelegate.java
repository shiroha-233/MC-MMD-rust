package com.shiroha.mmdskin.renderer.integration.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.scene.client.SceneModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * 玩家渲染 Mixin 委托。
 */
public final class PlayerMixinDelegate {

    private PlayerMixinDelegate() {}

    public static PlayerRenderAction handleRender(
            AbstractClientPlayer player, float entityYaw, float tickDelta,
            PoseStack matrixStack, MultiBufferSource vertexConsumers, int packedLight,
            boolean isYsmActive) {
        PlayerRenderSelection selection = PlayerRenderSelectionResolver.resolve(player, isYsmActive);
        if (selection.hasTerminalAction()) {
            renderSceneModelIfNeeded(selection, player, tickDelta, matrixStack, packedLight);
            return selection.terminalAction();
        }

        MMDModelManager.Model modelData = MMDModelManager.GetModel(selection.selectedModel(), selection.playerCacheKey());

        if (modelData == null) {
            if (MMDModelManager.isModelPending(selection.selectedModel(), selection.playerCacheKey())) {
                renderSceneModelIfNeeded(selection, player, tickDelta, matrixStack, packedLight);
                return PlayerRenderAction.CANCEL;
            }
            renderSceneModelIfNeeded(selection, player, tickDelta, matrixStack, packedLight);
            return PlayerRenderAction.SUPER_RENDER;
        }

        PlayerRenderAction action = PlayerModelRenderCoordinator.render(
                selection,
                player,
                entityYaw,
                tickDelta,
                matrixStack,
                vertexConsumers,
                packedLight,
                modelData);
        renderSceneModelIfNeeded(selection, player, tickDelta, matrixStack, packedLight);
        return action;
    }

    private static void renderSceneModelIfNeeded(PlayerRenderSelection selection,
                                                 AbstractClientPlayer player,
                                                 float tickDelta,
                                                 PoseStack matrixStack,
                                                 int packedLight) {
        if (selection.shouldSkipSceneModel()) {
            return;
        }

        renderSceneModel(player, tickDelta, matrixStack, packedLight);
    }

    private static void renderSceneModel(AbstractClientPlayer player, float tickDelta,
                                         PoseStack matrixStack, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(player.getUUID())) return;

        SceneModelManager sceneMgr = SceneModelManager.getInstance();
        if (!sceneMgr.isActive() && !sceneMgr.isLoading()) return;

        double renderX = Mth.lerp(tickDelta, player.xo, player.getX());
        double renderY = Mth.lerp(tickDelta, player.yo, player.getY());
        double renderZ = Mth.lerp(tickDelta, player.zo, player.getZ());
        sceneMgr.renderScene(matrixStack, tickDelta, packedLight, renderX, renderY, renderZ);
    }
}
