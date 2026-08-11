package com.shiroha.mmdskin.compat.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.bridge.runtime.NativeTaczArmTargetPort;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.render.PlayerRenderHelper;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.render.backend.BaseModelInstance;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Optional;

/** 文件职责：在 TaCZ 完成枪械批次后，同帧提交双臂目标并绘制 prepared MMD。 */
public final class TaczFirstPersonPostRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int LEFT_MASK = 0b01;
    private static final int RIGHT_MASK = 0b10;
    private static final int ARM_DIAGNOSTIC_FLOAT_COUNT = 24;
    private static final int ARM_DIAGNOSTIC_STRIDE = 12;
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 5_000_000_000L;
    private static final ThreadLocal<DeferredDraw> DEFERRED_DRAW = new ThreadLocal<>();
    private static long lastDiagnosticNanos;

    private TaczFirstPersonPostRenderer() {
    }

    /** 保存一次性绘制上下文；新帧会主动替换异常遗留的旧上下文。 */
    public static void defer(AbstractClientPlayer player, ItemStack gunStack, ManagedModel managedModel,
                             BaseModelInstance model, MutableRenderPose pose, PoseStack poseStack,
                             float tickDelta, int packedLight, float outerScale) {
        clearDeferredDraw();
        if (player == null || gunStack == null || model == null || pose == null
                || poseStack == null || !model.hasPreparedFirstPersonPose() || model.isVrActive()) {
            return;
        }
        PoseStack savedStack = copyPoseStack(poseStack);
        savedStack.scale(outerScale, outerScale, outerScale);
        MutableRenderPose savedPose = new MutableRenderPose();
        // 复用相机准备阶段的角度，确保 prepared pose、眼位、目标换算和最终模型根属于同一坐标系。
        savedPose.bodyYaw = pose.bodyYaw;
        savedPose.bodyPitch = pose.bodyPitch;
        savedPose.translation.set(pose.translation);
        DEFERRED_DRAW.set(new DeferredDraw(player, gunStack, model,
                savedPose, savedStack, tickDelta, packedLight));
    }

    /**
     * 世界实体入口被 BER/OBJ/实例化渲染流程跳过时，从相机阶段已准备的姿态恢复同帧后置上下文。
     */
    public static boolean ensureDeferredAtTaczHead(AbstractClientPlayer player, ItemStack gunStack,
                                                   float tickDelta, int packedLight) {
        if (hasUsableDeferredDraw(player, gunStack)) {
            return true;
        }
        clearDeferredDraw();

        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.player != player || !FirstPersonManager.shouldRenderFirstPerson()) {
            return false;
        }

        try {
            PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
            if (resolved == null || !(resolved.model().modelInstance() instanceof BaseModelInstance model)
                    || model.getModelHandle() == 0L || !model.hasPreparedFirstPersonPose() || model.isVrActive()) {
                return false;
            }

            float safeTickDelta = Float.isFinite(tickDelta) ? Mth.clamp(tickDelta, 0.0f, 1.0f) : 0.0f;
            ManagedModel managedModel = resolved.model();
            MutableRenderPose pose = PlayerRenderHelper.calculateMutableRenderPose(player, managedModel, safeTickDelta);
            ModelConfigData modelConfig = ModelConfigManager.getConfig(managedModel.requestKey().modelName());
            float outerScale = managedModel.renderProperties().modelScale();
            float combinedScale = outerScale * modelConfig.modelScale;
            FirstPersonManager.preRender(model.getModelHandle(), combinedScale, true);

            Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
            PoseStack entityRoot = createEntityRootPose(
                    Mth.lerp(safeTickDelta, player.xo, player.getX()),
                    Mth.lerp(safeTickDelta, player.yo, player.getY()),
                    Mth.lerp(safeTickDelta, player.zo, player.getZ()),
                    camera.x, camera.y, camera.z);
            defer(player, gunStack, managedModel, model, pose, entityRoot,
                    safeTickDelta, packedLight, outerScale);
            return hasUsableDeferredDraw(player, gunStack);
        } catch (RuntimeException | LinkageError e) {
            clearDeferredDraw();
            LOGGER.warn("TaCZ 第一人称 MMD 后置上下文恢复失败，将保留原版手臂回退", e);
            return false;
        }
    }

    /** 同一玩家当前帧已有可用 MMD 后置绘制时，禁止 TaCZ 内部栈切换导致方块手臂闪回。 */
    static boolean ownsArmRendering(Object player, Object gunStack) {
        DeferredDraw draw = DEFERRED_DRAW.get();
        boolean ready = hasUsableDeferredDraw(player, gunStack);
        if (!ready && draw != null) {
            diagnose("原版手臂未抑制: playerSame={}, stackSameObject={}, stackSameContent={}, prepared={}",
                    draw.player == player, draw.gunStack == gunStack,
                    TaczFirstPersonFrameSnapshot.matchesGunStack(draw.gunStack, gunStack),
                    draw.model.hasPreparedFirstPersonPose());
        }
        return ready;
    }

    private static boolean hasUsableDeferredDraw(Object player, Object gunStack) {
        DeferredDraw draw = DEFERRED_DRAW.get();
        return draw != null && draw.player == player
                && TaczFirstPersonFrameSnapshot.matchesGunStack(draw.gunStack, gunStack)
                && draw.model.getModelHandle() != 0L && draw.model.hasPreparedFirstPersonPose();
    }

    /** TaCZ wrapper 的 RETURN 唯一调用入口。 */
    public static void finish(TaczFirstPersonFrameSnapshot.Snapshot snapshot) {
        DeferredDraw draw = DEFERRED_DRAW.get();
        if (draw == null) {
            return;
        }
        NativeTaczArmTargetPort targetPort = (NativeTaczArmTargetPort) NativeRuntimeBridgeHolder.get();
        long modelHandle = draw.model.getModelHandle();
        try {
            boolean stackMatches = snapshot != null
                    && TaczFirstPersonFrameSnapshot.matchesGunStack(draw.gunStack, snapshot.gunStack());
            if (snapshot == null || snapshot.localPlayer() != draw.player || !stackMatches) {
                diagnose("后置绘制被丢弃: snapshot={}, playerSame={}, stackSameObject={}, stackSameContent={}",
                        snapshot != null,
                        snapshot != null && snapshot.localPlayer() == draw.player,
                        snapshot != null && snapshot.gunStack() == draw.gunStack,
                        stackMatches);
                draw.model.discardPreparedFirstPersonPose();
                return;
            }

            TargetPacket packet = createTargetPacket(draw, snapshot);
            boolean targetsAccepted = packet.validMask != 0
                    && targetPort.setTaczArmTargets(modelHandle, packet.matrices, packet.validMask);
            boolean poseRefreshed = targetsAccepted && draw.model.refreshPreparedFirstPersonPose();
            int nativeResult = poseRefreshed ? targetPort.getLastTaczArmApplyResult(modelHandle) : 0;
            float[] armDiagnostics = new float[ARM_DIAGNOSTIC_FLOAT_COUNT];
            boolean hasArmDiagnostics = poseRefreshed
                    && targetPort.getLastTaczArmDiagnostics(modelHandle, armDiagnostics);
            diagnose("TaCZ 第一人称帧 {}: capturedMask={}, targetMask={}, nativeReceivedMask={}, nativeAppliedMask={}, stackSameObject={}, targetsAccepted={}, poseRefreshed={}, bodyYaw={}, viewYaw={}, taczEntry={}, leftParent={}, rightParent={}, leftEntryLocal={}, rightEntryLocal={}, leftTarget={}, rightTarget={}, leftSolve={}, rightSolve={}",
                    snapshot.frameId(), snapshot.capturedMask(), packet.validMask,
                    nativeResult & 0b11, (nativeResult >> 2) & 0b11,
                    snapshot.gunStack() == draw.gunStack, targetsAccepted, poseRefreshed,
                    draw.player.yBodyRot, draw.pose.bodyYaw,
                    matrixPosition(snapshot.entryPose()),
                    capturedPosition(snapshot, TaczFirstPersonFrameSnapshot.Hand.LEFT),
                    capturedPosition(snapshot, TaczFirstPersonFrameSnapshot.Hand.RIGHT),
                    entryRelativePosition(snapshot, TaczFirstPersonFrameSnapshot.Hand.LEFT),
                    entryRelativePosition(snapshot, TaczFirstPersonFrameSnapshot.Hand.RIGHT),
                    position(packet.matrices, 0, packet.validMask, LEFT_MASK),
                    position(packet.matrices, 16, packet.validMask, RIGHT_MASK),
                    formatArmDiagnostic(armDiagnostics, 0, hasArmDiagnostics),
                    formatArmDiagnostic(armDiagnostics, ARM_DIAGNOSTIC_STRIDE, hasArmDiagnostics));
            if (targetsAccepted && !poseRefreshed) {
                LOGGER.warn("TaCZ 双臂目标已提交，但 prepared MMD 姿态刷新失败，frame={}", snapshot.frameId());
            }

            // TaCZ RETURN 可能紧跟 BER/OBJ/实例化批次，延后绘制必须拥有独立且可恢复的 GL 状态。
            try (TaczDeferredRenderState ignored = TaczDeferredRenderState.begin()) {
                draw.model.render(draw.player, draw.pose.bodyYaw, draw.pose.bodyPitch, draw.pose.translation,
                        draw.tickDelta, draw.poseStack, draw.packedLight, RenderScene.FIRST_PERSON);
            }
            FirstPersonManager.postRender(modelHandle, draw.player, draw.tickDelta);
        } finally {
            targetPort.clearTaczArmTargets(modelHandle);
            draw.model.discardPreparedFirstPersonPose();
            DEFERRED_DRAW.remove();
        }
    }

    /** 清除未被 TaCZ 消费的延后绘制，避免切枪或异常后跨帧复用。 */
    public static void clearDeferredDraw() {
        DeferredDraw old = DEFERRED_DRAW.get();
        if (old != null) {
            old.model.discardPreparedFirstPersonPose();
        }
        DEFERRED_DRAW.remove();
    }

    private static TargetPacket createTargetPacket(DeferredDraw draw,
                                                    TaczFirstPersonFrameSnapshot.Snapshot snapshot) {
        PoseStack rootStack = copyPoseStack(draw.poseStack);
        draw.model.applyModelRootTransform(rootStack, draw.pose.bodyYaw, draw.pose.bodyPitch, draw.pose.translation);
        Matrix4f rootToCamera = new Matrix4f(rootStack.last().pose());
        if (!isFinite(rootToCamera) || Math.abs(rootToCamera.determinant()) < 1.0e-8f) {
            return new TargetPacket(new float[32], 0);
        }

        Matrix4f cameraToModel = rootToCamera.invert(new Matrix4f());
        boolean slimArms = "slim".equals(draw.player.getModelName());
        float[] matrices = new float[32];
        int mask = 0;
        Optional<TaczFirstPersonFrameSnapshot.HandMatrices> left = snapshot.consume(
                draw.player, draw.gunStack, TaczFirstPersonFrameSnapshot.Hand.LEFT);
        if (left.isPresent() && writeRigidTarget(cameraToModel,
                TaczVanillaArmGeometry.toWrist(left.get().pose(),
                        TaczFirstPersonFrameSnapshot.Hand.LEFT, slimArms), matrices, 0)) {
            mask |= LEFT_MASK;
        }
        Optional<TaczFirstPersonFrameSnapshot.HandMatrices> right = snapshot.consume(
                draw.player, draw.gunStack, TaczFirstPersonFrameSnapshot.Hand.RIGHT);
        if (right.isPresent() && writeRigidTarget(cameraToModel,
                TaczVanillaArmGeometry.toWrist(right.get().pose(),
                        TaczFirstPersonFrameSnapshot.Hand.RIGHT, slimArms), matrices, 16)) {
            mask |= RIGHT_MASK;
        }
        return new TargetPacket(matrices, mask);
    }

    private static boolean writeRigidTarget(Matrix4f cameraToModel, Matrix4f handToCamera,
                                            float[] output, int offset) {
        Matrix4f raw = cameraToModel.mul(handToCamera, new Matrix4f());
        if (!isFinite(raw)) {
            return false;
        }
        Quaternionf rotation = raw.getUnnormalizedRotation(new Quaternionf()).normalize();
        if (!Float.isFinite(rotation.x) || !Float.isFinite(rotation.y)
                || !Float.isFinite(rotation.z) || !Float.isFinite(rotation.w)) {
            return false;
        }
        Matrix4f rigid = new Matrix4f().rotation(rotation).setTranslation(raw.m30(), raw.m31(), raw.m32());
        rigid.get(output, offset);
        return isFinite(rigid);
    }

    private static PoseStack copyPoseStack(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    /** 与 LevelRenderer 实体入口一致，只建立“实体插值位置减相机位置”的根平移。 */
    static PoseStack createEntityRootPose(double entityX, double entityY, double entityZ,
                                          double cameraX, double cameraY, double cameraZ) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(entityX - cameraX, entityY - cameraY, entityZ - cameraZ);
        return poseStack;
    }

    private static boolean isFinite(Matrix4f matrix) {
        float[] values = new float[16];
        matrix.get(values);
        for (float value : values) {
            if (!Float.isFinite(value)) return false;
        }
        return true;
    }

    private static String formatArmDiagnostic(float[] values, int offset, boolean available) {
        if (!available || !Float.isFinite(values[offset + 9])) {
            return "missing";
        }
        float signedReach = values[offset + 11];
        return String.format(java.util.Locale.ROOT,
                "target=(%.4f,%.4f,%.4f),wrist=(%.4f,%.4f,%.4f),attachment=(%.4f,%.4f,%.4f),error=%.4f,targetDistance=%.4f,maxReach=%.4f,clamped=%s",
                values[offset], values[offset + 1], values[offset + 2],
                values[offset + 3], values[offset + 4], values[offset + 5],
                values[offset + 6], values[offset + 7], values[offset + 8],
                values[offset + 9], values[offset + 10], Math.abs(signedReach), signedReach < 0.0f);
    }

    private static String capturedPosition(TaczFirstPersonFrameSnapshot.Snapshot snapshot,
                                           TaczFirstPersonFrameSnapshot.Hand hand) {
        return snapshot.capturedHand(hand)
                .map(matrices -> matrixPosition(matrices.pose()))
                .orElse("missing");
    }

    private static String entryRelativePosition(TaczFirstPersonFrameSnapshot.Snapshot snapshot,
                                                TaczFirstPersonFrameSnapshot.Hand hand) {
        Optional<TaczFirstPersonFrameSnapshot.HandMatrices> captured = snapshot.capturedHand(hand);
        Matrix4f entry = snapshot.entryPose();
        if (captured.isEmpty() || !isFinite(entry) || Math.abs(entry.determinant()) < 1.0e-8f) {
            return captured.isEmpty() ? "missing" : "invalid";
        }
        Matrix4f entryToHand = entry.invert(new Matrix4f()).mul(captured.get().pose());
        return isFinite(entryToHand) ? matrixPosition(entryToHand) : "invalid";
    }

    private static String matrixPosition(Matrix4f matrix) {
        if (!isFinite(matrix)) {
            return "invalid";
        }
        return String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)",
                matrix.m30(), matrix.m31(), matrix.m32());
    }

    private static String position(float[] matrices, int offset, int validMask, int handMask) {
        if ((validMask & handMask) == 0) {
            return "missing";
        }
        return String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)",
                matrices[offset + 12], matrices[offset + 13], matrices[offset + 14]);
    }

    /** 成功路径也只按固定间隔输出一次，避免第一人称逐帧刷满整合包日志。 */
    private static void diagnose(String message, Object... arguments) {
        long now = System.nanoTime();
        if (now - lastDiagnosticNanos < DIAGNOSTIC_INTERVAL_NANOS) {
            return;
        }
        lastDiagnosticNanos = now;
        LOGGER.info(message, arguments);
    }

    private record TargetPacket(float[] matrices, int validMask) {
    }

    private record DeferredDraw(AbstractClientPlayer player, ItemStack gunStack,
                                BaseModelInstance model, MutableRenderPose pose, PoseStack poseStack,
                                float tickDelta, int packedLight) {
    }
}
