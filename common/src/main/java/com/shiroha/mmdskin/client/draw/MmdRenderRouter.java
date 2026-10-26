// 负责决定 MMD 接管或原版回退并把模型提交到 Frame Queue。
package com.shiroha.mmdskin.client.draw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.ModelTransform;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.player.ragdoll.RagdollManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;

public final class MmdRenderRouter {
    private MmdRenderRouter() {
    }

    public static Result route(MmdRenderSnapshot extractedSnapshot, PoseStack poseStack,
                               int packedLight, boolean compatibilityConflict) {
        return route(extractedSnapshot, poseStack, null, null, packedLight, compatibilityConflict);
    }

    public static Result routePlayer(MmdRenderSnapshot extractedSnapshot, AvatarRenderState playerState,
                                     PoseStack poseStack, SubmitNodeCollector buffers,
                                     int packedLight, boolean compatibilityConflict) {
        return route(extractedSnapshot, poseStack, playerState, buffers, packedLight, compatibilityConflict);
    }

    private static Result route(MmdRenderSnapshot extractedSnapshot, PoseStack poseStack,
                                AvatarRenderState playerState, SubmitNodeCollector buffers,
                                int packedLight, boolean compatibilityConflict) {
        if (extractedSnapshot == null || compatibilityConflict || IrisCompatibility.isShadowPass()
                || !MmdRenderPipelines.isReady()) {
            return Result.FALLTHROUGH;
        }
        if (!extractedSnapshot.rendersBody() && !extractedSnapshot.glowing()) {
            return Result.FALLTHROUGH;
        }
        // 尸体模型就绪后，本体不再绘制——否则死亡瞬间会同时看到本体和尸体两重身影。
        // （1.20.1 是在 MobReplacementRenderer / PlayerMixinDelegate 里做同样的判定。）
        if (RagdollManager.getInstance().shouldSuppressEntityRender(extractedSnapshot.entityId())) {
            return Result.SUPPRESSED;
        }

        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
        // 加载前距离闸门：太远、而且还没加载的模型直接不加载。
        // 不 acquire 就不会触发 PMX 解析 + 独立 Bullet 物理世界 + 4 个 GPU buffer——
        // "模型一多就严重卡顿"的根因就在这里（每个实体一份完整 native 模型）。
        // 只挡"还没加载"的：已经加载的继续正常渲染，走近时不会闪回原版皮肤。
        if (!runtime.models().isLoaded(extractedSnapshot.modelKey())
                && exceedsModelLoadDistance(extractedSnapshot.cameraDistanceSquared())) {
            return Result.FALLTHROUGH;
        }
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
            Matrix4f modelMatrix = new Matrix4f(poseStack.last().pose())
                    .rotateY((float) Math.toRadians(-extractedSnapshot.pose().bodyYaw()));
            MmdRenderSnapshot snapshot = extractedSnapshot.withRenderTransform(
                    ModelTransform.from(modelMatrix), packedLight);
            // 物理 LOD：远处模型关掉 MMD 物理（发饰/裙摆的次级运动），回到范围内再打开。
            // 引擎已有 per-model 开关（SetPhysicsEnabled），不需要改 Rust。
            applyPhysicsLod(model, snapshot.cameraDistanceSquared());
            // 动画 LOD 只在世界上下文开启：物品栏预览是近距离特写，
            // 按世界相机距离降频会让预览看起来一顿一顿的。
            boolean animationLod = extractedSnapshot.context() == MmdRenderSnapshot.Context.WORLD;
            runtime.frameUpdater().updateOnce(
                    runtime.frameId(), model, snapshot, runtime.frameDeltaSeconds(), animationLod);
            PipelineVariant pipeline = choosePipeline(snapshot);
            MmdDrawRequest request = new MmdDrawRequest(
                    model, snapshot, pipeline,
                    snapshot.visibility() == MmdRenderSnapshot.Visibility.TRANSLUCENT);
            if (runtime.inventory().active()) {
                // GUI 实体预览（背包里的玩家模型）走背包自己的队列。
                //
                // 这里必须看**当前**会话是否活跃，而不是快照里采集时记下的上下文：
                // 26.x 的背包预览是"先提取渲染状态、稍后在 GUI 阶段再提交"两段，
                // 提交时采集阶段的会话早就关了，按快照上下文走会直接抛
                // "inventory render session is not active" 把游戏带崩。
                runtime.inventory().enqueue(request, lease);
            } else {
                runtime.frameQueue().enqueue(request, lease);
            }
            queued = true;
            if (playerState != null && buffers != null) {
                MmdHeldItemRenderer.render(playerState, model, poseStack, buffers,
                        packedLight, snapshot.pose().bodyYaw());
            }
            return Result.QUEUED;
        } finally {
            if (!queued) {
                lease.close();
            }
        }
    }

    /**
     * 加载距离闸门判定。
     *
     * <p>{@code modelLoadMaxDistance} &lt;= 0 表示不限制（闸门关闭，等价于旧行为）。
     */
    private static boolean exceedsModelLoadDistance(double cameraDistanceSquared) {
        float maxDistance = ConfigManager.getModelLoadMaxDistance();
        if (maxDistance <= 0.0F) {
            return false;
        }
        return cameraDistanceSquared > (double) maxDistance * maxDistance;
    }

    /**
     * 物理 LOD：超出 {@code physicsLodMaxDistance} 的模型关掉 MMD 物理，
     * 回到范围内再打开。
     *
     * <p>关掉的是发饰/裙摆这类次级运动的 Bullet 步进，主体骨骼动画不受影响。
     * 引擎侧已有 per-model 开关（SetPhysicsEnabled），不需要改 Rust。
     *
     * <p>只在实体路径调用：尸体路径有自己的冻结/启物理逻辑，两边同时写会打架。
     */
    private static void applyPhysicsLod(MmdModelInstance model, double cameraDistanceSquared) {
        float maxDistance = ConfigManager.getPhysicsLodMaxDistance();
        // maxDistance <= 0 = 不做 LOD：物理保持开启（也用于把之前被关掉的模型恢复回来）。
        boolean withinRange = maxDistance <= 0.0F
                || cameraDistanceSquared <= (double) maxDistance * maxDistance;
        model.setPhysicsEnabled(withinRange);
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
        /** 交回原版渲染。 */
        FALLTHROUGH,
        /** MMD 模型已入队，原版主体不再绘制。 */
        QUEUED,
        /** 本地第一人称推迟到专门的通道渲染。 */
        DEFERRED_LOCAL,
        /** 尸体已接管，本体整体不画（原版主体也不画）。 */
        SUPPRESSED
    }
}
