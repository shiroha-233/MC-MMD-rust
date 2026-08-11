package com.shiroha.mmdskin.compat.tacz;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL46C;

/** 文件职责：隔离 TaCZ 延后绘制与前后特殊方块渲染器的 GL 状态。 */
final class TaczDeferredRenderState implements AutoCloseable {
    private final boolean depthTest;
    private final boolean depthMask;
    private final boolean blend;
    private final boolean cull;
    private final int depthFunc;
    private final int blendEquation;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int cullFaceMode;
    private final int activeTexture;
    private final ShaderInstance shader;
    private final float[] shaderColor;

    private TaczDeferredRenderState() {
        RenderSystem.assertOnRenderThread();
        depthTest = GL46C.glIsEnabled(GL46C.GL_DEPTH_TEST);
        depthMask = GL46C.glGetBoolean(GL46C.GL_DEPTH_WRITEMASK);
        blend = GL46C.glIsEnabled(GL46C.GL_BLEND);
        cull = GL46C.glIsEnabled(GL46C.GL_CULL_FACE);
        depthFunc = GL46C.glGetInteger(GL46C.GL_DEPTH_FUNC);
        blendEquation = GL46C.glGetInteger(GL46C.GL_BLEND_EQUATION_RGB);
        blendSrcRgb = GL46C.glGetInteger(GL46C.GL_BLEND_SRC_RGB);
        blendDstRgb = GL46C.glGetInteger(GL46C.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL46C.glGetInteger(GL46C.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL46C.glGetInteger(GL46C.GL_BLEND_DST_ALPHA);
        cullFaceMode = GL46C.glGetInteger(GL46C.GL_CULL_FACE_MODE);
        activeTexture = GL46C.glGetInteger(GL46C.GL_ACTIVE_TEXTURE);
        shader = RenderSystem.getShader();
        shaderColor = RenderSystem.getShaderColor().clone();

        // 先翻转再设置目标值，使直接操作 GL 的实例化渲染器与 Blaze3D 状态缓存重新同步。
        RenderSystem.disableDepthTest();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL46C.GL_ALWAYS);
        RenderSystem.depthFunc(GL46C.GL_LEQUAL);
        RenderSystem.disableBlend();
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL46C.GL_FUNC_SUBTRACT);
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.enableCull();
        GL46C.glCullFace(GL46C.GL_BACK);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE1);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        BufferUploader.reset();
        RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    static TaczDeferredRenderState begin() {
        return new TaczDeferredRenderState();
    }

    @Override
    public void close() {
        BufferUploader.reset();
        // 1.20.1 Blaze3D 只公开单一混合方程缓存，使用对称 API 避免真实 GL 与缓存再次分叉。
        GlStateManager._blendEquation(blendEquation);
        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        RenderSystem.depthFunc(depthFunc);
        RenderSystem.depthMask(depthMask);
        setDepthTest(depthTest);
        setBlend(blend);
        setCull(cull);
        GL46C.glCullFace(cullFaceMode);
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
    }

    private static void setDepthTest(boolean enabled) {
        if (enabled) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
    }

    private static void setBlend(boolean enabled) {
        if (enabled) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();
    }

    private static void setCull(boolean enabled) {
        if (enabled) RenderSystem.enableCull();
        else RenderSystem.disableCull();
    }
}
