package com.shiroha.mmdskin.player.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.compat.vr.VRBoneDriver;
import com.shiroha.mmdskin.compat.vr.VivecraftReflectionBridge;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.shiroha.mmdskin.render.backend.BaseModelInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

final class PlayerModelRenderCoordinator {

    private PlayerModelRenderCoordinator() {
    }

    static PlayerRenderAction render(PlayerRenderSelection selection,
                                                    AbstractClientPlayer player,
                                                   float entityYaw,
                                                   float tickDelta,
                                                   PoseStack matrixStack,
                                                   MultiBufferSource vertexConsumers,
                                                   int packedLight,
                                                   ManagedModel modelData) {
        ModelInstance model = modelData.modelInstance();

        float[] size = PlayerRenderHelper.getModelSize(modelData);
        boolean isVr = selection.isLocalPlayer() && VRArmHider.isLocalPlayerInVR();
        syncVrState(modelData, player, tickDelta, isVr);

        float combinedScale = size[0] * ModelConfigManager.getConfig(selection.selectedModel()).modelScale;
        if (selection.isLocalPlayer()) {
            FirstPersonManager.preRender(model.getModelHandle(), combinedScale, true);
        }
        boolean isFirstPerson = !isVr && selection.isLocalPlayer() && FirstPersonManager.isActive();

        if (!isVr) {
            AnimationStateManager.updateAnimationState(player, modelData);
        }
        consumePendingSignals(player, modelData, selection.isLocalPlayer());

        MutableRenderPose params = PlayerRenderHelper.calculateMutableRenderPose(player, modelData, tickDelta);
        boolean needsPostRenderSync = selection.isLocalPlayer();

        matrixStack.pushPose();
        try {
            if (InventoryRenderHelper.isInventoryScreen()) {
                InventoryRenderHelper.renderInInventory(player, model, entityYaw, tickDelta, matrixStack, packedLight, size);
            } else {
                matrixStack.scale(size[0], size[0], size[0]);
                RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
                RenderScene context = isFirstPerson ? RenderScene.FIRST_PERSON : RenderScene.WORLD;
                model.render(player, params.bodyYaw, params.bodyPitch, params.translation, tickDelta, matrixStack, packedLight, context);
            }

            if (needsPostRenderSync) {
                FirstPersonManager.postRender(model.getModelHandle(), player, tickDelta);
                needsPostRenderSync = false;
            }

            ItemRenderHelper.renderItems(player, modelData, matrixStack, vertexConsumers, packedLight);
            return PlayerRenderAction.CANCEL;
        } finally {
            try {
                if (needsPostRenderSync) {
                    FirstPersonManager.postRender(model.getModelHandle(), player, tickDelta);
                }
            } finally {
                matrixStack.popPose();
            }
        }
    }

    private static void syncVrState(ManagedModel modelData,
                                    AbstractClientPlayer player,
                                    float tickDelta,
                                    boolean isVr) {
        ModelInstance model = modelData.modelInstance();
        if (!(model instanceof BaseModelInstance abstractModel)) {
            return;
        }

        if (isVr) {
            VivecraftReflectionBridge.applyMmdRenderState(true);
            if (!abstractModel.isVrActive()) {
                MmdSkinRendererPlayerHelper.suppressDefaultAnimationState(modelData);
                VRBoneDriver.setVREnabled(model.getModelHandle(), true);
                abstractModel.setVrActive(true);
            }
            VRBoneDriver.setVRIKParams(model.getModelHandle(), ConfigManager.getVRArmIKStrength());
            VRBoneDriver.driveModel(model.getModelHandle(), player, tickDelta);
            return;
        }

        VivecraftReflectionBridge.applyMmdRenderState(false);
        if (abstractModel.isVrActive()) {
            VRBoneDriver.setVREnabled(model.getModelHandle(), false);
            abstractModel.setVrActive(false);
            MmdSkinRendererPlayerHelper.resetModelAnimationState(player, modelData);
        }
    }

    private static void consumePendingSignals(AbstractClientPlayer player,
                                              ManagedModel modelData,
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
