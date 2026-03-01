package com.shiroha.mmdskin.renderer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.compat.vr.VRBoneDriver;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.renderer.animation.AnimationStateManager;
import com.shiroha.mmdskin.renderer.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.renderer.core.FirstPersonManager;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.core.RenderParams;
import com.shiroha.mmdskin.renderer.model.AbstractMMDModel;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.renderer.model.SceneModelManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * 玩家渲染 Mixin 委托（DRY 原则）
 * 将 Fabric/Forge Mixin 中完全相同的渲染逻辑集中到此处，
 * Mixin 只需调用此类的静态方法即可。
 */
public final class PlayerMixinDelegate {

    private PlayerMixinDelegate() {}

    /**
     * 渲染结果枚举
     */
    public enum RenderAction {
        CANCEL,         // 取消原版渲染（ci.cancel）
        FALLTHROUGH,    // 不干预，让原版流程继续
        SUPER_RENDER    // 调用 super.render()
    }

    /**
     * 核心渲染逻辑
     * @param isYsmActive 由平台 Mixin 传入的 YSM 激活状态
     * @param superRender 回退到原版渲染的回调
     */
    public static RenderAction handleRender(
            AbstractClientPlayer player, float entityYaw, float tickDelta,
            PoseStack matrixStack, MultiBufferSource vertexConsumers, int packedLight,
            boolean isYsmActive) {

        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());

        // 第一人称模式下的 YSM 优先级处理
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

        // 让渡渲染权给原版流程（包括 YSM）
        if (selectedModel == null || selectedModel.isEmpty()
                || selectedModel.equals("默认 (原版渲染)") || isYsmActive || player.isSpectator()) {
            return RenderAction.FALLTHROUGH;
        }

        // 加载模型
        MMDModelManager.Model modelData = MMDModelManager.GetModel(selectedModel, playerName);

        if (modelData == null) {
            if (MMDModelManager.isModelPending(selectedModel, playerName)) {
                return RenderAction.CANCEL;
            }
            return RenderAction.SUPER_RENDER;
        }

        IMMDModel model = modelData.model;
        modelData.loadModelProperties(false);

        float[] size = PlayerRenderHelper.getModelSize(modelData);

        // VR 模式集成：启用 IK 并传递追踪数据
        boolean isVR = isLocalPlayer && VRArmHider.isLocalPlayerInVR();
        if (model instanceof AbstractMMDModel abstractModel) {
            if (isVR) {
                // 启用 Rust 侧 VR IK
                if (!abstractModel.isVrActive()) {
                    VRBoneDriver.setVREnabled(model.getModelHandle(), true);
                    abstractModel.setVrActive(true);
                }
                // 每帧传递控制器追踪数据（转换到模型局部空间）
                VRBoneDriver.driveModel(model.getModelHandle(), player, tickDelta);
            } else if (abstractModel.isVrActive()) {
                // 退出 VR 模式时恢复
                VRBoneDriver.setVREnabled(model.getModelHandle(), false);
                abstractModel.setVrActive(false);
            }
        }

        // 第一人称管理（VR 模式下跳过，由 VR IK 接管）
        float combinedScale = size[0] * ModelConfigManager.getConfig(selectedModel).modelScale;
        if (!isVR) {
            FirstPersonManager.preRender(NativeFunc.GetInst(), model.getModelHandle(), combinedScale, isLocalPlayer);
        }
        boolean isFirstPerson = !isVR && isLocalPlayer && FirstPersonManager.isActive();

        AnimationStateManager.updateAnimationState(player, modelData);

        // 远程玩家：消费延迟的动画中断信号（target 不在渲染范围时缓存的）
        if (!isLocalPlayer) {
            PendingAnimSignalCache.SignalType signal = PendingAnimSignalCache.consume(player.getUUID());
            if (signal != null) {
                switch (signal) {
                    case RESET -> MmdSkinRendererPlayerHelper.ResetPhysics(player);
                    case STAGE_END -> StageAnimSyncHelper.endStageAnim(player);
                }
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

    /**
     * 场景模型渲染（独立于玩家模型，由 Mixin 在每次本地玩家渲染时调用）
     */
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
