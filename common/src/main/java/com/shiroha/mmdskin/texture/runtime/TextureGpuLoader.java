package com.shiroha.mmdskin.texture.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.bridge.runtime.NativeTexturePort;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** 文件职责：封装纹理从 native 解码到 GPU 上传的无状态操作。 */
final class TextureGpuLoader {

    private TextureGpuLoader() {
    }

    static TextureRepository.Texture loadToGpu(String filename, NativeTexturePort port) {
        long handle = port.loadTexture(filename);
        if (handle == 0) return null;

        int width = port.textureWidth(handle);
        int height = port.textureHeight(handle);
        long dataAddr = port.textureData(handle);
        boolean hasAlpha = port.textureHasAlpha(handle);
        int size = width * height * (hasAlpha ? 4 : 3);

        ByteBuffer buf = MemoryUtil.memAlloc(size);
        try {
            int texId = GL46C.glGenTextures();
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, texId);
            port.copyTextureData(buf, dataAddr, size);
            buf.rewind();
            uploadPixels(width, height, hasAlpha, buf);
            configureTexture();
            return buildTexture(texId, width, height, hasAlpha);
        } catch (RuntimeException | Error e) {
            deleteGlTexture(GL46C.glGenTextures());
            throw e;
        } finally {
            MemoryUtil.memFree(buf);
            port.deleteTexture(handle);
        }
    }

    static TextureRepository.PredecodedTexture decode(String filename, NativeTexturePort port) {
        long handle = port.loadTexture(filename);
        if (handle == 0) return null;
        try {
            int width = port.textureWidth(handle);
            int height = port.textureHeight(handle);
            long dataAddr = port.textureData(handle);
            boolean hasAlpha = port.textureHasAlpha(handle);
            int size = width * height * (hasAlpha ? 4 : 3);
            ByteBuffer buf = MemoryUtil.memAlloc(size);
            port.copyTextureData(buf, dataAddr, size);
            buf.rewind();
            return new TextureRepository.PredecodedTexture(buf, width, height, hasAlpha);
        } finally {
            port.deleteTexture(handle);
        }
    }

    static TextureRepository.Texture uploadPredecoded(TextureRepository.PredecodedTexture pre) {
        int texId = GL46C.glGenTextures();
        try {
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, texId);
            uploadPixels(pre.width, pre.height, pre.hasAlpha, pre.pixelData());
            configureTexture();
            return buildTexture(texId, pre.width, pre.height, pre.hasAlpha);
        } catch (RuntimeException | Error e) {
            deleteGlTexture(texId);
            throw e;
        } finally {
            pre.release();
        }
    }

    static void deleteGlTexture(TextureRepository.Texture tex) {
        if (tex != null) {
            deleteGlTexture(tex.tex);
            tex.tex = 0;
        }
    }

    static void deleteGlTexture(int texId) {
        if (texId <= 0) return;
        if (RenderSystem.isOnRenderThreadOrInit()) {
            GL46C.glDeleteTextures(texId);
        } else {
            RenderSystem.recordRenderCall(() -> GL46C.glDeleteTextures(texId));
        }
    }

    private static void uploadPixels(int w, int h, boolean hasAlpha, ByteBuffer buf) {
        if (hasAlpha) {
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 4);
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGBA, w, h, 0, GL46C.GL_RGBA, GL46C.GL_UNSIGNED_BYTE, buf);
        } else {
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 1);
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGB, w, h, 0, GL46C.GL_RGB, GL46C.GL_UNSIGNED_BYTE, buf);
        }
    }

    private static void configureTexture() {
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_LEVEL, 0);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, GL46C.GL_LINEAR);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, GL46C.GL_LINEAR);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
    }

    private static TextureRepository.Texture buildTexture(int texId, int w, int h, boolean hasAlpha) {
        TextureRepository.Texture t = new TextureRepository.Texture();
        t.tex = texId;
        t.hasAlpha = hasAlpha;
        t.vramSize = (long) w * h * (hasAlpha ? 4 : 3);
        return t;
    }
}
