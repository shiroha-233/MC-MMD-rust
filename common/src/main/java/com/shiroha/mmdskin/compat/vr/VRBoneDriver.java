package com.shiroha.mmdskin.compat.vr;

import com.shiroha.mmdskin.NativeFunc;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * VR 骨骼驱动层（SRP：将 VR 追踪数据转换到模型空间并传递给 Rust IK）
 */

public final class VRBoneDriver {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final float MODEL_SCALE = 0.09f;

    private VRBoneDriver() {}

    public static boolean isVRPlayer(Player player) {
        try {
            return VRDataProvider.isVRPlayer(player);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean driveModel(long modelHandle, Player player, float tickDelta) {
        if (modelHandle == 0) return false;
        try {
            float[] worldData = VRDataProvider.getRenderTrackingData(player);
            if (worldData == null) return false;

            float px = (float) Mth.lerp(tickDelta, player.xo, player.getX());
            float py = (float) Mth.lerp(tickDelta, player.yo, player.getY());
            float pz = (float) Mth.lerp(tickDelta, player.zo, player.getZ());

            float bodyYaw = Mth.lerp(tickDelta, player.yBodyRotO, player.yBodyRot);
            float yawRad = bodyYaw * ((float) Math.PI / 180F);
            float cosY = Mth.cos(yawRad);
            float sinY = Mth.sin(yawRad);

            float[] localData = new float[21];
            for (int i = 0; i < 3; i++) {
                int off = i * 7;

                float dx = worldData[off]     - px;
                float dy = worldData[off + 1] - py;
                float dz = worldData[off + 2] - pz;

                float lx =  cosY * dx + sinY * dz;
                float lz = -sinY * dx + cosY * dz;
                localData[off]     = lx / MODEL_SCALE;
                localData[off + 1] = dy / MODEL_SCALE;
                localData[off + 2] = lz / MODEL_SCALE;

                transformRotation(worldData, off + 3, localData, off + 3, cosY, sinY);
            }

            NativeFunc.GetInst().SetVRTrackingData(modelHandle, localData);
            return true;
        } catch (Exception e) {
            LOGGER.debug("VR 骨骼驱动异常", e);
            return false;
        }
    }

    private static void transformRotation(float[] src, int si,
                                           float[] dst, int di,
                                           float cosY, float sinY) {

        float cosH = (float) Math.sqrt((1.0f + cosY) * 0.5f);
        float sinH = (float) Math.sqrt(Math.max(0, (1.0f - cosY) * 0.5f));
        if (sinY > 0) sinH = -sinH;

        float qx = src[si], qy = src[si + 1], qz = src[si + 2], qw = src[si + 3];
        dst[di]     = cosH * qx + sinH * qz;
        dst[di + 1] = cosH * qy + sinH * qw;
        dst[di + 2] = cosH * qz - sinH * qx;
        dst[di + 3] = cosH * qw - sinH * qy;

        float len = (float) Math.sqrt(
            dst[di] * dst[di] + dst[di+1] * dst[di+1] +
            dst[di+2] * dst[di+2] + dst[di+3] * dst[di+3]);
        if (len > 1e-6f) {
            float inv = 1.0f / len;
            dst[di] *= inv; dst[di+1] *= inv;
            dst[di+2] *= inv; dst[di+3] *= inv;
        }
    }

    public static void setVREnabled(long modelHandle, boolean enabled) {
        if (modelHandle == 0) return;
        try {
            NativeFunc.GetInst().SetVREnabled(modelHandle, enabled);
        } catch (Exception e) {
            LOGGER.debug("设置 VR 模式异常", e);
        }
    }

    public static void setVRIKParams(long modelHandle, float armIKStrength) {
        if (modelHandle == 0) return;
        try {
            NativeFunc.GetInst().SetVRIKParams(modelHandle, armIKStrength);
        } catch (Exception e) {
            LOGGER.debug("设置 VR IK 参数异常", e);
        }
    }
}
