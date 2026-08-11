package com.shiroha.mmdskin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证实验性碰撞配置的默认值和快照传递。 */
class PhysicsConfigSnapshotTest {
    @Test
    void normalCollisionModeIsEnabledByDefault() {
        ConfigData data = new ConfigData();

        assertTrue(data.physicsCollisionEnabled);
        assertFalse(data.physicsKinematicFilter);
        assertTrue(PhysicsConfigSnapshot.from(data).collisionEnabled());
        assertFalse(PhysicsConfigSnapshot.from(data).kinematicFilter());
        assertEquals(PhysicsCollisionStabilityMode.STABLE,
                PhysicsConfigSnapshot.from(data).collisionStabilityMode());
    }

    @Test
    void collisionValueIsCopiedToSnapshot() {
        ConfigData data = new ConfigData();
        data.physicsCollisionEnabled = false;

        assertFalse(PhysicsConfigSnapshot.from(data).collisionEnabled());
    }
}
