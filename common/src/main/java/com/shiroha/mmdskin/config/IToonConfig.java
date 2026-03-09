package com.shiroha.mmdskin.config;

/**
 * Toon 渲染配置子接口
 */

public interface IToonConfig {

    default boolean isToonRenderingEnabled() { return true; }

    default int getToonLevels() { return 3; }

    default boolean isToonOutlineEnabled() { return false; }

    default float getToonOutlineWidth() { return 0.003f; }

    default float getToonRimPower() { return 5.0f; }
    default float getToonRimIntensity() { return 0.1f; }

    default float getToonShadowR() { return 0.8f; }
    default float getToonShadowG() { return 0.8f; }
    default float getToonShadowB() { return 0.8f; }

    default float getToonSpecularPower() { return 30.0f; }
    default float getToonSpecularIntensity() { return 0.08f; }

    default float getToonOutlineR() { return 0.0f; }
    default float getToonOutlineG() { return 0.0f; }
    default float getToonOutlineB() { return 0.0f; }
}
