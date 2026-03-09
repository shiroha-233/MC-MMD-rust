package com.shiroha.mmdskin.renderer.pipeline.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.shiroha.mmdskin.NativeFunc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL46C;

/**
 * Toon 渲染辅助类。
 */
public class ToonRenderHelper {

    private static final ToonConfig toonConfig = ToonConfig.getInstance();

    public static void setupToonUniforms(ToonShaderBase shader, float lightIntensity) {
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
    }

    public static void setupOutlineUniforms(ToonShaderBase shader) {
        shader.setOutlineWidth(toonConfig.getOutlineWidth());
        shader.setOutlineColor(
            toonConfig.getOutlineColorR(),
            toonConfig.getOutlineColorG(),
            toonConfig.getOutlineColorB()
        );
    }

    public static void prepareRenderState(int vao) {
        BufferUploader.reset();
        GL46C.glBindVertexArray(vao);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    public static void restoreRenderState() {
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        GL46C.glUseProgram(0);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        BufferUploader.reset();
    }

    public static boolean isOutlineEnabled() {
        return toonConfig.isOutlineEnabled();
    }

    public static void setupOutlineCulling() {
        GL46C.glCullFace(GL46C.GL_FRONT);
        RenderSystem.enableCull();
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
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        long subMeshCount = nf.GetSubMeshCount(model);

        for (long i = 0; i < subMeshCount; ++i) {
            int materialID = nf.GetSubMeshMaterialID(model, i);
            if (!nf.IsMaterialVisible(model, materialID)) continue;

            float alpha = nf.GetMaterialAlpha(model, materialID);
            if (alpha == 0.0f) continue;

            if (nf.GetMaterialBothFace(model, materialID)) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }

            int texId = materialProvider.getTextureId(materialID);
            if (texId == 0) {
                texId = mc.getTextureManager().getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE).getId();
            }
            RenderSystem.setShaderTexture(0, texId);
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
