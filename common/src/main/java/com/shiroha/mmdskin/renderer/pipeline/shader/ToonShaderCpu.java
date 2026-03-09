package com.shiroha.mmdskin.renderer.pipeline.shader;

import com.shiroha.mmdskin.util.AssetsUtil;

/**
 * CPU 蒙皮版本的 Toon 着色器。
 */
public class ToonShaderCpu extends ToonShaderBase {

    private static final String MAIN_VERTEX_SHADER =
            AssetsUtil.getAssetsAsString("shader/toon_main_body.vert.glsl");

    private static final String OUTLINE_VERTEX_SHADER =
            AssetsUtil.getAssetsAsString("shader/toon_outline_body.vert.glsl");

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

    }

    @Override
    protected String getShaderName() {
        return "ToonShaderCpu";
    }
}
