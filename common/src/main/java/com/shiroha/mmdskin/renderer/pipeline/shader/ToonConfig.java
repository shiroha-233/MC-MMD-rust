package com.shiroha.mmdskin.renderer.pipeline.shader;

import com.shiroha.mmdskin.config.ConfigManager;

/**
 * Toon 渲染配置代理。
 */
public class ToonConfig {

    private static final ToonConfig INSTANCE = new ToonConfig();

    private ToonConfig() {}

    public static ToonConfig getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return ConfigManager.isToonRenderingEnabled();
    }

    public int getToonLevels() {
        return ConfigManager.getToonLevels();
    }

    public float getRimPower() {
        return ConfigManager.getToonRimPower();
    }

    public float getRimIntensity() {
        return ConfigManager.getToonRimIntensity();
    }

    public float getShadowColorR() {
        return ConfigManager.getToonShadowR();
    }

    public float getShadowColorG() {
        return ConfigManager.getToonShadowG();
    }

    public float getShadowColorB() {
        return ConfigManager.getToonShadowB();
    }

    public float getSpecularPower() {
        return ConfigManager.getToonSpecularPower();
    }

    public float getSpecularIntensity() {
        return ConfigManager.getToonSpecularIntensity();
    }

    public boolean isOutlineEnabled() {
        return ConfigManager.isToonOutlineEnabled();
    }

    public float getOutlineWidth() {
        return ConfigManager.getToonOutlineWidth();
    }

    public float getOutlineColorR() {
        return ConfigManager.getToonOutlineR();
    }

    public float getOutlineColorG() {
        return ConfigManager.getToonOutlineG();
    }

    public float getOutlineColorB() {
        return ConfigManager.getToonOutlineB();
    }
}
