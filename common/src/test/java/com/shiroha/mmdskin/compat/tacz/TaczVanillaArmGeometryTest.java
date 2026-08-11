package com.shiroha.mmdskin.compat.tacz;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaczVanillaArmGeometryTest {
    @Test
    void shouldAppendNormalArmPivotAndLengthInEntryLocalSpace() {
        Matrix4f entry = new Matrix4f().translate(1.0f, 2.0f, 3.0f);

        Matrix4f left = TaczVanillaArmGeometry.toWrist(
                entry, TaczFirstPersonFrameSnapshot.Hand.LEFT, false);
        Matrix4f right = TaczVanillaArmGeometry.toWrist(
                entry, TaczFirstPersonFrameSnapshot.Hand.RIGHT, false);

        assertEquals(1.3125f, left.m30(), 1.0e-6f);
        assertEquals(0.6875f, right.m30(), 1.0e-6f);
        assertEquals(2.875f, left.m31(), 1.0e-6f);
        assertEquals(3.0f, left.m32(), 1.0e-6f);
        assertEquals(1.0f, entry.m30(), 1.0e-6f);
    }

    @Test
    void shouldUseSlimArmPivotHeight() {
        Matrix4f wrist = TaczVanillaArmGeometry.toWrist(
                new Matrix4f(), TaczFirstPersonFrameSnapshot.Hand.RIGHT, true);

        assertEquals(-0.3125f, wrist.m30(), 1.0e-6f);
        assertEquals(0.90625f, wrist.m31(), 1.0e-6f);
    }

    @Test
    void shouldApplyOffsetAlongRotatedEntryAxes() {
        Matrix4f entry = new Matrix4f().rotateZ((float) Math.PI);

        Matrix4f wrist = TaczVanillaArmGeometry.toWrist(
                entry, TaczFirstPersonFrameSnapshot.Hand.LEFT, false);

        assertEquals(-0.3125f, wrist.m30(), 1.0e-6f);
        assertEquals(-0.875f, wrist.m31(), 1.0e-6f);
    }
}
