/* 文件职责：统一管理子网格绘制与描边绘制的索引提交逻辑。 */
package com.shiroha.mmdskin.renderer.runtime.model.shared;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL46C;

import java.nio.ByteBuffer;

/**
 * 子网格绘制公共逻辑。
 */
public final class SubMeshDrawHelper {

    private static final int SUB_MESH_STRIDE = 20;

    private SubMeshDrawHelper() {
    }

    @FunctionalInterface
    public interface TextureResolver {
        int resolve(int materialId);
    }

    @FunctionalInterface
    public interface AlphaResolver {
        float resolve(int materialId, float baseAlpha);
    }

    public static void draw(ByteBuffer subMeshDataBuf,
                            int subMeshCount,
                            int indexElementSize,
                            int indexType,
                            TextureResolver textureResolver,
                            AlphaResolver alphaResolver) {
        // TODO_1.21.11: 渲染管线重写 - RenderSystem.activeTexture 已删除
        GlStateManager._activeTexture(GL46C.GL_TEXTURE0);

        for (int i = 0; i < subMeshCount; ++i) {
            int base = i * SUB_MESH_STRIDE;
            int materialId = subMeshDataBuf.getInt(base);
            int beginIndex = subMeshDataBuf.getInt(base + 4);
            int vertexCount = subMeshDataBuf.getInt(base + 8);
            float alpha = subMeshDataBuf.getFloat(base + 12);
            boolean visible = subMeshDataBuf.get(base + 16) != 0;
            boolean bothFace = subMeshDataBuf.get(base + 17) != 0;

            if (!visible || alphaResolver.resolve(materialId, alpha) < 0.001f) {
                continue;
            }

            // TODO_1.21.11: 渲染管线重写 - RenderSystem 剔除控制已删除，使用 GL 直调
            if (bothFace) {
                GL46C.glDisable(GL46C.GL_CULL_FACE);
            } else {
                GL46C.glEnable(GL46C.GL_CULL_FACE);
            }

            int textureId = textureResolver.resolve(materialId);
            // TODO_1.21.11: 渲染管线重写 - RenderSystem.setShaderTexture 已删除
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);

            long startPos = (long) beginIndex * indexElementSize;
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, vertexCount, indexType, startPos);
        }
    }

    public static void drawOutline(ByteBuffer subMeshDataBuf,
                                   int subMeshCount,
                                   int indexElementSize,
                                   int indexType,
                                   TextureResolver textureResolver,
                                   AlphaResolver alphaResolver) {
        // TODO_1.21.11: 渲染管线重写 - RenderSystem.activeTexture 已删除
        GlStateManager._activeTexture(GL46C.GL_TEXTURE0);
        for (int i = 0; i < subMeshCount; ++i) {
            int base = i * SUB_MESH_STRIDE;
            int materialId = subMeshDataBuf.getInt(base);
            int beginIndex = subMeshDataBuf.getInt(base + 4);
            int vertexCount = subMeshDataBuf.getInt(base + 8);
            float alpha = subMeshDataBuf.getFloat(base + 12);
            boolean visible = subMeshDataBuf.get(base + 16) != 0;

            if (!visible || alphaResolver.resolve(materialId, alpha) < 0.001f) {
                continue;
            }

            int textureId = textureResolver.resolve(materialId);
            // TODO_1.21.11: 渲染管线重写 - RenderSystem.setShaderTexture 已删除
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);

            long startPos = (long) beginIndex * indexElementSize;
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, vertexCount, indexType, startPos);
        }
    }
}
