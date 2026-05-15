package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.config.RuntimeConfigPortHolder;
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

/** 文件职责：维护本地第一人称与 VR 视角相关的模型状态。 */
public final class FirstPersonManager {
    private static final Logger logger = LogManager.getLogger();
    private static final float MODEL_SCALE = 0.09f;

    private static final NativeModelPort NOOP_MODEL_PORT = new NativeModelPort() {
        @Override public boolean setLayerBoneMask(long h, int l, String b) { return false; }
        @Override public boolean setLayerBoneExclude(long h, int l, String b) { return false; }
        @Override public long getModelMemoryUsage(long h) { return 0L; }
        @Override public void setFirstPersonMode(long h, boolean e) {}
        @Override public void getEyeBonePosition(long h, float[] o) {}
        @Override public void applyVrTrackingInput(long h, float[] d) {}
        @Override public void setVrEnabled(long h, boolean e) {}
        @Override public void setVrIkParams(long h, float s) {}
        @Override public int getMaterialCount(long h) { return 0; }
        @Override public void setMaterialVisible(long h, int i, boolean v) {}
        @Override public void setAllMaterialsVisible(long h, boolean v) {}
        @Override public void deleteModel(long h) {}
    };

    private static volatile NativeModelPort modelPort = NOOP_MODEL_PORT;
    private static volatile VrRuntimePort vrRuntimePort = VrRuntimePort.noop();

    private static float cachedModelScale = 1.0f;
    private static long trackedModelHandle = 0;
    private static boolean activeDesktopFirstPerson = false;
    private static boolean activeVrEyeCamera = false;
    private static final float[] eyeBonePos = new float[3];
    private static boolean eyeBoneValid = false;

    private static Vec3 vrModelRootOffset = Vec3.ZERO;
    private static boolean vrModelRootOffsetValid = false;
    private static Vec3 lastCameraPos = Vec3.ZERO;

    private FirstPersonManager() {}

    public static void configureRuntimeCollaborators(NativeModelPort port) {
        modelPort = port != null ? port : NOOP_MODEL_PORT;
    }

    public static void configureVrRuntime(VrRuntimePort vrRuntimePort) {
        FirstPersonManager.vrRuntimePort = vrRuntimePort != null ? vrRuntimePort : VrRuntimePort.noop();
    }

    public static VrRuntimePort vrRuntime() {
        return vrRuntimePort;
    }

    public static void setLastCameraPos(Vec3 pos) {
        lastCameraPos = pos;
    }

    public static Vec3 getLastCameraPos() {
        return lastCameraPos;
    }

    public static boolean shouldRenderFirstPerson() {
        if (isLocalVrMmdModelActive()) return false;
        if (!RuntimeConfigPortHolder.get().isFirstPersonModelEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return false;
        return mc.player != null && MmdSkinRendererPlayerHelper.isUsingMmdModel(mc.player);
    }

    public static void preRender(long modelHandle, float modelScale, boolean isLocalPlayer) {
        if (!isLocalPlayer) return;

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        boolean vrEyeCamera = vrModelActive && isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
        activeVrEyeCamera = vrEyeCamera;

        // 切换模型时先关闭旧模型的第一人称模式
        if (modelHandle != trackedModelHandle) {
            if (trackedModelHandle != 0 && activeDesktopFirstPerson) {
                disableTrackedModel();
            }
            trackedModelHandle = modelHandle;
            activeDesktopFirstPerson = false;
        }

        // 仅桌面第一人称需要驱动 native SetFirstPersonMode
        if (desktopFirstPerson != activeDesktopFirstPerson) {
            try {
                modelPort.setFirstPersonMode(modelHandle, desktopFirstPerson);
            } catch (Exception e) {
                logger.warn("SetFirstPersonMode failed for model {}", modelHandle, e);
            }
            activeDesktopFirstPerson = desktopFirstPerson;
        }

        if (desktopFirstPerson || vrModelActive) {
            cachedModelScale = modelScale;
        }
    }

    public static void postRender(long modelHandle, Player player, float tickDelta) {
        if (activeDesktopFirstPerson && modelHandle != 0) {
            try {
                modelPort.getEyeBonePosition(modelHandle, eyeBonePos);
                eyeBoneValid = (eyeBonePos[0] != 0.0f || eyeBonePos[1] != 0.0f || eyeBonePos[2] != 0.0f);
            } catch (Exception e) {
                logger.warn("GetEyeBonePosition failed for model {}", modelHandle, e);
                clearEyeBoneState();
            }
        } else {
            clearEyeBoneState();
        }
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
        Minecraft minecraft = Minecraft.getInstance();
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
        Vec3 renderOrigin = entity instanceof Player player
                ? fallbackRenderOrigin(player, partialTick)
                : new Vec3(
                        Mth.lerp(partialTick, entity.xo, entity.getX()),
                        Mth.lerp(partialTick, entity.yo, entity.getY()),
                        Mth.lerp(partialTick, entity.zo, entity.getZ())
                );
        if (entity instanceof Player player) {
            renderOrigin = renderOrigin.add(getLocalVrModelRootOffset(player));
        }
        double px = renderOrigin.x;
        double py = renderOrigin.y;
        double pz = renderOrigin.z;

        float bodyYaw = entity instanceof Player player
                ? fallbackBodyYawDegrees(player, partialTick)
                : entity instanceof LivingEntity livingEntity
                ? Mth.rotLerp(partialTick, livingEntity.yBodyRotO, livingEntity.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float yawRad = (float) Math.toRadians(bodyYaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double worldOffX = eyeOffset[0] * cosYaw - eyeOffset[2] * sinYaw;
        double worldOffZ = eyeOffset[0] * sinYaw + eyeOffset[2] * cosYaw;
        return new Vec3(px + worldOffX, py + eyeOffset[1], pz + worldOffZ);
    }

    public static Vec3 getVanillaEyePosition(LivingEntity entity, float partialTick) {
        double px = Mth.lerp(partialTick, entity.xo, entity.getX());
        double py = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double pz = Mth.lerp(partialTick, entity.zo, entity.getZ());
        return new Vec3(px, py, pz);
    }

    public static boolean shouldUseVanillaReachValidation(LivingEntity entity) {
        if (!isActive() || !isEyeBoneValid()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(entity.getUUID())) return false;
        return mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    public static Vec3 getVrCameraPosition(Entity entity, float partialTick) {
        if (!(entity instanceof Player player)) {
            return getRotatedEyePosition(entity, partialTick);
        }
        Vec3 headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        if (headRenderPos == null) {
            return getRotatedEyePosition(entity, partialTick);
        }
        return headRenderPos;
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
        if (!activeDesktopFirstPerson && !activeVrEyeCamera) return;

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        boolean vrEyeCamera = vrModelActive && isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
        if (desktopFirstPerson || vrEyeCamera) return;

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
        return minecraft.player != null
                && vrRuntimePort.isLocalPlayerInVr()
                && MmdSkinRendererPlayerHelper.isUsingMmdModel(minecraft.player);
    }

    private static boolean isVrFirstPersonRequested() {
        return Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
    }

    private static Vec3 fallbackRenderOrigin(Player player, float tickDelta) {
        Vec3 renderOrigin = vrRuntimePort.getRenderOrigin(player, tickDelta);
        if (renderOrigin != null) return renderOrigin;
        return new Vec3(
                Mth.lerp(tickDelta, player.xo, player.getX()),
                Mth.lerp(tickDelta, player.yo, player.getY()),
                Mth.lerp(tickDelta, player.zo, player.getZ())
        );
    }

    private static float fallbackBodyYawDegrees(Player player, float tickDelta) {
        float bodyYaw = vrRuntimePort.getBodyYawDegrees(player, tickDelta);
        if (Float.isFinite(bodyYaw)) return bodyYaw;
        return Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
    }

    private static void updateVrModelRootOffset(Player player, float tickDelta) {
        if (player == null || !eyeBoneValid || !isLocalVrMmdModelActive()) return;
        Vec3 headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        if (headRenderPos == null) return;
        Vec3 avatarEyePos = getRotatedEyePosition(player, tickDelta);
        double correctedY = Mth.clamp(vrModelRootOffset.y + (headRenderPos.y - avatarEyePos.y), -2.5d, 2.5d);
        vrModelRootOffset = new Vec3(0.0d, correctedY, 0.0d);
        vrModelRootOffsetValid = true;
    }

    private static void disableTrackedModel() {
        if (!activeDesktopFirstPerson || trackedModelHandle == 0) return;
        try {
            modelPort.setFirstPersonMode(trackedModelHandle, false);
        } catch (Exception e) {
            logger.warn("Failed to disable first-person mode for model {}", trackedModelHandle, e);
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
