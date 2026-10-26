// 负责维护本地玩家第一人称与 VR 模型会话，不承担相机计算或绘制提交。
package com.shiroha.mmdskin.client.firstperson;

import com.shiroha.mmdskin.bridge.NativeDiagnostics;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.client.model.ModelLease;
import com.shiroha.mmdskin.client.model.ModelRepository;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.player.port.VrRuntimePort;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public final class FirstPersonSession implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final double MAX_VR_ROOT_CORRECTION = 2.5D;

    private final NativeModelPort modelPort;
    private final VrRuntimePort vrRuntime;
    private final ModelRepository models;
    private final float[] eyeBonePosition = new float[3];

    private long modelHandle;
    private ModelLease modelLease;
    private boolean desktopActive;
    private boolean vrActive;
    private boolean vrEyePassActive;
    private boolean eyeBoneValid;
    private float modelScale = 1.0F;
    private Vec3 vrModelRootOffset = Vec3.ZERO;
    private boolean vrModelRootOffsetValid;

    public FirstPersonSession(ModelRepository models, NativeModelPort modelPort, VrRuntimePort vrRuntime) {
        this.models = Objects.requireNonNull(models, "models");
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.vrRuntime = Objects.requireNonNull(vrRuntime, "vrRuntime");
    }

    public boolean shouldContribute(Player player) {
        return desktopRequested(player) || vrRequested(player);
    }

    public void beginFrame() {
        reconcileLiveState();
    }

    /**
     * 第一人称诊断：把 Rust 侧的头部识别结果、被隐藏的材质、眼睛骨骼位置写进游戏日志。
     *
     * <p>Rust 内部的 log 走的是 log crate，游戏里看不到；这条日志是唯一能在
     * 客户端侧直接观察第一人称掩码是否生效的途径。
     */
    private void logFirstPersonInfo(long handle, boolean enabled) {
        try {
            LOGGER.info("[第一人称] 模式={} 诊断: {}",
                    enabled, NativeDiagnostics.firstPersonInfo(handle));
        } catch (Throwable exception) {
            LOGGER.warn("[第一人称] 诊断信息读取失败", exception);
        }
    }

    public PreparedModel prepareModel(MmdModelInstance model, Player player,
                                      float partialTick, float effectiveModelScale) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(player, "player");
        long nextHandle = model.nativeHandle();
        boolean nextVr = vrRequested(player);
        boolean nextDesktop = !nextVr && desktopRequested(player);
        boolean nextVrEyePass = nextVr
                && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON
                && vrRuntime.isLocalPlayerEyePass();

        boolean modelChanged = nextHandle != modelHandle;
        boolean enteredVr = nextVr && (!vrActive || modelChanged);
        boolean leftVr = vrActive && (!nextVr || modelChanged);
        boolean vrModeChanged = modelChanged || vrActive != nextVr;
        if (modelChanged) {
            ModelLease nextLease = models.acquire(model.key())
                    .orElseThrow(() -> new IllegalStateException("ready first-person model lost its lease"));
            disableTrackedModel();
            releaseModelLease();
            modelLease = nextLease;
            modelHandle = nextHandle;
        }

        if (modelChanged || desktopActive != nextDesktop) {
            modelPort.setFirstPersonMode(nextHandle, nextDesktop);
            logFirstPersonInfo(nextHandle, nextDesktop);
        }
        if (vrModeChanged) {
            vrRuntime.setModelVrEnabled(nextHandle, nextVr);
        }

        Vec3 modelOrigin = nextVr
                ? renderOrigin(player, partialTick).add(localVrModelRootOffset(player))
                : FirstPersonCamera.interpolatedOrigin(player, partialTick);
        float modelYawDegrees = nextVr
                ? bodyYawDegrees(player, partialTick)
                : net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        model.setModelPositionAndYaw(
                (float) modelOrigin.x * 0.09F,
                (float) modelOrigin.y * 0.09F,
                (float) modelOrigin.z * 0.09F,
                (float) Math.toRadians(modelYawDegrees));

        desktopActive = nextDesktop;
        vrActive = nextVr;
        vrEyePassActive = nextVrEyePass;
        modelScale = Float.isFinite(effectiveModelScale) && effectiveModelScale > 0.0F
                ? effectiveModelScale : 1.0F;
        if (vrModeChanged) {
            vrRuntime.applyMmdRenderState(nextVr);
        }
        if (nextVr) {
            vrRuntime.updateModelVr(nextHandle, player, partialTick,
                    ConfigManager.getVRArmIKStrength(), localVrModelRootOffset(player));
        } else {
            clearVrRootOffset();
        }
        return new PreparedModel(nextVr, enteredVr, leftVr);
    }

    public void captureUpdatedEyeAnchor(MmdModelInstance model, Player player, float partialTick) {
        if (model == null || model.nativeHandle() != modelHandle) {
            return;
        }
        modelPort.getEyeBonePosition(modelHandle, eyeBonePosition);
        eyeBoneValid = isNonZeroFinite(eyeBonePosition);
        if (vrActive && eyeBoneValid) {
            updateVrRootOffset(player, partialTick);
        }
    }

    public boolean desktopActive() {
        reconcileLiveState();
        return desktopActive;
    }

    public boolean eyeCameraActive() {
        reconcileLiveState();
        return desktopActive || vrEyePassActive;
    }

    public boolean vrEyePassActive() {
        reconcileLiveState();
        return vrEyePassActive;
    }

    public boolean eyeBoneValid() {
        return eyeBoneValid;
    }

    public Vec3 scaledEyeBoneOffset() {
        float scale = 0.09F * modelScale;
        return new Vec3(
                eyeBonePosition[0] * scale,
                eyeBonePosition[1] * scale,
                eyeBonePosition[2] * scale);
    }

    public Vec3 localVrModelRootOffset(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.player == null
                || !minecraft.player.getUUID().equals(player.getUUID())) {
            return Vec3.ZERO;
        }
        return vrModelRootOffsetValid ? vrModelRootOffset : Vec3.ZERO;
    }

    public VrRuntimePort vrRuntime() {
        return vrRuntime;
    }

    public void reset() {
        boolean hadActiveState = modelHandle != 0L || desktopActive || vrActive || vrEyePassActive;
        disableTrackedModel();
        releaseModelLease();
        modelHandle = 0L;
        desktopActive = false;
        vrActive = false;
        vrEyePassActive = false;
        modelScale = 1.0F;
        clearEyeAnchor();
        clearVrRootOffset();
        if (hadActiveState) {
            vrRuntime.applyMmdRenderState(false);
        }
    }

    @Override
    public void close() {
        reset();
    }

    private boolean desktopRequested(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return isLocalPlayer(minecraft, player)
                && ConfigManager.isFirstPersonModelEnabled()
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON
                && MmdSkinRendererPlayerHelper.isUsingMmdModel(player);
    }

    private boolean vrRequested(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return isLocalPlayer(minecraft, player)
                && ConfigManager.isVREnabled()
                && vrRuntime.isLocalPlayerInVr()
                && MmdSkinRendererPlayerHelper.isUsingMmdModel(player);
    }

    private void reconcileLiveState() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        boolean desktopStillRequested = desktopRequested(player);
        boolean vrStillRequested = vrRequested(player);
        boolean vrEyeStillRequested = vrStillRequested
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON
                && vrRuntime.isLocalPlayerEyePass();
        if (desktopStillRequested || vrEyeStillRequested) {
            return;
        }
        if (vrStillRequested) {
            desktopActive = false;
            vrEyePassActive = false;
            return;
        }
        if (modelHandle == 0L && !desktopActive && !vrActive && !vrEyePassActive) {
            return;
        }
        reset();
    }

    private void updateVrRootOffset(Player player, float partialTick) {
        Vec3 head = vrRuntime.getWorldRenderHeadPosition(player);
        if (head == null) {
            return;
        }
        Vec3 origin = renderOrigin(player, partialTick).add(localVrModelRootOffset(player));
        float yaw = bodyYawDegrees(player, partialTick);
        Vec3 avatarEye = FirstPersonCamera.rotateEyeOffset(origin, yaw, scaledEyeBoneOffset());
        double correctedY = Math.clamp(
                vrModelRootOffset.y + head.y - avatarEye.y,
                -MAX_VR_ROOT_CORRECTION,
                MAX_VR_ROOT_CORRECTION);
        vrModelRootOffset = new Vec3(0.0D, correctedY, 0.0D);
        vrModelRootOffsetValid = true;
    }

    Vec3 renderOrigin(Player player, float partialTick) {
        Vec3 vrOrigin = vrRuntime.getRenderOrigin(player, partialTick);
        if (vrOrigin != null) {
            return vrOrigin;
        }
        return FirstPersonCamera.interpolatedOrigin(player, partialTick);
    }

    float bodyYawDegrees(Player player, float partialTick) {
        float vrYaw = vrRuntime.getBodyYawDegrees(player, partialTick);
        return Float.isFinite(vrYaw)
                ? vrYaw
                : net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
    }

    private void disableTrackedModel() {
        if (modelHandle == 0L) {
            return;
        }
        try {
            if (desktopActive) {
                modelPort.setFirstPersonMode(modelHandle, false);
            }
            if (vrActive) {
                vrRuntime.setModelVrEnabled(modelHandle, false);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("关闭本地第一人称模型状态失败: {}", modelHandle, exception);
        }
    }

    private void clearEyeAnchor() {
        eyeBonePosition[0] = 0.0F;
        eyeBonePosition[1] = 0.0F;
        eyeBonePosition[2] = 0.0F;
        eyeBoneValid = false;
    }

    private void clearVrRootOffset() {
        vrModelRootOffset = Vec3.ZERO;
        vrModelRootOffsetValid = false;
    }

    private void releaseModelLease() {
        if (modelLease == null) {
            return;
        }
        modelLease.close();
        modelLease = null;
    }

    private static boolean isLocalPlayer(Minecraft minecraft, Player player) {
        return player != null && minecraft.player != null
                && minecraft.player.getUUID().equals(player.getUUID());
    }

    private static boolean isNonZeroFinite(float[] value) {
        return Float.isFinite(value[0]) && Float.isFinite(value[1]) && Float.isFinite(value[2])
                && (value[0] != 0.0F || value[1] != 0.0F || value[2] != 0.0F);
    }

    public record PreparedModel(boolean vr, boolean enteredVr, boolean leftVr) {
    }
}
