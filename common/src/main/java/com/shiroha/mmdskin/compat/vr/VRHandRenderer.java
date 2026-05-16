/* 文件职责：VR 第一人称手持物品渲染入口，使用 1.21.11 ItemStackRenderState 提交管线。 */
package com.shiroha.mmdskin.compat.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class VRHandRenderer {

    private static final Logger LOGGER = LogManager.getLogger();

    private VRHandRenderer() {}

    public static void renderHandItem(PoseStack poseStack, SubmitNodeCollector collector,
                                       int packedLight, InteractionHand hand) {
        if (collector == null) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return;

            ItemStack itemStack = player.getItemInHand(hand);
            if (itemStack.isEmpty()) return;

            boolean isMainHand = (hand == InteractionHand.MAIN_HAND);
            ItemDisplayContext ctx = isMainHand
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

            ItemModelResolver resolver = mc.getItemModelResolver();
            Level level = player.level();
            ItemStackRenderState state = new ItemStackRenderState();
            resolver.updateForTopItem(state, itemStack, ctx, level, player, player.getId() + ctx.ordinal());

            poseStack.pushPose();
            state.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        } catch (Exception e) {
            LOGGER.debug("VR 手持物品渲染异常", e);
        }
    }
}
