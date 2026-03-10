package com.shiroha.mmdskin.renderer.integration.player;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.compat.vr.VRBoneDriver;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.shiroha.mmdskin.renderer.integration.ModelPropertyHelper;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridge;
import com.shiroha.mmdskin.renderer.runtime.bridge.ModelRuntimeBridgeHolder;
import com.shiroha.mmdskin.renderer.runtime.model.AbstractMMDModel;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

final class PlayerModelRenderCoordinator {

    private PlayerModelRenderCoordinator() {
    }

    static PlayerMixinDelegate.RenderAction render(PlayerRenderSelection selection,
                                                   AbstractClientPlayer player,
                                                   float entityYaw,
                                                   float tickDelta,
                                                   PoseStack matrixStack,
                                                   MultiBufferSource vertexConsumers,
                                                   int packedLight,
                                                   MMDModelManager.Model modelData) {
        IMMDModel model = modelData.model;
        modelData.loadModelProperties(false);

        float[] size = ModelPropertyHelper.getModelSize(modelData.properties);
        boolean isVr = selection.isLocalPlayer() && VRArmHider.isLocalPlayerInVR();
        syncVrState(model, player, tickDelta, isVr);

        ModelRuntimeBridge runtimeBridge = ModelRuntimeBridgeHolder.get();
        float combinedScale = size[0] * ModelConfigManager.getConfig(selection.selectedModel()).modelScale;
        if (!isVr) {
            runtimeBridge.preRenderFirstPerson(model.getModelHandle(), combinedScale, selection.isLocalPlayer());
        }
        boolean isFirstPerson = !isVr && selection.isLocalPlayer() && FirstPersonManager.isActive();

        AnimationStateManager.updateAnimationState(player, modelData);
        consumePendingSignals(player, modelData, selection.isLocalPlayer());

        RenderParams params = PlayerRenderHelper.calculateRenderParams(player, modelData, tickDelta);
        boolean needsFirstPersonCleanup = isFirstPerson;

        matrixStack.pushPose();
        try {
            if (InventoryRenderHelper.isInventoryScreen()) {
                InventoryRenderHelper.renderInInventory(player, model, entityYaw, tickDelta, matrixStack, packedLight, size);
            } else {
                matrixStack.scale(size[0], size[0], size[0]);
                RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
                RenderContext context = isFirstPerson ? RenderContext.FIRST_PERSON : RenderContext.WORLD;
                model.render(player, params.bodyYaw, params.bodyPitch, params.translation, tickDelta, matrixStack, packedLight, context);
            }

            if (needsFirstPersonCleanup) {
                runtimeBridge.postRenderFirstPerson(model.getModelHandle());
                needsFirstPersonCleanup = false;
            }

            ItemRenderHelper.renderItems(player, modelData, matrixStack, vertexConsumers, packedLight);
            return PlayerMixinDelegate.RenderAction.CANCEL;
        } finally {
            try {
                if (needsFirstPersonCleanup) {
                    runtimeBridge.postRenderFirstPerson(model.getModelHandle());
                }
            } finally {
                matrixStack.popPose();
            }
        }
    }

    private static void syncVrState(IMMDModel model, AbstractClientPlayer player, float tickDelta, boolean isVr) {
        if (!(model instanceof AbstractMMDModel abstractModel)) {
            return;
        }

        if (isVr) {
            if (!abstractModel.isVrActive()) {
                VRBoneDriver.setVREnabled(model.getModelHandle(), true);
                abstractModel.setVrActive(true);
            }
            VRBoneDriver.driveModel(model.getModelHandle(), player, tickDelta);
            return;
        }

        if (abstractModel.isVrActive()) {
            VRBoneDriver.setVREnabled(model.getModelHandle(), false);
            abstractModel.setVrActive(false);
        }
    }

    private static void consumePendingSignals(AbstractClientPlayer player,
                                              MMDModelManager.Model modelData,
                                              boolean isLocalPlayer) {
        if (isLocalPlayer) {
            return;
        }

        PendingAnimSignalCache.SignalType signal = PendingAnimSignalCache.consume(player.getUUID());
        if (signal == PendingAnimSignalCache.SignalType.RESET) {
            MmdSkinRendererPlayerHelper.ResetPhysics(player);
        }
    }
}
