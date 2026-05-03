package com.shiroha.mmdskin.config;

import java.util.Objects;

/** 文件职责：封装提交给 native 运行时的物理配置快照。 */
public record PhysicsConfigSnapshot(
        boolean enabled,
        float gravityY,
        float physicsFps,
        int maxSubstepCount,
        float inertiaStrength,
        float maxLinearVelocity,
        float maxAngularVelocity,
        boolean jointsEnabled,
        boolean kinematicFilter,
        boolean debugLog) {

    public static PhysicsConfigSnapshot from(ConfigData data) {
        Objects.requireNonNull(data, "data");
        return new PhysicsConfigSnapshot(
                data.physicsEnabled,
                data.physicsGravityY,
                data.physicsFps,
                data.physicsMaxSubstepCount,
                data.physicsInertiaStrength,
                data.physicsMaxLinearVelocity,
                data.physicsMaxAngularVelocity,
                data.physicsJointsEnabled,
                data.physicsKinematicFilter,
                data.physicsDebugLog);
    }
}
