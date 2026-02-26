package com.shiroha.mmdskin.renderer.shader;

import com.shiroha.mmdskin.util.AssetsUtil;

/**
 * CPU 蒙皮版本的 Toon 着色器
 * 
 * 继承 ToonShaderBase，只需提供不含骨骼蒙皮的顶点着色器。
 * 蒙皮已在 Rust 引擎完成，此着色器直接接收已蒙皮的顶点位置和法线。
 * 
 * 用于 MMDModelOpenGL（CPU 蒙皮模式）
 */
public class ToonShaderCpu extends ToonShaderBase {
    
    // ==================== CPU 蒙皮版顶点着色器（无骨骼计算） ====================
    
    private static final String MAIN_VERTEX_SHADER =
            AssetsUtil.getAssetsAsString("shader/toon_main_body.vert.glsl");
    
    private static final String OUTLINE_VERTEX_SHADER =
            AssetsUtil.getAssetsAsString("shader/toon_outline_body.vert.glsl");
    
    // ==================== 实现抽象方法 ====================
    
    @Override
    protected String getMainVertexShader() {
        return MAIN_VERTEX_SHADER;
    }
    
    @Override
    protected String getOutlineVertexShader() {
        return OUTLINE_VERTEX_SHADER;
    }
    
    @Override
    protected void onInitialized() {
        // CPU 版本无额外初始化
    }
    
    @Override
    protected String getShaderName() {
        return "ToonShaderCpu";
    }
}
