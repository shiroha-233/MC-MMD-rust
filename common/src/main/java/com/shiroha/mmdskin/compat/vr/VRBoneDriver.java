package com.shiroha.mmdskin.compat.vr;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：将 VR 跟踪数据转换并驱动模型骨骼。 */
public final class VRBoneDriver {
    static final int TRACKING_POINT_STRIDE = 7;
    static final int TRACKING_PACKET_LENGTH = TRACKING_POINT_STRIDE * 3;

    private static final Logger LOGGER = LogManager.getLogger();
    private static final NativeModelPort NOOP_MODEL_PORT = new NativeModelPort() {
        @Override
        public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
            return false;
        }

        @Override
        public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
            return false;
        }

        @Override
        public long getModelMemoryUsage(long modelHandle) {
            return 0L;
        }

        @Override
        public void setFirstPersonMode(long modelHandle, boolean enabled) {
        }

        @Override
        public void getEyeBonePosition(long modelHandle, float[] output) {
        }

        @Override
        public void applyVrTrackingInput(long modelHandle, float[] trackingData) {
        }

        @Override
        public void setVrEnabled(long modelHandle, boolean enabled) {
        }

        @Override
        public void setVrIkParams(long modelHandle, float armIkStrength) {
        }

        @Override
        public int getMaterialCount(long modelHandle) {
            return 0;
        }

        @Override
        public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
        }

        @Override
        public void setAllMaterialsVisible(long modelHandle, boolean visible) {
        }

        @Override
        public void deleteModel(long modelHandle) {
        }
    };

    private static volatile NativeModelPort modelPort = NOOP_MODEL_PORT;

    private VRBoneDriver() {
    }

    public static void configureRuntimeCollaborators(NativeModelPort modelPort) {
        VRBoneDriver.modelPort = modelPort != null ? modelPort : NOOP_MODEL_PORT;
    }

    public static boolean isVRPlayer(Player player) {
        try {
            return VRDataProvider.isVRPlayer(player);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean driveModel(long modelHandle, Player player, float tickDelta) {
        if (modelHandle == 0 || player == null) {
            return false;
        }

        try {
            float[] worldData = VRDataProvider.getRenderTrackingData(player);
            if (!hasUsableTrackingData(worldData)) {
                return false;
            }

            Vec3 renderOrigin = VRDataProvider.getRenderOrigin(player, tickDelta)
                    .add(FirstPersonManager.getLocalVrModelRootOffset(player));
            if (!isFiniteVec3(renderOrigin)) {
                LOGGER.debug("Skipped VR bone drive because render origin was invalid");
                return false;
            }
            float px = (float) renderOrigin.x;
            float py = (float) renderOrigin.y;
            float pz = (float) renderOrigin.z;

            float yawRad = VRDataProvider.getBodyYawRad(player, tickDelta);
            if (!Float.isFinite(yawRad)) {
                LOGGER.debug("Skipped VR bone drive because body yaw was invalid");
                return false;
            }
            float cosY = Mth.cos(yawRad);
            float sinY = Mth.sin(yawRad);

            float[] localTracking = new float[TRACKING_PACKET_LENGTH];
            transformWorldTrackingToPlayerLocal(worldData, px, py, pz, cosY, sinY, localTracking);
            if (!hasUsableTrackingData(localTracking)) {
                LOGGER.debug("Skipped VR bone drive because transformed tracking packet became invalid");
                return false;
            }

            modelPort.applyVrTrackingInput(modelHandle, localTracking);
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
            int off = i * TRACKING_POINT_STRIDE;

            float dx = worldData[off] - px;
            float dy = worldData[off + 1] - py;
            float dz = worldData[off + 2] - pz;

            localTracking[off] = cosY * dx + sinY * dz;
            localTracking[off + 1] = dy;
            localTracking[off + 2] = -sinY * dx + cosY * dz;

            transformRotation(worldData, off + 3, localTracking, off + 3, cosY, sinY);
        }
    }

    static boolean hasUsableTrackingData(float[] trackingData) {
        if (trackingData == null || trackingData.length < TRACKING_PACKET_LENGTH) {
            return false;
        }
        return isUsableTrackingPoint(trackingData, 0)
                && (isUsableTrackingPoint(trackingData, TRACKING_POINT_STRIDE)
                || isUsableTrackingPoint(trackingData, TRACKING_POINT_STRIDE * 2));
    }

    private static boolean isUsableTrackingPoint(float[] trackingData, int offset) {
        return isFiniteTrackingSegment(trackingData, offset)
                && isFiniteQuaternion(trackingData, offset + 3)
                && quaternionLengthSquared(trackingData, offset + 3) > 1.0e-6f;
    }

    private static boolean isFiniteTrackingSegment(float[] trackingData, int offset) {
        for (int i = 0; i < TRACKING_POINT_STRIDE; i++) {
            if (!Float.isFinite(trackingData[offset + i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFiniteQuaternion(float[] trackingData, int offset) {
        return Float.isFinite(trackingData[offset])
                && Float.isFinite(trackingData[offset + 1])
                && Float.isFinite(trackingData[offset + 2])
                && Float.isFinite(trackingData[offset + 3]);
    }

    private static float quaternionLengthSquared(float[] trackingData, int offset) {
        float qx = trackingData[offset];
        float qy = trackingData[offset + 1];
        float qz = trackingData[offset + 2];
        float qw = trackingData[offset + 3];
        return qx * qx + qy * qy + qz * qz + qw * qw;
    }

    private static boolean isFiniteVec3(Vec3 vec3) {
        return vec3 != null
                && Double.isFinite(vec3.x)
                && Double.isFinite(vec3.y)
                && Double.isFinite(vec3.z);
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
            modelPort.setVrEnabled(modelHandle, enabled);
        } catch (Exception e) {
            LOGGER.debug("Failed to set VR mode", e);
        }
    }

    public static void setVRIKParams(long modelHandle, float armIKStrength) {
        if (modelHandle == 0 || !Float.isFinite(armIKStrength)) {
            return;
        }
        try {
            modelPort.setVrIkParams(modelHandle, armIKStrength);
        } catch (Exception e) {
            LOGGER.debug("Failed to set VR IK params", e);
        }
    }
}
