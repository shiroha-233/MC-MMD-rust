package com.shiroha.mmdskin.compat.vr;

import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;

import java.lang.reflect.Method;

/** 文件职责：把 Vivecraft pose 解析为 MMD 所需的 tracking 数据包。 */
final class VivecraftTrackingDataReader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float EPSILON = 1.0e-4f;

    private final Method vrPoseGetHeadMethod;
    private final Method vrPoseGetMainHandMethod;
    private final Method vrPoseGetOffHandMethod;
    private final Method vrPoseIsLeftHandedMethod;
    private final Method vrBodyPartDataGetPosMethod;
    private final Method vrBodyPartDataGetRotationMethod;

    private boolean loggedMissingTracking;
    private String lastTrackingSource;

    VivecraftTrackingDataReader(Method vrPoseGetHeadMethod,
                                Method vrPoseGetMainHandMethod,
                                Method vrPoseGetOffHandMethod,
                                Method vrPoseIsLeftHandedMethod,
                                Method vrBodyPartDataGetPosMethod,
                                Method vrBodyPartDataGetRotationMethod) {
        this.vrPoseGetHeadMethod = vrPoseGetHeadMethod;
        this.vrPoseGetMainHandMethod = vrPoseGetMainHandMethod;
        this.vrPoseGetOffHandMethod = vrPoseGetOffHandMethod;
        this.vrPoseIsLeftHandedMethod = vrPoseIsLeftHandedMethod;
        this.vrBodyPartDataGetPosMethod = vrBodyPartDataGetPosMethod;
        this.vrBodyPartDataGetRotationMethod = vrBodyPartDataGetRotationMethod;
    }

    Vec3 extractHeadPosition(Object pose) throws Exception {
        if (pose == null) {
            return null;
        }

        Object head = vrPoseGetHeadMethod.invoke(pose);
        if (head == null) {
            return null;
        }
        return (Vec3) vrBodyPartDataGetPosMethod.invoke(head);
    }

    float[] poseToTrackingPacket(Object pose) throws Exception {
        float[] data = new float[21];

        Object head = vrPoseGetHeadMethod.invoke(pose);
        Object mainHand = vrPoseGetMainHandMethod.invoke(pose);
        Object offHand = vrPoseGetOffHandMethod.invoke(pose);
        boolean leftHanded = (boolean) vrPoseIsLeftHandedMethod.invoke(pose);

        Object rightHand = leftHanded ? offHand : mainHand;
        Object leftHand = leftHanded ? mainHand : offHand;

        writeTrackingPoint(head, data, 0);
        writeTrackingPoint(rightHand, data, 7);
        writeTrackingPoint(leftHand, data, 14);
        return data;
    }

    boolean isPoseUsable(Object pose) throws Exception {
        if (pose == null) {
            return false;
        }
        return isPacketUsable(poseToTrackingPacket(pose));
    }

    boolean isPacketUsable(float[] data) {
        return isPointUsable(data, 0) && (isPointUsable(data, 7) || isPointUsable(data, 14));
    }

    String lastTrackingSource() {
        return lastTrackingSource;
    }

    void recordTrackingSource(String source) {
        this.lastTrackingSource = source;
    }

    void clearMissingTrackingFlag() {
        this.loggedMissingTracking = false;
    }

    void logMissingTracking(String reason) {
        if (!loggedMissingTracking) {
            LOGGER.warn("Vivecraft VR is active but no usable tracking pose was available ({})", reason);
            loggedMissingTracking = true;
        }
    }

    private boolean isPointUsable(float[] data, int offset) {
        float px = Math.abs(data[offset]);
        float py = Math.abs(data[offset + 1]);
        float pz = Math.abs(data[offset + 2]);
        float qx = Math.abs(data[offset + 3]);
        float qy = Math.abs(data[offset + 4]);
        float qz = Math.abs(data[offset + 5]);
        float qw = Math.abs(data[offset + 6]);

        boolean hasPosition = px > EPSILON || py > EPSILON || pz > EPSILON;
        boolean hasRotation = qx > EPSILON || qy > EPSILON || qz > EPSILON || Math.abs(qw - 1.0f) > EPSILON;
        return hasPosition || hasRotation;
    }

    private void writeTrackingPoint(Object bodyPartData, float[] out, int offset) throws Exception {
        if (bodyPartData == null) {
            return;
        }

        Vec3 pos = (Vec3) vrBodyPartDataGetPosMethod.invoke(bodyPartData);
        out[offset] = (float) pos.x;
        out[offset + 1] = (float) pos.y;
        out[offset + 2] = (float) pos.z;

        Quaternionf rotation = new Quaternionf();
        rotation.set((org.joml.Quaternionfc) vrBodyPartDataGetRotationMethod.invoke(bodyPartData));
        rotation.normalize();
        out[offset + 3] = rotation.x;
        out[offset + 4] = rotation.y;
        out[offset + 5] = rotation.z;
        out[offset + 6] = rotation.w;
    }
}
