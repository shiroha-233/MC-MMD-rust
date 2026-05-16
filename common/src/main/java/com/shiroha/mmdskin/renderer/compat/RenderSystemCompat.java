package com.shiroha.mmdskin.renderer.compat;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL46C;

public final class RenderSystemCompat {
    private RenderSystemCompat() {
    }

    public static void enableBlend() {
        GlStateManager._enableBlend();
    }

    public static void disableBlend() {
        GlStateManager._disableBlend();
    }

    public static void enableDepthTest() {
        GlStateManager._enableDepthTest();
    }

    public static void enableCull() {
        GlStateManager._enableCull();
    }

    public static void depthMask(boolean flag) {
        GlStateManager._depthMask(flag);
    }

    public static void defaultBlendFunc() {
        GlStateManager._blendFuncSeparate(770, 771, 1, 771);
    }

    public static void blendFuncSrcAlphaOneMinusSrcAlpha() {
        GlStateManager._blendFuncSeparate(770, 771, 1, 771);
    }

    public static void activeTexture(int unit) {
        GlStateManager._activeTexture(unit);
    }

    public static void bindTexture(int textureId) {
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);
    }

    public static Matrix4f getProjectionMatrix() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameRenderer != null) {
            return mc.gameRenderer.getProjectionMatrix(mc.gameRenderer.getDepthFar());
        }
        return new Matrix4f();
    }

    public static Matrix4f getModelViewMatrix() {
        return RenderSystem.getModelViewMatrix();
    }

    public static void setShaderColor(float r, float g, float b, float a) {
    }

    public static float[] getShaderColor() {
        return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    }

    public static void resetBufferUploader() {
    }
}
