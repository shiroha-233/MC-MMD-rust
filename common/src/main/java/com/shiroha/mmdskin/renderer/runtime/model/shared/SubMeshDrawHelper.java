package com.shiroha.mmdskin.renderer.runtime.model.shared;

import com.mojang.blaze3d.systems.RenderSystem;
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
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);

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

            if (bothFace) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }

            int textureId = textureResolver.resolve(materialId);
            RenderSystem.setShaderTexture(0, textureId);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);

            long startPos = (long) beginIndex * indexElementSize;
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, vertexCount, indexType, startPos);
        }
    }

    public static void drawOutline(ByteBuffer subMeshDataBuf,
                                   int subMeshCount,
                                   int indexElementSize,
                                   int indexType,
                                   AlphaResolver alphaResolver) {
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

            long startPos = (long) beginIndex * indexElementSize;
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, vertexCount, indexType, startPos);
        }
    }
}
