// 负责在实体阶段结束时提取本地玩家状态并贡献一次第一人称模型绘制。
package com.shiroha.mmdskin.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.draw.FrameRenderQueue;
import com.shiroha.mmdskin.client.draw.MmdDrawRequest;
import com.shiroha.mmdskin.client.draw.MmdHeldItemRenderer;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.frame.MmdFrameUpdater;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.MmdSnapshotFactory;
import com.shiroha.mmdskin.client.frame.ModelTransform;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.client.model.ModelLease;
import com.shiroha.mmdskin.client.model.ModelRepository;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Matrix4f;

import java.util.Objects;

public final class LocalPlayerRenderContributor {
    private final ModelRepository models;
    private final MmdFrameUpdater frameUpdater;
    private final FrameRenderQueue frameQueue;
    private final FirstPersonSession session;

    public LocalPlayerRenderContributor(ModelRepository models, MmdFrameUpdater frameUpdater,
                                        FrameRenderQueue frameQueue, FirstPersonSession session) {
        this.models = Objects.requireNonNull(models, "models");
        this.frameUpdater = Objects.requireNonNull(frameUpdater, "frameUpdater");
        this.frameQueue = Objects.requireNonNull(frameQueue, "frameQueue");
        this.session = Objects.requireNonNull(session, "session");
    }

    public boolean contribute(Minecraft minecraft, Camera camera, float partialTick,
                              long frameId, float deltaSeconds, boolean compatibilityConflict,
                              PoseStack worldPose, SubmitNodeCollector collector) {
        LocalPlayer player = minecraft.player;
        if (IrisCompatibility.isShadowPass()) {
            return false;
        }
        if (player == null || compatibilityConflict || !MmdRenderPipelines.isReady()
                || !session.shouldContribute(player)) {
            session.reset();
            return false;
        }

        LivingEntityRenderState state = extractState(minecraft, player, partialTick);
        MmdRenderSnapshot extracted = state == null ? null : MmdSnapshotFactory.capture(
                player, state, MmdRenderSnapshot.Context.FIRST_PERSON);
        if (extracted == null || (!extracted.rendersBody() && !extracted.glowing())) {
            session.reset();
            return false;
        }
        ModelLease lease = models.acquire(extracted.modelKey()).orElse(null);
        if (lease == null) {
            session.reset();
            return false;
        }

        boolean queued = false;
        try {
            MmdModelInstance model = lease.instance();
            float configuredScale = ModelConfigManager.getLiveConfig(model.modelName()).modelScale;
            FirstPersonSession.PreparedModel prepared = session.prepareModel(
                    model, player, partialTick, configuredScale);
            if (prepared.enteredVr()) {
                MmdSkinRendererPlayerHelper.suppressDefaultAnimationState(model);
            } else if (prepared.leftVr()) {
                MmdSkinRendererPlayerHelper.resetModelAnimationState(player, model);
            }
            // modelMat 使用相机相对坐标（state - camera）：与 26.2 官方例程一致，
            // ModelView（CameraMatrices）只含视图旋转，顶点自身携带 -pos。
            double x = state.x - camera.position().x;
            double y = state.y - camera.position().y;
            double z = state.z - camera.position().z;
            int packedLight = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
            Matrix4f matrix = new Matrix4f()
                    .translation((float) x, (float) y, (float) z)
                    .rotateY((float) Math.toRadians(-state.bodyRot));
            MmdRenderSnapshot animationSnapshot = prepared.vr()
                    ? extracted.withAnimationIntent(MmdAnimationIntent.none())
                    : extracted;
            MmdRenderSnapshot snapshot = animationSnapshot.withRenderTransform(
                    ModelTransform.from(matrix), packedLight);
            frameUpdater.updateOnce(frameId, model, snapshot, deltaSeconds);
            session.captureUpdatedEyeAnchor(model, player, partialTick);
            boolean transparent = snapshot.visibility() == MmdRenderSnapshot.Visibility.TRANSLUCENT;
            frameQueue.enqueue(new MmdDrawRequest(
                    model,
                    snapshot,
                    choosePipeline(snapshot),
                    transparent), lease);
            queued = true;

            if (worldPose != null && collector != null) {
                worldPose.pushPose();
                try {
                    worldPose.translate(x, y, z);
                    MmdHeldItemRenderer.render(model, worldPose, collector, player,
                            packedLight, state.bodyRot);
                } finally {
                    worldPose.popPose();
                }
            }
            return true;
        } finally {
            if (!queued) {
                lease.close();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LivingEntityRenderState extractState(Minecraft minecraft, LocalPlayer player,
                                                         float partialTick) {
        EntityRenderer renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        EntityRenderState state = (EntityRenderState) renderer.createRenderState(player, partialTick);
        return state instanceof LivingEntityRenderState livingState ? livingState : null;
    }

    private static PipelineVariant choosePipeline(MmdRenderSnapshot snapshot) {
        if (snapshot.glowing() && !snapshot.rendersBody()) {
            return PipelineVariant.GLOWING;
        }
        if (ConfigManager.isToonRenderingEnabled()) {
            return PipelineVariant.TOON;
        }
        return snapshot.visibility() == MmdRenderSnapshot.Visibility.TRANSLUCENT
                ? PipelineVariant.TRANSLUCENT : PipelineVariant.OPAQUE;
    }
}
