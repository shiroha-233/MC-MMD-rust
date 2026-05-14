package com.shiroha.mmdskin.config;

/**
 * Toon 渲染配置子接口
 */

public interface IToonConfig {

    default boolean isToonRenderingEnabled() { return false; }

    default int getToonLevels() { return 4; }

    default boolean isToonOutlineEnabled() { return true; }

    default float getToonOutlineWidth() { return 0.0022f; }

    default float getToonRimPower() { return 5.6f; }
    default float getToonRimIntensity() { return 0.02f; }

    default float getToonShadowR() { return 0.78f; }
    default float getToonShadowG() { return 0.84f; }
    default float getToonShadowB() { return 0.94f; }

    default float getToonSpecularPower() { return 96.0f; }
    default float getToonSpecularIntensity() { return 0.015f; }

    default float getToonOutlineR() { return 0.06f; }
    default float getToonOutlineG() { return 0.08f; }
    default float getToonOutlineB() { return 0.12f; }
}
