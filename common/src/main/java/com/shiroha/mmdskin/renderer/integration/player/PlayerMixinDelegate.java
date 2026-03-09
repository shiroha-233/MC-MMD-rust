package com.shiroha.mmdskin.renderer.integration.player;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.compat.vr.VRBoneDriver;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.shiroha.mmdskin.renderer.runtime.model.AbstractMMDModel;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.scene.client.SceneModelManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * 玩家渲染 Mixin 委托。
 */
public final class PlayerMixinDelegate {

    private PlayerMixinDelegate() {}

    public enum RenderAction {
        CANCEL,
        FALLTHROUGH,
        SUPER_RENDER
    }

    public static RenderAction handleRender(
            AbstractClientPlayer player, float entityYaw, float tickDelta,
            PoseStack matrixStack, MultiBufferSource vertexConsumers, int packedLight,
            boolean isYsmActive) {

        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());

        if (isLocalPlayer && FirstPersonManager.shouldRenderFirstPerson()) {
            String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
            if (isYsmActive) {
                return RenderAction.CANCEL;
            }
            if (selectedModel == null || selectedModel.isEmpty()
                    || selectedModel.equals("默认 (原版渲染)") || player.isSpectator()) {
                return RenderAction.CANCEL;
            }
        }

        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        String playerCacheKey = PlayerModelResolver.getCacheKey(player);

        if (selectedModel == null || selectedModel.isEmpty()
                || selectedModel.equals("默认 (原版渲染)") || isYsmActive || player.isSpectator()) {
            return RenderAction.FALLTHROUGH;
        }

        MMDModelManager.Model modelData = MMDModelManager.GetModel(selectedModel, playerCacheKey);

        if (modelData == null) {
            if (MMDModelManager.isModelPending(selectedModel, playerCacheKey)) {
                return RenderAction.CANCEL;
            }
            return RenderAction.SUPER_RENDER;
        }

        IMMDModel model = modelData.model;
        modelData.loadModelProperties(false);

        float[] size = PlayerRenderHelper.getModelSize(modelData);

        boolean isVR = isLocalPlayer && VRArmHider.isLocalPlayerInVR();
        if (model instanceof AbstractMMDModel abstractModel) {
            if (isVR) {

                if (!abstractModel.isVrActive()) {
                    VRBoneDriver.setVREnabled(model.getModelHandle(), true);
                    abstractModel.setVrActive(true);
                }

                VRBoneDriver.driveModel(model.getModelHandle(), player, tickDelta);
            } else if (abstractModel.isVrActive()) {

                VRBoneDriver.setVREnabled(model.getModelHandle(), false);
                abstractModel.setVrActive(false);
            }
        }

        float combinedScale = size[0] * ModelConfigManager.getConfig(selectedModel).modelScale;
        if (!isVR) {
            FirstPersonManager.preRender(NativeFunc.GetInst(), model.getModelHandle(), combinedScale, isLocalPlayer);
        }
        boolean isFirstPerson = !isVR && isLocalPlayer && FirstPersonManager.isActive();

        AnimationStateManager.updateAnimationState(player, modelData);

        if (!isLocalPlayer) {
            PendingAnimSignalCache.SignalType signal = PendingAnimSignalCache.consume(player.getUUID());
            if (signal == PendingAnimSignalCache.SignalType.RESET) {
                MmdSkinRendererPlayerHelper.ResetPhysics(player);
            }
        }

        RenderParams params = PlayerRenderHelper.calculateRenderParams(player, modelData, tickDelta);

        matrixStack.pushPose();

        if (InventoryRenderHelper.isInventoryScreen()) {
            InventoryRenderHelper.renderInInventory(player, model, entityYaw, tickDelta, matrixStack, packedLight, size);
        } else {
            matrixStack.scale(size[0], size[0], size[0]);
            RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
            RenderContext ctx = isFirstPerson ? RenderContext.FIRST_PERSON : RenderContext.WORLD;
            model.render(player, params.bodyYaw, params.bodyPitch, params.translation, tickDelta, matrixStack, packedLight, ctx);
        }

        if (isFirstPerson) {
            FirstPersonManager.postRender(NativeFunc.GetInst(), model.getModelHandle());
        }

        ItemRenderHelper.renderItems(player, modelData, matrixStack, vertexConsumers, packedLight);
        matrixStack.popPose();

        return RenderAction.CANCEL;
    }

    public static void renderSceneModel(AbstractClientPlayer player, float tickDelta,
                                         PoseStack matrixStack, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(player.getUUID())) return;

        SceneModelManager sceneMgr = SceneModelManager.getInstance();
        if (!sceneMgr.isActive() && !sceneMgr.isLoading()) return;

        double renderX = Mth.lerp(tickDelta, player.xo, player.getX());
        double renderY = Mth.lerp(tickDelta, player.yo, player.getY());
        double renderZ = Mth.lerp(tickDelta, player.zo, player.getZ());
        sceneMgr.renderScene(matrixStack, tickDelta, packedLight, renderX, renderY, renderZ);
    }
}
