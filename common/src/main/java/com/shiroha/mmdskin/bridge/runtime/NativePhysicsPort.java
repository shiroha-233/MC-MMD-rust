// 文件职责：定义全局物理参数写入的 native 边界。
package com.shiroha.mmdskin.bridge.runtime;

public interface NativePhysicsPort {
    void configure(boolean enabled, float gravityY, float physicsFps, int maxSubstepCount,
                   float inertiaStrength, float maxLinearVelocity, float maxAngularVelocity,
                   boolean jointsEnabled, boolean kinematicFilter,
                   boolean collisionEnabled, int collisionStabilityMode, boolean debugLog);
}
