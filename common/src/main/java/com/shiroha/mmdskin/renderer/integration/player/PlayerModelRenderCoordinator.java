package com.shiroha.mmdskin.renderer.integration.player;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.config.ConfigManager;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * 文件职责：协调本地第一人称与 VR 状态下的玩家模型渲染时序。
 */
final class PlayerModelRenderCoordinator {

    private PlayerModelRenderCoordinator() {
    }

    static PlayerMixinDelegate.RenderAction render(PlayerRenderSelection selection,
                                                   AbstractClientPlayer player,
                                                   float entityYaw,
                                                   float tickDelta,
                                                   PoseStack matrixStack,
                                                   SubmitNodeCollector collector,
                                                   int packedLight,
                                                   MMDModelManager.Model modelData) {
        IMMDModel model = modelData.model;
        modelData.loadModelProperties(false);

        float[] size = ModelPropertyHelper.getModelSize(modelData.properties);
        boolean isVr = selection.isLocalPlayer() && FirstPersonManager.vrRuntime().isLocalPlayerInVr();
        syncVrState(modelData, player, tickDelta, isVr);

        ModelRuntimeBridge runtimeBridge = ModelRuntimeBridgeHolder.get();
        float combinedScale = size[0] * ModelConfigManager.getLiveConfig(selection.selectedModel()).modelScale;
        runtimeBridge.preRenderFirstPerson(model.getModelHandle(), combinedScale, selection.isLocalPlayer());
        boolean isFirstPerson = !isVr && selection.isLocalPlayer() && FirstPersonManager.isActive();

        if (!isVr) {
            AnimationStateManager.updateAnimationState(player, modelData);
        }
        consumePendingSignals(player, modelData, selection.isLocalPlayer());

        RenderParams params = PlayerRenderHelper.calculateRenderParams(player, modelData, tickDelta);
        boolean needsLocalRenderSync = selection.isLocalPlayer();

        matrixStack.pushPose();
        try {
            if (InventoryRenderHelper.isInventoryScreen()) {
                InventoryRenderHelper.renderInInventory(player, model, entityYaw, tickDelta, matrixStack, packedLight, size);
            } else {
                matrixStack.scale(size[0], size[0], size[0]);
                RenderContext context = isFirstPerson ? RenderContext.FIRST_PERSON : RenderContext.WORLD;
                model.render(player, params.bodyYaw, params.bodyPitch, params.translation, tickDelta, matrixStack, packedLight, context);
            }

            if (needsLocalRenderSync) {
                runtimeBridge.postRenderFirstPerson(model.getModelHandle(), player, tickDelta);
                needsLocalRenderSync = false;
            }

            ItemRenderHelper.renderItems(player, modelData, matrixStack, collector, packedLight);
            return PlayerMixinDelegate.RenderAction.CANCEL;
        } finally {
            try {
                if (needsLocalRenderSync) {
                    runtimeBridge.postRenderFirstPerson(model.getModelHandle(), player, tickDelta);
                }
            } finally {
                matrixStack.popPose();
            }
        }
    }

    private static void syncVrState(MMDModelManager.Model modelData,
                                    AbstractClientPlayer player,
                                    float tickDelta,
                                    boolean isVr) {
        IMMDModel model = modelData.model;
        if (!(model instanceof AbstractMMDModel abstractModel)) {
            return;
        }

        if (isVr) {
            LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
            if (localPlayer != null && localPlayer.getUUID().equals(player.getUUID())) {
                FirstPersonManager.vrRuntime().applyMmdRenderState(true);
            }
            if (!abstractModel.isVrActive()) {
                MmdSkinRendererPlayerHelper.suppressDefaultAnimationState(modelData);
                FirstPersonManager.vrRuntime().setModelVrEnabled(model.getModelHandle(), true);
                abstractModel.setVrActive(true);
            }
            FirstPersonManager.vrRuntime().updateModelVr(
                    model.getModelHandle(),
                    player,
                    tickDelta,
                    ConfigManager.getVRArmIKStrength()
            );
            return;
        }

        LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer != null && localPlayer.getUUID().equals(player.getUUID())) {
            FirstPersonManager.vrRuntime().applyMmdRenderState(false);
        }
        if (abstractModel.isVrActive()) {
            FirstPersonManager.vrRuntime().setModelVrEnabled(model.getModelHandle(), false);
            abstractModel.setVrActive(false);
            MmdSkinRendererPlayerHelper.resetModelAnimationState(player, modelData);
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
