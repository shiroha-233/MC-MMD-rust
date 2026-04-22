package com.shiroha.mmdskin.player.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

/** 文件职责：作为平台玩家渲染 mixin 的统一入口。 */
public final class PlayerRenderEntrypoint {
    private PlayerRenderEntrypoint() {
    }

    public static PlayerRenderAction handleRender(
            AbstractClientPlayer player,
            float entityYaw,
            float tickDelta,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            boolean isYsmActive) {
        return PlayerMixinDelegate.handleRender(player, entityYaw, tickDelta, poseStack, buffers, packedLight, isYsmActive);
    }

    public static void renderSceneOverlay(
            AbstractClientPlayer player,
            float tickDelta,
            PoseStack poseStack,
            int packedLight) {
        PlayerMixinDelegate.renderSceneModel(player, tickDelta, poseStack, packedLight);
    }
}
