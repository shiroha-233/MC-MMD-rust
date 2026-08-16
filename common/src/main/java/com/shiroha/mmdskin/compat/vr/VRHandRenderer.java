// 负责在 VR 控制器姿态下提交当前手持物品。
package com.shiroha.mmdskin.compat.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * VR 手持物品渲染器（SRP：在控制器位置渲染手持物品）
 */

public final class VRHandRenderer {

    private static final Logger LOGGER = LogManager.getLogger();

    private VRHandRenderer() {}

    public static void renderHandItem(PoseStack poseStack, SubmitNodeCollector collector,
                                       int packedLight, InteractionHand hand) {
        try {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;

            var itemStack = player.getItemInHand(hand);
            if (itemStack.isEmpty()) return;

            boolean isMainHand = (hand == InteractionHand.MAIN_HAND);
            ItemDisplayContext ctx = isMainHand
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

            poseStack.pushPose();
            try {
                Minecraft.getInstance().gameRenderer.itemInHandRenderer.renderItem(
                        player, itemStack, ctx, poseStack, collector, packedLight);
            } finally {
                poseStack.popPose();
            }
        } catch (Exception e) {
            LOGGER.debug("VR 手持物品渲染异常", e);
        }
    }
}
