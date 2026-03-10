package com.shiroha.mmdskin.renderer.runtime.model.helper;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * 眼球追踪工具类。
 */
public final class EyeTrackingHelper {

    private static final float MAX_EYE_ANGLE = 0.1745f;

    private static final float MIN_HORIZONTAL_DIST = 0.01f;

    private EyeTrackingHelper() {

    }

    public static void updateEyeTracking(NativeFunc nf, long modelHandle,
            LivingEntity entity, float entityYaw, float tickDelta, String modelName) {

        ModelConfigData modelConfig = ModelConfigManager.getConfig(modelName);

        if (!modelConfig.eyeTrackingEnabled) {
            nf.SetEyeTrackingEnabled(modelHandle, false);
            return;
        }

        float maxAngle = modelConfig.eyeMaxAngle;
        updateEyeTrackingInternal(nf, modelHandle, entity, entityYaw, tickDelta, maxAngle);
    }

    public static void updateEyeTracking(NativeFunc nf, long modelHandle,
            LivingEntity entity, float entityYaw, float tickDelta) {
        updateEyeTrackingInternal(nf, modelHandle, entity, entityYaw, tickDelta, MAX_EYE_ANGLE);
    }

    private static void updateEyeTrackingInternal(NativeFunc nf, long modelHandle,
            LivingEntity entity, float entityYaw, float tickDelta, float maxAngle) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) {
            return;
        }

        float camX = (float) mc.gameRenderer.getMainCamera().getPosition().x;
        float camY = (float) mc.gameRenderer.getMainCamera().getPosition().y;
        float camZ = (float) mc.gameRenderer.getMainCamera().getPosition().z;

        float eyeX = (float) Mth.lerp(tickDelta, entity.xo, entity.getX());
        float eyeY = (float) (Mth.lerp(tickDelta, entity.yo, entity.getY()) + entity.getEyeHeight());
        float eyeZ = (float) Mth.lerp(tickDelta, entity.zo, entity.getZ());

        float dx = camX - eyeX;
        float dy = camY - eyeY;
        float dz = camZ - eyeZ;

        float yawRad = entityYaw * ((float) Math.PI / 180F);
        float cosYaw = Mth.cos(yawRad);
        float sinYaw = Mth.sin(yawRad);

        float localX = dx * cosYaw + dz * sinYaw;
        float localY = dy;
        float localZ = -dx * sinYaw + dz * cosYaw;

        float horizontalDist = Mth.sqrt(localX * localX + localZ * localZ);

        float eyeAngleX = 0.0f;
        float eyeAngleY = 0.0f;

        if (horizontalDist > MIN_HORIZONTAL_DIST) {

            eyeAngleX = (float) Math.atan2(-localY, horizontalDist);

            eyeAngleY = (float) Math.atan2(localX, localZ);
        }

        eyeAngleX = Mth.clamp(eyeAngleX, -maxAngle, maxAngle);
        eyeAngleY = Mth.clamp(eyeAngleY, -maxAngle, maxAngle);

        nf.SetEyeTrackingEnabled(modelHandle, true);
        nf.SetEyeMaxAngle(modelHandle, maxAngle);
        nf.SetEyeAngle(modelHandle, eyeAngleX, eyeAngleY);
    }

    public static void disableEyeTracking(NativeFunc nf, long modelHandle) {
        nf.SetEyeTrackingEnabled(modelHandle, false);
    }
}
