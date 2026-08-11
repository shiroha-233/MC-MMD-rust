package com.shiroha.mmdskin.config;

/** 控制关节拓扑内部刚体对的碰撞过滤强度。 */
public enum PhysicsCollisionStabilityMode {
    STRICT(0),
    STABLE(1),
    RELAXED(2);

    private final int nativeValue;

    PhysicsCollisionStabilityMode(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int nativeValue() {
        return nativeValue;
    }
}
