package com.shiroha.mmdskin.renderer.core;

/**
 * 渲染模式分类（替代字符串匹配，遵循 OCP）
 */
public enum RenderCategory {
    /** CPU 蒙皮（基础回退模式） */
    CPU_SKINNING,
    /** GPU 蒙皮（Compute Shader） */
    GPU_SKINNING
}
