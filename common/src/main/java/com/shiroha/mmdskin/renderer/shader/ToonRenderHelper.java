package com.shiroha.mmdskin.renderer.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.shiroha.mmdskin.NativeFunc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL46C;


/**
 * Toon 渲染辅助类
 * 
 * 提取 GPU 蒙皮和 CPU 蒙皮模式下 Toon 渲染的公共逻辑：
 * - OpenGL 状态管理
 * - 子网格遍历和绘制
 * - 材质/纹理绑定
 * - Toon 参数设置
 */
public class ToonRenderHelper {
    
    private static final ToonConfig toonConfig = ToonConfig.getInstance();
    
    /**
     * 设置 Toon 渲染公共参数
     */
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
    
    /**
     * 设置描边参数
     */
    public static void setupOutlineUniforms(ToonShaderBase shader) {
        shader.setOutlineWidth(toonConfig.getOutlineWidth());
        shader.setOutlineColor(
            toonConfig.getOutlineColorR(),
            toonConfig.getOutlineColorG(),
            toonConfig.getOutlineColorB()
        );
    }
    
    /**
     * 准备 Toon 渲染的 OpenGL 状态
     */
    public static void prepareRenderState(int vao) {
        BufferUploader.reset();
        GL46C.glBindVertexArray(vao);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * 恢复 OpenGL 状态
     */
    public static void restoreRenderState() {
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        GL46C.glUseProgram(0);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        BufferUploader.reset();
    }
    
    /**
     * 绘制描边 pass
     * @return 是否执行了描边绘制
     */
    public static boolean isOutlineEnabled() {
        return toonConfig.isOutlineEnabled();
    }
    
    /**
     * 设置描边剔除模式（正面剔除）
     */
    public static void setupOutlineCulling() {
        GL46C.glCullFace(GL46C.GL_FRONT);
        RenderSystem.enableCull();
    }
    
    /**
     * 恢复正常剔除模式（背面剔除）
     */
    public static void restoreNormalCulling() {
        GL46C.glCullFace(GL46C.GL_BACK);
    }
    
    /**
     * 绘制所有子网格（描边 pass，不需要纹理）
     */
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
    
    /**
     * 材质信息接口
     */
    public interface MaterialProvider {
        int getTextureId(int materialID);
    }
    
    /**
     * 绘制所有子网格（主 pass，带纹理）
     */
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
            
            // 双面材质处理
            if (nf.GetMaterialBothFace(model, materialID)) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }
            
            // 绑定纹理（setShaderTexture 更新 Iris TextureTracker，glBindTexture 做实际 GL 绑定）
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
    
    /**
     * 禁用顶点属性数组
     */
    public static void disableVertexAttribArray(int... locations) {
        for (int loc : locations) {
            if (loc != -1) {
                GL46C.glDisableVertexAttribArray(loc);
            }
        }
    }
    
    /**
     * 启用并设置 float 顶点属性
     */
    public static void setupFloatVertexAttrib(int location, int vbo, int size, java.nio.ByteBuffer data) {
        if (location != -1) {
            GL46C.glEnableVertexAttribArray(location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, data, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glVertexAttribPointer(location, size, GL46C.GL_FLOAT, false, 0, 0);
        }
    }
    
    /**
     * 启用并设置 int 顶点属性
     */
    public static void setupIntVertexAttrib(int location, int vbo, int size, java.nio.ByteBuffer data) {
        if (location != -1) {
            GL46C.glEnableVertexAttribArray(location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vbo);
            GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, data, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glVertexAttribIPointer(location, size, GL46C.GL_INT, 0, 0);
        }
    }
}
