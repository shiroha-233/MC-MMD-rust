/* 文件职责：玩家手部物品渲染辅助，对接 1.21.11 ItemStackRenderState 提交管线。 */
package com.shiroha.mmdskin.renderer.integration.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridge;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridgeHolder;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager.Model;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ItemRenderHelper {

    private static final float DEG_TO_RAD = (float) Math.PI / 180F;
    private static final float DEFAULT_HELD_ITEM_SCALE = 1.0f;

    public static void renderItems(AbstractClientPlayer player, Model model,
                                   PoseStack matrixStack, SubmitNodeCollector collector, int packedLight) {
        if (collector == null) {
            return;
        }
        float heldItemScale = resolveHeldItemScale(model);
        renderHandItem(player, model, matrixStack, collector, packedLight, InteractionHand.MAIN_HAND, heldItemScale);
        renderHandItem(player, model, matrixStack, collector, packedLight, InteractionHand.OFF_HAND, heldItemScale);
    }

    private static void renderHandItem(AbstractClientPlayer player, Model model,
                                         PoseStack matrixStack, SubmitNodeCollector collector,
                                         int packedLight, InteractionHand hand, float heldItemScale) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.isEmpty()) {
            return;
        }

        boolean isMainHand = (hand == InteractionHand.MAIN_HAND);
        ModelRuntimeBridge runtimeBridge = ModelRuntimeBridgeHolder.get();
        long modelHandle = model.model.getModelHandle();
        long handMat = isMainHand ? model.entityData.rightHandMat : model.entityData.leftHandMat;

        runtimeBridge.populateHandMatrix(modelHandle, handMat, isMainHand);

        matrixStack.pushPose();
        matrixStack.last().pose().mul(convertToMatrix4f(runtimeBridge, handMat, model.entityData.matBuffer));

        matrixStack.mulPose(new Quaternionf().rotateX(90.0f * DEG_TO_RAD));
        matrixStack.mulPose(new Quaternionf().rotateY(180.0f * DEG_TO_RAD));

        applyConfiguredRotation(matrixStack, player, model, hand);

        float itemScale = resolveItemScale(heldItemScale);
        matrixStack.scale(10.0f * itemScale, 10.0f * itemScale, 10.0f * itemScale);

        ItemDisplayContext displayCtx = isMainHand
                ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        Minecraft mc = Minecraft.getInstance();
        ItemModelResolver resolver = mc.getItemModelResolver();
        Level level = player.level();
        ItemStackRenderState state = new ItemStackRenderState();
        resolver.updateForTopItem(state, itemStack, displayCtx, level, player, player.getId() + displayCtx.ordinal());
        state.submit(matrixStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);

        matrixStack.popPose();
    }

    private static void applyConfiguredRotation(PoseStack matrixStack, AbstractClientPlayer player,
                                                 Model model, InteractionHand hand) {
        for (String axis : new String[]{"x", "y", "z"}) {
            float rotation = getItemRotation(player, model, hand, axis);
            if (rotation != 0.0f) {
                Quaternionf q = new Quaternionf();
                switch (axis) {
                    case "x" -> q.rotateX(rotation * DEG_TO_RAD);
                    case "y" -> q.rotateY(rotation * DEG_TO_RAD);
                    case "z" -> q.rotateZ(rotation * DEG_TO_RAD);
                }
                matrixStack.mulPose(q);
            }
        }
    }

    private static float getItemRotation(AbstractClientPlayer player, Model model,
                                        InteractionHand hand, String axis) {
        String itemId = getItemId(player, hand);
        String handStr = (hand == InteractionHand.MAIN_HAND) ? "Right" : "Left";
        String handState = getHandState(player, hand);

        String specificKey = itemId + "_" + handStr + "_" + handState + "_" + axis;
        String specificValue = model.properties.getProperty(specificKey);
        if (specificValue != null) {
            return parseFloatSafe(specificValue, 0.0f);
        }

        String defaultKey = "default_" + axis;
        String defaultValue = model.properties.getProperty(defaultKey);
        if (defaultValue != null) {
            return parseFloatSafe(defaultValue, 0.0f);
        }

        return 0.0f;
    }

    private static float parseFloatSafe(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static float resolveHeldItemScale(Model model) {
        if (model.getModelName() != null && !model.getModelName().isBlank()) {
            ModelConfigData modelConfig = ModelConfigManager.getLiveConfig(model.getModelName());
            if (Float.isFinite(modelConfig.heldItemScale) && modelConfig.heldItemScale > 0.0f) {
                return modelConfig.heldItemScale;
            }
        }
        String[] keys = {"heldItemScale", "firstPersonHeldBlockScale", "held_item_scale"};
        for (String key : keys) {
            String value = model.properties.getProperty(key);
            if (value != null) {
                return parseFloatSafe(value, DEFAULT_HELD_ITEM_SCALE);
            }
        }
        return DEFAULT_HELD_ITEM_SCALE;
    }

    private static float resolveItemScale(float heldItemScale) {
        return Float.isFinite(heldItemScale) && heldItemScale > 0.0f
                ? heldItemScale
                : DEFAULT_HELD_ITEM_SCALE;
    }

    private static String getHandState(AbstractClientPlayer player, InteractionHand hand) {
        if (hand == player.getUsedItemHand() && player.isUsingItem()) {
            return "using";
        } else if (hand == player.swingingArm && player.swinging) {
            return "swinging";
        }
        return "idle";
    }

    private static String getItemId(AbstractClientPlayer player, InteractionHand hand) {
        String descriptionId = player.getItemInHand(hand).getItem().getDescriptionId();
        return descriptionId.substring(descriptionId.indexOf(".") + 1);
    }

    private static Matrix4f convertToMatrix4f(ModelRuntimeBridge runtimeBridge, long matId, ByteBuffer buf) {
        buf.clear();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        if (!runtimeBridge.copyMatrixToBuffer(matId, buf)) {
            return new Matrix4f();
        }
        buf.position(0);
        Matrix4f result = new Matrix4f(
            buf.getFloat(0),  buf.getFloat(16), buf.getFloat(32), buf.getFloat(48),
            buf.getFloat(4),  buf.getFloat(20), buf.getFloat(36), buf.getFloat(52),
            buf.getFloat(8),  buf.getFloat(24), buf.getFloat(40), buf.getFloat(56),
            buf.getFloat(12), buf.getFloat(28), buf.getFloat(44), buf.getFloat(60)
        );
        result.transpose();
        return result;
    }
}
