// 负责在接管玩家主体时立即提交双手物品。
// 26.2 适配：MultiBufferSource 移除，改由 SubmitNodeCollector 提交；
// 手持物渲染状态不再挂在 PlayerRenderState 上，改为从实体直接取物品栈，
// 通过 ItemInHandRenderer#renderItem 按 MMD 手骨变换绘制。
package com.shiroha.mmdskin.client.draw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.bridge.runtime.NativeModelMatrixPort;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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

    public static void render(MmdModelInstance model, PoseStack poseStack, SubmitNodeCollector collector,
                              LivingEntity entity, int packedLight, float bodyYawDegrees) {
        poseStack.pushPose();
        try {
            // 手持物需要与主模型一致的实体朝向（主模型在 MmdRenderRouter 中 rotateY(-bodyYaw)），
            // 否则转身时物品不跟随。
            float yawRadians = (float) Math.toRadians(-bodyYawDegrees);
            poseStack.last().pose().rotateY(yawRadians);
            poseStack.last().normal().rotateY(yawRadians);
            float renderScale = model.renderScale();
            poseStack.scale(renderScale, renderScale, renderScale);
            renderHand(model, entity, entity.getMainHandItem(), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    NativeModelMatrixPort.Hand.RIGHT, poseStack, collector, packedLight);
            renderHand(model, entity, entity.getOffhandItem(), ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    NativeModelMatrixPort.Hand.LEFT, poseStack, collector, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderHand(MmdModelInstance model, LivingEntity entity, ItemStack stack,
                                   ItemDisplayContext displayContext, NativeModelMatrixPort.Hand hand,
                                   PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        if (stack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        try {
            var handTransform = model.updateHandTransform(hand);
            if (handTransform == null) {
                return;
            }
            poseStack.mulPose(handTransform);
            poseStack.mulPose(ITEM_ORIENTATION);
            float itemScale = 10.0F * model.heldItemScale();
            poseStack.scale(itemScale, itemScale, itemScale);
            Minecraft.getInstance().gameRenderer.itemInHandRenderer
                    .renderItem(entity, stack, displayContext, poseStack, collector, packedLight);
        } catch (RuntimeException exception) {
            LOGGER.warn("MMD 手持物矩阵提交失败: {} {}", model.modelName(), hand, exception);
        } finally {
            poseStack.popPose();
        }
    }
}
