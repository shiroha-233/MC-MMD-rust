/* 文件职责：定义 VR 运行时可读取的全局配置。 */
package com.shiroha.mmdskin.config;

public interface IVRConfig {
    default boolean isVREnabled() { return false; }

    default float getVRArmIKStrength() { return 1.0f; }
}
