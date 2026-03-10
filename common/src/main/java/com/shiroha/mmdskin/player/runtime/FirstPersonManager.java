package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 第一人称模型管理器。
 */
public final class FirstPersonManager {
    private static final Logger logger = LogManager.getLogger();

    private static final float MODEL_SCALE = 0.09f;

    private static float cachedModelScale = 1.0f;

    private static boolean activeFirstPerson = false;

    private static long trackedModelHandle = 0;

    private static final float[] eyeBonePos = new float[3];

    private static boolean eyeBoneValid = false;

    private static Vec3 lastCameraPos = Vec3.ZERO;

    private FirstPersonManager() {}

    public static void setLastCameraPos(Vec3 pos) {
        lastCameraPos = pos;
    }

    public static Vec3 getLastCameraPos() {
        return lastCameraPos;
    }

    public static boolean shouldRenderFirstPerson() {
        if (!ConfigManager.isFirstPersonModelEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return false;

        if (mc.player != null && !MmdSkinRendererPlayerHelper.isUsingMmdModel(mc.player)) {
            return false;
        }

        return true;
    }

    public static void preRender(NativeFunc nf, long modelHandle, float modelScale, boolean isLocalPlayer) {

        if (!isLocalPlayer) return;

        boolean shouldEnable = shouldRenderFirstPerson();

        if (modelHandle != trackedModelHandle) {
            if (trackedModelHandle != 0 && activeFirstPerson) {
                nf.SetFirstPersonMode(trackedModelHandle, false);
            }
            trackedModelHandle = modelHandle;
            activeFirstPerson = false;
        }

        if (shouldEnable != activeFirstPerson) {
            nf.SetFirstPersonMode(modelHandle, shouldEnable);
            activeFirstPerson = shouldEnable;
        }

        if (shouldEnable) {
            cachedModelScale = modelScale;
        }
    }

    public static void postRender(NativeFunc nf, long modelHandle) {
        nf.GetEyeBonePosition(modelHandle, eyeBonePos);
        eyeBoneValid = (eyeBonePos[0] != 0.0f || eyeBonePos[1] != 0.0f || eyeBonePos[2] != 0.0f);
    }

    public static boolean isActive() {
        ensureActiveState();
        return activeFirstPerson;
    }

    public static boolean isEyeBoneValid() {
        return eyeBoneValid;
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
        double px = Mth.lerp(partialTick, entity.xo, entity.getX());
        double py = Mth.lerp(partialTick, entity.yo, entity.getY());
        double pz = Mth.lerp(partialTick, entity.zo, entity.getZ());

        float bodyYaw = entity instanceof LivingEntity le
                ? Mth.rotLerp(partialTick, le.yBodyRotO, le.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float yawRad = (float) Math.toRadians(bodyYaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double worldOffX = eyeOffset[0] * cosYaw - eyeOffset[2] * sinYaw;
        double worldOffZ = eyeOffset[0] * sinYaw + eyeOffset[2] * cosYaw;
        return new Vec3(px + worldOffX, py + eyeOffset[1], pz + worldOffZ);
    }

    public static void reset() {
        disableTrackedModel();
        activeFirstPerson = false;
        trackedModelHandle = 0;
        cachedModelScale = 1.0f;
        eyeBonePos[0] = 0.0f;
        eyeBonePos[1] = 0.0f;
        eyeBonePos[2] = 0.0f;
        eyeBoneValid = false;
        lastCameraPos = Vec3.ZERO;
    }

    private static void ensureActiveState() {
        if (!activeFirstPerson) {
            return;
        }

        if (shouldRenderFirstPerson()) {
            return;
        }

        disableTrackedModel();
        activeFirstPerson = false;
        trackedModelHandle = 0;
        cachedModelScale = 1.0f;
        eyeBonePos[0] = 0.0f;
        eyeBonePos[1] = 0.0f;
        eyeBonePos[2] = 0.0f;
        eyeBoneValid = false;
        lastCameraPos = Vec3.ZERO;
    }

    private static void disableTrackedModel() {
        if (!activeFirstPerson || trackedModelHandle == 0) {
            return;
        }

        try {
            NativeFunc.GetInst().SetFirstPersonMode(trackedModelHandle, false);
        } catch (Exception e) {
            logger.warn("Failed to disable first-person mode for model {}", trackedModelHandle, e);
        }
    }
}
