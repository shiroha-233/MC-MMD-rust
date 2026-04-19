package com.shiroha.mmdskin.compat.vr;

import com.shiroha.mmdskin.NativeFunc;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * VR 骨骼驱动层。
 */
public final class VRBoneDriver {

    private static final Logger LOGGER = LogManager.getLogger();

    private VRBoneDriver() {}

    public static boolean isVRPlayer(Player player) {
        try {
            return VRDataProvider.isVRPlayer(player);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean driveModel(long modelHandle, Player player, float tickDelta) {
        if (modelHandle == 0) {
            return false;
        }

        try {
            float[] worldData = VRDataProvider.getRenderTrackingData(player);
            if (worldData == null) {
                return false;
            }

            float px = (float) Mth.lerp(tickDelta, player.xo, player.getX());
            float py = (float) Mth.lerp(tickDelta, player.yo, player.getY());
            float pz = (float) Mth.lerp(tickDelta, player.zo, player.getZ());

            float yawRad = VRDataProvider.getBodyYawRad(player, tickDelta);
            float cosY = Mth.cos(yawRad);
            float sinY = Mth.sin(yawRad);

            float[] localTracking = new float[21];
            transformWorldTrackingToPlayerLocal(worldData, px, py, pz, cosY, sinY, localTracking);

            NativeFunc.GetInst().ApplyVRTrackingInput(modelHandle, localTracking);
            return true;
        } catch (Exception e) {
            LOGGER.debug("VR bone driving failed", e);
            return false;
        }
    }

    static void transformWorldTrackingToPlayerLocal(float[] worldData,
                                                    float px,
                                                    float py,
                                                    float pz,
                                                    float cosY,
                                                    float sinY,
                                                    float[] localTracking) {
        for (int i = 0; i < 3; i++) {
            int off = i * 7;

            float dx = worldData[off] - px;
            float dy = worldData[off + 1] - py;
            float dz = worldData[off + 2] - pz;

            localTracking[off] = cosY * dx + sinY * dz;
            localTracking[off + 1] = dy;
            localTracking[off + 2] = -sinY * dx + cosY * dz;

            transformRotation(worldData, off + 3, localTracking, off + 3, cosY, sinY);
        }
    }

    static void transformRotationToPlayerLocal(float[] src,
                                               int si,
                                               float[] dst,
                                               int di,
                                               float yawRad) {
        transformRotation(src, si, dst, di, Mth.cos(yawRad), Mth.sin(yawRad));
    }

    private static void transformRotation(float[] src, int si, float[] dst, int di, float cosY, float sinY) {
        float cosH = (float) Math.sqrt((1.0f + cosY) * 0.5f);
        float sinH = (float) Math.sqrt(Math.max(0.0f, (1.0f - cosY) * 0.5f));
        if (sinY < 0.0f) {
            sinH = -sinH;
        }

        float qx = src[si];
        float qy = src[si + 1];
        float qz = src[si + 2];
        float qw = src[si + 3];
        dst[di] = cosH * qx + sinH * qz;
        dst[di + 1] = cosH * qy + sinH * qw;
        dst[di + 2] = cosH * qz - sinH * qx;
        dst[di + 3] = cosH * qw - sinH * qy;

        float len = (float) Math.sqrt(
                dst[di] * dst[di] + dst[di + 1] * dst[di + 1]
                        + dst[di + 2] * dst[di + 2] + dst[di + 3] * dst[di + 3]);
        if (len > 1e-6f) {
            float inv = 1.0f / len;
            dst[di] *= inv;
            dst[di + 1] *= inv;
            dst[di + 2] *= inv;
            dst[di + 3] *= inv;
        }
    }

    public static void setVREnabled(long modelHandle, boolean enabled) {
        if (modelHandle == 0) {
            return;
        }
        try {
            NativeFunc.GetInst().SetVREnabled(modelHandle, enabled);
        } catch (Exception e) {
            LOGGER.debug("Failed to set VR mode", e);
        }
    }

    public static void setVRIKParams(long modelHandle, float armIKStrength) {
        if (modelHandle == 0) {
            return;
        }
        try {
            NativeFunc.GetInst().SetVRIKParams(modelHandle, armIKStrength);
        } catch (Exception e) {
            LOGGER.debug("Failed to set VR IK params", e);
        }
    }
}
