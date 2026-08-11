package com.shiroha.mmdskin.compat.tacz;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczThirdPersonGunTransformTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    void appliesOnlyToMainHandTaczGun() {
        assertTrue(TaczThirdPersonGunTransform.shouldApply(true, true));
        assertFalse(TaczThirdPersonGunTransform.shouldApply(false, true));
        assertFalse(TaczThirdPersonGunTransform.shouldApply(true, false));
        assertFalse(TaczThirdPersonGunTransform.shouldApply(false, false));
    }

    @Test
    void rotatesPositiveFortyFiveDegreesAroundLocalZ() {
        Quaternionf expected = new Quaternionf().rotateZ((float) Math.toRadians(45.0));
        Quaternionf actual = TaczThirdPersonGunTransform.correction();

        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
        assertEquals(expected.w, actual.w, EPSILON);
    }
}
