package com.shiroha.mmdskin.player.runtime;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 文件职责：验证第一人称与 VR 相机位置回退语义。 */
class FirstPersonManagerTest {
    @Test
    void shouldPreferVrHeadPositionWhenAvailable() throws Exception {
        Vec3 expected = new Vec3(1.0d, 2.0d, 3.0d);
        Vec3 fallback = new Vec3(10.0d, 20.0d, 30.0d);

        Vec3 actual = FirstPersonManager.resolveVrCameraPosition(expected, fallback);

        assertVec3Equals(expected, actual);
    }

    @Test
    void shouldFallBackToRotatedEyePositionWhenVrHeadPositionMissing() {
        Vec3 fallback = FirstPersonManager.resolveWorldEyePosition(
                new Vec3(10.0d, 20.0d, 30.0d),
                (float) Math.toRadians(90.0d),
                0.09f,
                0.18f,
                0.27f
        );

        Vec3 actual = FirstPersonManager.resolveVrCameraPosition(null, fallback);

        assertEquals(9.73d, actual.x, 1.0E-6d);
        assertEquals(20.18d, actual.y, 1.0E-6d);
        assertEquals(30.09d, actual.z, 1.0E-6d);
    }

    private static void assertVec3Equals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-6d);
        assertEquals(expected.y, actual.y, 1.0E-6d);
        assertEquals(expected.z, actual.z, 1.0E-6d);
    }
}
