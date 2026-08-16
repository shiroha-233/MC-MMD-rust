// 负责决定 MMD 接管或原版回退并把模型提交到 Frame Queue。
package com.shiroha.mmdskin.client.draw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.ModelTransform;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

public final class MmdRenderRouter {
    private MmdRenderRouter() {
    }

    public static Result route(MmdRenderSnapshot extractedSnapshot, PoseStack poseStack,
                               int packedLight, boolean compatibilityConflict) {
        return route(extractedSnapshot, poseStack, null, null, null, packedLight, compatibilityConflict);
    }

    public static Result routePlayer(MmdRenderSnapshot extractedSnapshot, LivingEntityRenderState playerState,
                                     LivingEntity player, PoseStack poseStack, SubmitNodeCollector collector,
                                     int packedLight, boolean compatibilityConflict) {
        return route(extractedSnapshot, poseStack, playerState, player, collector, packedLight, compatibilityConflict);
    }

    private static Result route(MmdRenderSnapshot extractedSnapshot, PoseStack poseStack,
                                LivingEntityRenderState playerState, LivingEntity player,
                                SubmitNodeCollector collector,
                                int packedLight, boolean compatibilityConflict) {
        if (extractedSnapshot == null || compatibilityConflict || IrisCompatibility.isShadowPass()
                || !MmdRenderPipelines.isReady()) {
            return Result.FALLTHROUGH;
        }
        if (!extractedSnapshot.rendersBody() && !extractedSnapshot.glowing()) {
            return Result.FALLTHROUGH;
        }

        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
        var lease = runtime.acquire(extractedSnapshot.modelKey()).orElse(null);
        if (lease == null) {
            return Result.FALLTHROUGH;
        }
        if (runtime.firstPerson().defersWorldRoute(extractedSnapshot.entityId())) {
            lease.close();
            return Result.DEFERRED_LOCAL;
        }
        boolean queued = false;
        try {
            var model = lease.instance();
            // 26.2 实体 submit 的 poseStack 只含相机相对平移，不含朝向，
            // 需要按实体 bodyYaw 自行旋转（1.21.5 约定，26.2 未变）。
            Matrix4f modelMatrix = new Matrix4f(poseStack.last().pose())
                    .rotateY((float) Math.toRadians(-extractedSnapshot.pose().bodyYaw()));
            MmdRenderSnapshot snapshot = extractedSnapshot.withRenderTransform(
                    ModelTransform.from(modelMatrix), packedLight);
            runtime.frameUpdater().updateOnce(
                    runtime.frameId(), model, snapshot, runtime.frameDeltaSeconds());
            PipelineVariant pipeline = choosePipeline(snapshot);
            MmdDrawRequest request = new MmdDrawRequest(
                    model, snapshot, pipeline,
                    snapshot.visibility() == MmdRenderSnapshot.Visibility.TRANSLUCENT);
            if (snapshot.context() == MmdRenderSnapshot.Context.INVENTORY) {
                // 26.2 的 EntityRenderState 可复用：物品栏提交阶段（begin/finish 内）创建的
                // INVENTORY snapshot 会在 GUI 画中画阶段（inventory session 未 active）被再次
                // 提交。此时入队会抛 IllegalStateException，直接回退原版渲染。
                if (!runtime.inventory().active()) {
                    return Result.FALLTHROUGH;
                }
                runtime.inventory().enqueue(request, lease);
            } else {
                runtime.frameQueue().enqueue(request, lease);
            }
            queued = true;
            if (playerState != null && player != null && collector != null) {
                MmdHeldItemRenderer.render(model, poseStack, collector, player,
                        packedLight, snapshot.pose().bodyYaw());
            }
            return Result.QUEUED;
        } finally {
            if (!queued) {
                lease.close();
            }
        }
    }

    private static PipelineVariant choosePipeline(MmdRenderSnapshot snapshot) {
        if (snapshot.glowing() && !snapshot.rendersBody()) {
            return PipelineVariant.GLOWING;
        }
        if (ConfigManager.isToonRenderingEnabled()) {
            return PipelineVariant.TOON;
        }
        return snapshot.visibility() == MmdRenderSnapshot.Visibility.TRANSLUCENT
                ? PipelineVariant.TRANSLUCENT
                : PipelineVariant.OPAQUE;
    }

    public enum Result {
        FALLTHROUGH,
        QUEUED,
        DEFERRED_LOCAL
    }
}
