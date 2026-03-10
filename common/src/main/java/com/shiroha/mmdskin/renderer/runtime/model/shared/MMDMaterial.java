package com.shiroha.mmdskin.renderer.runtime.model.shared;

/**
 * MMD 模型材质（统一定义，避免多个渲染器重复定义）。
 */
public class MMDMaterial {
    public int tex = 0;
    public boolean hasAlpha = false;

    public boolean ownsTexture = false;
}
