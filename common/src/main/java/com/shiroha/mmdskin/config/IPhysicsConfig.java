package com.shiroha.mmdskin.config;

/**
 * 物理引擎配置子接口（Bullet3）
 */

public interface IPhysicsConfig {

    default boolean isPhysicsEnabled() { return true; }

    default float getPhysicsGravityY() { return -98.0f; }

    default float getPhysicsFps() { return 60.0f; }

    default int getPhysicsMaxSubstepCount() { return 5; }

    default float getPhysicsInertiaStrength() { return 0.5f; }

    default float getPhysicsMaxLinearVelocity() { return 20.0f; }

    default float getPhysicsMaxAngularVelocity() { return 20.0f; }

    default boolean isPhysicsJointsEnabled() { return true; }

    default boolean isPhysicsKinematicFilter() { return true; }

    default boolean isPhysicsDebugLog() { return false; }
}
