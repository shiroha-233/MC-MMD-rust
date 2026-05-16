/* 文件职责：封装 Toon 渲染参数与 OpenGL 状态切换。 */
package com.shiroha.mmdskin.renderer.pipeline.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.compat.RenderSystemCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;

/**
 * Toon 渲染辅助类。
 */
public class ToonRenderHelper {

    private static final ToonConfig toonConfig = ToonConfig.getInstance();
    private static final float ALPHA_CUTOFF = 0.1f;

    public static void setupToonUniforms(ToonShaderBase shader, float lightIntensity, Vector3f lightDirection) {
        float lightX = 0.35f;
        float lightY = 0.85f;
        float lightZ = -0.4f;
        if (lightDirection != null) {
            float lenSq = lightDirection.x * lightDirection.x
                    + lightDirection.y * lightDirection.y
                    + lightDirection.z * lightDirection.z;
            if (lenSq > 1.0E-6f) {
                float invLen = (float) (1.0 / Math.sqrt(lenSq));
                lightX = lightDirection.x * invLen;
                lightY = lightDirection.y * invLen;
                lightZ = lightDirection.z * invLen;
            }
        }

        shader.setSampler0(0);
        shader.setLightIntensity(lightIntensity);
        shader.setToonLevels(toonConfig.getToonLevels());
        shader.setRimLight(toonConfig.getRimPower(), toonConfig.getRimIntensity());
        shader.setShadowColor(
            toonConfig.getShadowColorR(),
            toonConfig.getShadowColorG(),
            toonConfig.getShadowColorB()
        );
        shader.setSpecular(toonConfig.getSpecularPower(), toonConfig.getSpecularIntensity());
        shader.setLightDirection(lightX, lightY, lightZ);
        shader.setAlphaCutoff(ALPHA_CUTOFF);
    }

    public static void setupOutlineUniforms(ToonShaderBase shader) {
        shader.setOutlineSampler0(0);
        shader.setOutlineAlphaCutoff(ALPHA_CUTOFF);
        shader.setOutlineWidth(toonConfig.getOutlineWidth());
        shader.setOutlineColor(
            toonConfig.getOutlineColorR(),
            toonConfig.getOutlineColorG(),
            toonConfig.getOutlineColorB()
        );
    }

    public static void prepareRenderState(int vao) {
        /* BufferUploader.reset() removed in 1.21.11 */
        GL46C.glBindVertexArray(vao);
        GlStateManager._enableBlend();
        GlStateManager._enableDepthTest();
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        // TODO_1.21.11 stub: RenderSystem.blendFunc 已删除，使用 RenderSystemCompat 兼容
        RenderSystemCompat.blendFuncSrcAlphaOneMinusSrcAlpha();
    }

    public static void restoreRenderState() {
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        GL46C.glUseProgram(0);
        GlStateManager._activeTexture(GL46C.GL_TEXTURE0);
        /* BufferUploader.reset() removed in 1.21.11 */
    }

    public static boolean isOutlineEnabled() {
        return toonConfig.isOutlineEnabled();
    }

    public static void setupOutlineCulling() {
        GL46C.glCullFace(GL46C.GL_FRONT);
        GlStateManager._enableCull();
    }

    public static void restoreNormalCulling() {
        GL46C.glCullFace(GL46C.GL_BACK);
    }

    public static void drawSubMeshesOutline(NativeFunc nf, long model, int indexElementSize, int indexType) {
        long subMeshCount = nf.GetSubMeshCount(model);
        for (long i = 0; i < subMeshCount; ++i) {
            int materialID = nf.GetSubMeshMaterialID(model, i);
            if (!nf.IsMaterialVisible(model, materialID)) continue;
            if (nf.GetMaterialAlpha(model, materialID) == 0.0f) continue;

            long startPos = (long) nf.GetSubMeshBeginIndex(model, i) * indexElementSize;
            int count = nf.GetSubMeshVertexCount(model, i);
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, indexType, startPos);
        }
    }

    public interface MaterialProvider {
        int getTextureId(int materialID);
    }

    public static void drawSubMeshesMain(Minecraft mc, NativeFunc nf, long model,
                                         int indexElementSize, int indexType,
                                         MaterialProvider materialProvider) {
        GlStateManager._activeTexture(GL46C.GL_TEXTURE0);
        long subMeshCount = nf.GetSubMeshCount(model);

        for (long i = 0; i < subMeshCount; ++i) {
            int materialID = nf.GetSubMeshMaterialID(model, i);
            if (!nf.IsMaterialVisible(model, materialID)) continue;

            float alpha = nf.GetMaterialAlpha(model, materialID);
            if (alpha == 0.0f) continue;

            if (nf.GetMaterialBothFace(model, materialID)) {
                GlStateManager._disableCull();
            } else {
                GlStateManager._enableCull();
            }

            int texId = materialProvider.getTextureId(materialID);
            if (texId == 0) {
                texId = 0;
            }
            // TODO_1.21.11 stub: AbstractTexture.getId / RenderSystem.setShaderTexture 已删除
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, texId);

            long startPos = (long) nf.GetSubMeshBeginIndex(model, i) * indexElementSize;
            int count = nf.GetSubMeshVertexCount(model, i);

            GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, indexType, startPos);
        }
    }

    public static void disableVertexAttribArray(int... locations) {
        for (int loc : locations) {
            if (loc != -1) {
                GL46C.glDisableVertexAttribArray(loc);
            }
        }
    }

    public static void setupFloatVertexAttrib(int location, int vbo, int size, java.nio.ByteBuffer data) {
        if (location != -1) {
            GL46C.glEnableVertexAttribArray(location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, data, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glVertexAttribPointer(location, size, GL46C.GL_FLOAT, false, 0, 0);
        }
    }

    public static void setupIntVertexAttrib(int location, int vbo, int size, java.nio.ByteBuffer data) {
        if (location != -1) {
            GL46C.glEnableVertexAttribArray(location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, data, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glVertexAttribIPointer(location, size, GL46C.GL_INT, 0, 0);
        }
    }
}
