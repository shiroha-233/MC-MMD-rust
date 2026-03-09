package com.shiroha.mmdskin.config;

/**
 * VR 联动配置子接口（ISP 原则）
 */

public interface IVRConfig {

    default boolean isVREnabled() { return true; }

    default float getVRArmIKStrength() { return 1.0f; }
}
