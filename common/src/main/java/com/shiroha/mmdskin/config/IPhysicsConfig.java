/* 文件职责：定义物理系统可读取的全局配置。 */
package com.shiroha.mmdskin.config;

public interface IPhysicsConfig {
    default boolean isPhysicsEnabled() { return true; }

    default float getPhysicsGravityY() { return -98.0f; }

    default float getPhysicsFps() { return 60.0f; }

    default int getPhysicsMaxSubstepCount() { return 5; }

    default float getPhysicsInertiaStrength() { return 0.5f; }

    default float getPhysicsMaxLinearVelocity() { return 20.0f; }

    default float getPhysicsMaxAngularVelocity() { return 20.0f; }

    default boolean isPhysicsJointsEnabled() { return true; }

    default boolean isPhysicsKinematicFilter() { return false; }

    default boolean isPhysicsCollisionEnabled() { return true; }

    /** 0=STRICT, 1=STABLE(默认), 2=RELAXED（与 Rust CollisionStabilityMode 一致） */
    default int getPhysicsCollisionStabilityMode() { return 1; }

    default boolean isPhysicsDebugLog() { return false; }

    default int getMaxPhysicsModelsPerFrame() { return 10; }

    default float getPhysicsLodMaxDistance() { return 24.0f; }
}
