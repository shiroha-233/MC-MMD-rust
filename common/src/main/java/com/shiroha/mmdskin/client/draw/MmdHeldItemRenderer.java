// 负责在接管玩家主体时立即提交 RenderState 中的双手物品。
package com.shiroha.mmdskin.client.draw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.bridge.runtime.NativeModelMatrixPort;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;

public final class MmdHeldItemRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Quaternionf ITEM_ORIENTATION = new Quaternionf()
            .rotateX((float) Math.toRadians(90.0D))
            .rotateY((float) Math.toRadians(180.0D));

    private MmdHeldItemRenderer() {
    }

    public static void render(AvatarRenderState state, MmdModelInstance model, PoseStack poseStack,
                              SubmitNodeCollector buffers, int packedLight, float bodyYawDegrees) {
        poseStack.pushPose();
        try {
            float yawRadians = (float) Math.toRadians(-bodyYawDegrees);
            poseStack.last().pose().rotateY(yawRadians);
            poseStack.last().normal().rotateY(yawRadians);
            float renderScale = model.renderScale();
            poseStack.scale(renderScale, renderScale, renderScale);
            // 26.3 起手持物在实体状态里改名为 rightHandItemState / leftHandItemState
            // （类型仍是 ItemStackRenderState）。
            renderHand(state.rightHandItemState, model, NativeModelMatrixPort.Hand.RIGHT,
                    poseStack, buffers, packedLight);
            renderHand(state.leftHandItemState, model, NativeModelMatrixPort.Hand.LEFT,
                    poseStack, buffers, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderHand(ItemStackRenderState item, MmdModelInstance model,
                                   NativeModelMatrixPort.Hand hand, PoseStack poseStack,
                                   SubmitNodeCollector buffers, int packedLight) {
        if (item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        try {
            var handTransform = model.updateHandTransform(hand);
            if (handTransform == null) {
                return;
            }
            poseStack.mulPose(handTransform);
            // 26.3 的 PoseStack 用 rotate(Quaternionfc) 代替旧的 mulPose(Quaternionf)
            poseStack.rotate(ITEM_ORIENTATION);
            float itemScale = 10.0F * model.heldItemScale();
            poseStack.scale(itemScale, itemScale, itemScale);
            // 26.3：ItemStackRenderState.render(...) -> submit(pose, collector, light, overlay, outlineColor)
            item.submit(poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, 0);
        } catch (RuntimeException exception) {
            LOGGER.warn("MMD 手持物矩阵提交失败: {} {}", model.modelName(), hand, exception);
        } finally {
            poseStack.popPose();
        }
    }
}
