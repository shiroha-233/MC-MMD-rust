package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.compat.vr.DefaultVrRuntimePort;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.player.port.VrRuntimePort;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 文件职责：维护本地第一人称与 VR 视角相关的模型状态。
 */
public final class FirstPersonManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float MODEL_SCALE = 0.09f;

    private static float cachedModelScale = 1.0f;
    private static boolean activeDesktopFirstPerson = false;
    private static boolean activeVrEyeCamera = false;
    private static long trackedModelHandle = 0;
    private static final float[] eyeBonePos = new float[3];
    private static boolean eyeBoneValid = false;
    private static Vec3 vrModelRootOffset = Vec3.ZERO;
    private static boolean vrModelRootOffsetValid = false;
    private static Vec3 lastCameraPos = Vec3.ZERO;
    private static volatile VrRuntimePort vrRuntimePort = new DefaultVrRuntimePort();

    private FirstPersonManager() {
    }

    public static void setLastCameraPos(Vec3 pos) {
        lastCameraPos = pos;
    }

    public static Vec3 getLastCameraPos() {
        return lastCameraPos;
    }

    public static void configureVrRuntime(VrRuntimePort vrRuntimePort) {
        FirstPersonManager.vrRuntimePort = vrRuntimePort != null ? vrRuntimePort : VrRuntimePort.noop();
    }

    public static VrRuntimePort vrRuntime() {
        return vrRuntimePort;
    }

    public static boolean shouldRenderFirstPerson() {
        if (isLocalVrMmdModelActive()) {
            return false;
        }
        if (!ConfigManager.isFirstPersonModelEnabled()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            return false;
        }

        return mc.player != null && MmdSkinRendererPlayerHelper.isUsingMmdModel(mc.player);
    }

    public static void preRender(NativeFunc nf, long modelHandle, float modelScale, boolean isLocalPlayer) {
        if (!isLocalPlayer) {
            return;
        }

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        boolean vrEyeCamera = vrModelActive && isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
        boolean shouldEnableNativeFirstPerson = desktopFirstPerson;

        if (modelHandle != trackedModelHandle) {
            if (trackedModelHandle != 0 && activeDesktopFirstPerson) {
                nf.SetFirstPersonMode(trackedModelHandle, false);
            }
            trackedModelHandle = modelHandle;
            activeDesktopFirstPerson = false;
        }

        if (shouldEnableNativeFirstPerson != activeDesktopFirstPerson) {
            nf.SetFirstPersonMode(modelHandle, shouldEnableNativeFirstPerson);
            activeDesktopFirstPerson = shouldEnableNativeFirstPerson;
        }

        activeVrEyeCamera = vrEyeCamera;
        if (desktopFirstPerson || vrModelActive) {
            cachedModelScale = modelScale;
        }
    }

    public static void postRender(NativeFunc nf, long modelHandle, Player player, float tickDelta) {
        nf.GetEyeBonePosition(modelHandle, eyeBonePos);
        eyeBoneValid = eyeBonePos[0] != 0.0f || eyeBonePos[1] != 0.0f || eyeBonePos[2] != 0.0f;
        updateVrModelRootOffset(player, tickDelta);
    }

    public static boolean isActive() {
        ensureActiveState();
        return activeDesktopFirstPerson;
    }

    public static boolean isEyeCameraActive() {
        ensureActiveState();
        return activeDesktopFirstPerson || activeVrEyeCamera;
    }

    public static boolean isVrEyeCameraActive() {
        ensureActiveState();
        return activeVrEyeCamera;
    }

    public static boolean isEyeBoneValid() {
        return eyeBoneValid;
    }

    public static Vec3 getLocalVrModelRootOffset(Player player) {
        if (player == null) {
            return Vec3.ZERO;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return Vec3.ZERO;
        }
        if (player == null || minecraft.player == null || !minecraft.player.getUUID().equals(player.getUUID())) {
            return Vec3.ZERO;
        }
        return vrModelRootOffsetValid ? vrModelRootOffset : Vec3.ZERO;
    }

    public static void getEyeWorldOffset(float[] out) {
        float scale = MODEL_SCALE * cachedModelScale;
        out[0] = eyeBonePos[0] * scale;
        out[1] = eyeBonePos[1] * scale;
        out[2] = eyeBonePos[2] * scale;
    }

    public static Vec3 getRotatedEyePosition(Entity entity, float partialTick) {
        float[] eyeOffset = new float[3];
        getEyeWorldOffset(eyeOffset);
        Vec3 renderOrigin = getVanillaRenderOrigin(entity, partialTick);

        float bodyYaw = entity instanceof LivingEntity livingEntity
                ? Mth.rotLerp(partialTick, livingEntity.yBodyRotO, livingEntity.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        return resolveDesktopEyePosition(renderOrigin, bodyYaw, eyeOffset[0], eyeOffset[1], eyeOffset[2]);
    }

    public static Vec3 getVrCameraPosition(Entity entity, float partialTick) {
        if (!(entity instanceof Player player)) {
            return getRotatedEyePosition(entity, partialTick);
        }

        Vec3 headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        return resolveVrCameraPosition(headRenderPos, getVrRotatedEyePosition(player, partialTick));
    }

    public static Vec3 getVanillaEyePosition(LivingEntity entity, float partialTick) {
        double px = Mth.lerp(partialTick, entity.xo, entity.getX());
        double py = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double pz = Mth.lerp(partialTick, entity.zo, entity.getZ());
        return new Vec3(px, py, pz);
    }

    public static boolean shouldUseVanillaReachValidation(LivingEntity entity) {
        if (!isActive() || !isEyeBoneValid()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(entity.getUUID())) {
            return false;
        }
        return mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    public static void reset() {
        disableTrackedModel();
        activeDesktopFirstPerson = false;
        activeVrEyeCamera = false;
        trackedModelHandle = 0;
        cachedModelScale = 1.0f;
        clearEyeBoneState();
        clearVrModelRootOffset();
        lastCameraPos = Vec3.ZERO;
    }

    private static void ensureActiveState() {
        if (!activeDesktopFirstPerson && !activeVrEyeCamera) {
            return;
        }

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        boolean vrEyeCamera = vrModelActive && isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
        if (desktopFirstPerson || vrEyeCamera) {
            return;
        }

        if (vrModelActive) {
            activeDesktopFirstPerson = false;
            activeVrEyeCamera = false;
            lastCameraPos = Vec3.ZERO;
            return;
        }

        reset();
    }

    private static boolean isLocalVrMmdModelActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return ConfigManager.isVREnabled()
                && minecraft.player != null
                && vrRuntimePort.isLocalPlayerInVr()
                && MmdSkinRendererPlayerHelper.isUsingMmdModel(minecraft.player);
    }

    private static boolean isVrFirstPersonRequested() {
        return Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
    }

    private static Vec3 fallbackRenderOrigin(Player player, float tickDelta) {
        Vec3 renderOrigin = vrRuntimePort.getRenderOrigin(player, tickDelta);
        if (renderOrigin != null) {
            return renderOrigin;
        }
        return new Vec3(
                Mth.lerp(tickDelta, player.xo, player.getX()),
                Mth.lerp(tickDelta, player.yo, player.getY()),
                Mth.lerp(tickDelta, player.zo, player.getZ())
        );
    }

    private static float fallbackBodyYawDegrees(Player player, float tickDelta) {
        float bodyYaw = vrRuntimePort.getBodyYawDegrees(player, tickDelta);
        if (Float.isFinite(bodyYaw)) {
            return bodyYaw;
        }
        return Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
    }

    private static void updateVrModelRootOffset(Player player, float tickDelta) {
        if (player == null || !eyeBoneValid || !isLocalVrMmdModelActive()) {
            return;
        }

        Vec3 headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        if (headRenderPos == null) {
            return;
        }

        Vec3 avatarEyePos = getRotatedEyePositionFallback(player, tickDelta);
        double correctedY = Mth.clamp(vrModelRootOffset.y + (headRenderPos.y - avatarEyePos.y), -2.5d, 2.5d);
        vrModelRootOffset = new Vec3(0.0d, correctedY, 0.0d);
        vrModelRootOffsetValid = true;
    }

    private static Vec3 getRotatedEyePositionFallback(Player player, float partialTick) {
        float[] eyeOffset = new float[3];
        getEyeWorldOffset(eyeOffset);
        Vec3 renderOrigin = fallbackRenderOrigin(player, partialTick);
        float yawRad = (float) Math.toRadians(fallbackBodyYawDegrees(player, partialTick));
        return resolveWorldEyePosition(renderOrigin, yawRad, eyeOffset[0], eyeOffset[1], eyeOffset[2]);
    }

    private static Vec3 getVrRotatedEyePosition(Player player, float partialTick) {
        float[] eyeOffset = new float[3];
        getEyeWorldOffset(eyeOffset);
        Vec3 renderOrigin = fallbackRenderOrigin(player, partialTick).add(getLocalVrModelRootOffset(player));
        float yawRad = (float) Math.toRadians(fallbackBodyYawDegrees(player, partialTick));
        return resolveWorldEyePosition(renderOrigin, yawRad, eyeOffset[0], eyeOffset[1], eyeOffset[2]);
    }

    private static Vec3 getVanillaRenderOrigin(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()),
                Mth.lerp(partialTick, entity.zo, entity.getZ())
        );
    }

    static Vec3 resolveVrCameraPosition(Vec3 headRenderPos, Vec3 fallbackEyePos) {
        return headRenderPos != null ? headRenderPos : fallbackEyePos;
    }

    static Vec3 resolveDesktopEyePosition(Vec3 renderOrigin,
                                          float bodyYawDegrees,
                                          float eyeOffsetX,
                                          float eyeOffsetY,
                                          float eyeOffsetZ) {
        return resolveWorldEyePosition(
                renderOrigin,
                (float) Math.toRadians(bodyYawDegrees),
                eyeOffsetX,
                eyeOffsetY,
                eyeOffsetZ
        );
    }

    static Vec3 resolveWorldEyePosition(Vec3 renderOrigin,
                                        float bodyYawRad,
                                        float eyeOffsetX,
                                        float eyeOffsetY,
                                        float eyeOffsetZ) {
        double sinYaw = Math.sin(bodyYawRad);
        double cosYaw = Math.cos(bodyYawRad);
        double worldOffX = eyeOffsetX * cosYaw - eyeOffsetZ * sinYaw;
        double worldOffZ = eyeOffsetX * sinYaw + eyeOffsetZ * cosYaw;
        return new Vec3(renderOrigin.x + worldOffX, renderOrigin.y + eyeOffsetY, renderOrigin.z + worldOffZ);
    }

    private static void disableTrackedModel() {
        if (!activeDesktopFirstPerson || trackedModelHandle == 0) {
            return;
        }
        try {
            NativeFunc.GetInst().SetFirstPersonMode(trackedModelHandle, false);
        } catch (Exception e) {
            LOGGER.warn("Failed to disable first-person mode for model {}", trackedModelHandle, e);
        }
    }

    private static void clearEyeBoneState() {
        eyeBonePos[0] = 0.0f;
        eyeBonePos[1] = 0.0f;
        eyeBonePos[2] = 0.0f;
        eyeBoneValid = false;
    }

    private static void clearVrModelRootOffset() {
        vrModelRootOffset = Vec3.ZERO;
        vrModelRootOffsetValid = false;
    }
}
