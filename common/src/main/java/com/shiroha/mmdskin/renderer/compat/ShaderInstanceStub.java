package com.shiroha.mmdskin.renderer.compat;

public final class ShaderInstanceStub {
    public final org.joml.Matrix4f MODEL_VIEW_MATRIX = null;
    public final org.joml.Matrix4f PROJECTION_MATRIX = null;
    public final org.joml.Matrix4f TEXTURE_MATRIX = null;
    public final Object COLOR_MODULATOR = null;
    public final Object LIGHT0_DIRECTION = null;
    public final Object LIGHT1_DIRECTION = null;
    public final Object FOG_START = null;
    public final Object FOG_END = null;
    public final Object FOG_COLOR = null;
    public final Object FOG_SHAPE = null;
    public final Object GAME_TIME = null;
    public final Object SCREEN_SIZE = null;
    public final Object LINE_WIDTH = null;

    private ShaderInstanceStub() {
    }

    public int getId() {
        return 0;
    }

    public void apply() {
    }

    public void clear() {
    }

    public void setSampler(String name, Object value) {
    }
}
