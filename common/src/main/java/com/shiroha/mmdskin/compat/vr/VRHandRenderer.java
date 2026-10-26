// 负责在 VR 控制器姿态下提交当前手持物品。
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * VR 手持物品渲染器（SRP：在控制器位置渲染手持物品）
 */

public final class VRHandRenderer {

    private static final Logger LOGGER = LogManager.getLogger();

    private VRHandRenderer() {}

    public static void renderHandItem(PoseStack poseStack, SubmitNodeCollector buffer,
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
                // 26.3：Minecraft.getItemRenderer().renderStatic(...) 已整体移除，
                // 物品渲染改成"渲染状态 + 提交"两步：
                //   ItemModelResolver 把 ItemStack 填进 ItemStackRenderState，
                //   再由 ItemStackRenderState.submit(...) 交给 SubmitNodeCollector。
                ItemStackRenderState itemState = new ItemStackRenderState();
                ItemModelResolver resolver = new ItemModelResolver(
                        Minecraft.getInstance().getModelManager());
                resolver.updateForLiving(itemState, itemStack, ctx, player);
                itemState.submit(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, 0);
            } finally {
                poseStack.popPose();
            }
        } catch (Exception e) {
            LOGGER.debug("VR 手持物品渲染异常", e);
        }
    }
}
