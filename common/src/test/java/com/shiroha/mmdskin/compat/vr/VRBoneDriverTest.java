package com.shiroha.mmdskin.compat.vr;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VRBoneDriverTest {

    private static final float EPSILON = 1.0e-4f;

    @Test
    void shouldConvertDiagonalWorldTrackingPositionsIntoModelSpace() {
        float yawRad = (float) (3.0 * Math.PI / 4.0);
        float px = 10.0f;
        float py = 64.0f;
        float pz = -3.0f;

        float cosY = Mth.cos(yawRad);
        float sinY = Mth.sin(yawRad);
        float[] worldTracking = new float[21];
        float[] localTracking = new float[21];

        float forwardX = -sinY;
        float forwardZ = cosY;
        float rightX = cosY;
        float rightZ = sinY;

        writeTrackingPoint(worldTracking, 0, px + forwardX, py, pz + forwardZ, -yawRad);
        writeTrackingPoint(worldTracking, 7, px + rightX, py, pz + rightZ, -yawRad);
        writeTrackingPoint(worldTracking, 14, px - rightX, py, pz - rightZ, -yawRad);

        VRBoneDriver.transformWorldTrackingToPlayerLocal(worldTracking, px, py, pz, cosY, sinY, localTracking);

        assertEquals(0.0f, localTracking[0], EPSILON);
        assertEquals(0.0f, localTracking[1], EPSILON);
        assertEquals(1.0f, localTracking[2], EPSILON);

        assertEquals(1.0f, localTracking[7], EPSILON);
        assertEquals(0.0f, localTracking[8], EPSILON);
        assertEquals(0.0f, localTracking[9], EPSILON);

        assertEquals(-1.0f, localTracking[14], EPSILON);
        assertEquals(0.0f, localTracking[15], EPSILON);
        assertEquals(0.0f, localTracking[16], EPSILON);
    }

    @Test
    void shouldTransformBodyAlignedDiagonalRotationIntoIdentity() {
        float yawRad = (float) (3.0 * Math.PI / 4.0);
        float[] src = new float[7];
        float[] dst = new float[7];

        writeTrackingPoint(src, 0, 0.0f, 0.0f, 0.0f, -yawRad);
        VRBoneDriver.transformRotationToPlayerLocal(src, 3, dst, 3, yawRad);

        assertEquals(0.0f, dst[3], EPSILON);
        assertEquals(0.0f, dst[4], EPSILON);
        assertEquals(0.0f, dst[5], EPSILON);
        assertEquals(1.0f, dst[6], EPSILON);
    }

    @Test
    void shouldRejectTrackingPacketsWithInvalidContract() {
        assertFalse(VRBoneDriver.hasUsableTrackingData(null));
        assertFalse(VRBoneDriver.hasUsableTrackingData(new float[VRBoneDriver.TRACKING_PACKET_LENGTH - 1]));

        float[] packetWithNaN = new float[VRBoneDriver.TRACKING_PACKET_LENGTH];
        writeTrackingPoint(packetWithNaN, 0, 0.0f, 1.0f, 0.0f, 0.0f);
        writeTrackingPoint(packetWithNaN, 7, 1.0f, 1.0f, 0.0f, 0.0f);
        packetWithNaN[10] = Float.NaN;
        assertFalse(VRBoneDriver.hasUsableTrackingData(packetWithNaN));
    }

    @Test
    void shouldAcceptTrackingPacketsWithFiniteHeadAndHandData() {
        float[] packet = new float[VRBoneDriver.TRACKING_PACKET_LENGTH];
        writeTrackingPoint(packet, 0, 0.0f, 1.6f, 0.0f, 0.0f);
        writeTrackingPoint(packet, 7, 0.2f, 1.2f, 0.1f, 0.3f);
        writeTrackingPoint(packet, 14, -0.2f, 1.2f, 0.1f, -0.3f);

        assertTrue(VRBoneDriver.hasUsableTrackingData(packet));
    }

    private static void writeTrackingPoint(float[] tracking,
                                           int offset,
                                           float x,
                                           float y,
                                           float z,
                                           float yawRad) {
        tracking[offset] = x;
        tracking[offset + 1] = y;
        tracking[offset + 2] = z;
        tracking[offset + 3] = 0.0f;
        tracking[offset + 4] = Mth.sin(yawRad * 0.5f);
        tracking[offset + 5] = 0.0f;
        tracking[offset + 6] = Mth.cos(yawRad * 0.5f);
    }
}
