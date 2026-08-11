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
        int texId = GL46C.glGenTextures();
        try {
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, texId);
            port.copyTextureData(buf, dataAddr, size);
            buf.rewind();
            uploadPixels(width, height, hasAlpha, buf);
            configureTexture();
            return buildTexture(texId, width, height, hasAlpha);
        } catch (RuntimeException | Error e) {
            deleteGlTexture(texId);
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
        PixelUnpackState previousState = PixelUnpackState.capture();
        try {
            // 绑定 PBO 时 OpenGL 会把 ByteBuffer 数据解释为偏移量，重置全部
            // 解包参数，避免纹理上传继承其他渲染器遗留的状态。
            GL46C.glBindBuffer(GL46C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 1);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ROW_LENGTH, 0);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SKIP_ROWS, 0);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SKIP_PIXELS, 0);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SKIP_IMAGES, 0);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SWAP_BYTES, GL46C.GL_FALSE);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_LSB_FIRST, GL46C.GL_FALSE);

            int format = hasAlpha ? GL46C.GL_RGBA : GL46C.GL_RGB;
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, format, w, h, 0, format, GL46C.GL_UNSIGNED_BYTE, buf);
        } finally {
            previousState.restore();
        }
    }

    private static final class PixelUnpackState {
        private final int pixelUnpackBuffer;
        private final int alignment;
        private final int rowLength;
        private final int skipRows;
        private final int skipPixels;
        private final int imageHeight;
        private final int skipImages;
        private final int swapBytes;
        private final int lsbFirst;

        private PixelUnpackState() {
            pixelUnpackBuffer = GL46C.glGetInteger(GL46C.GL_PIXEL_UNPACK_BUFFER_BINDING);
            alignment = GL46C.glGetInteger(GL46C.GL_UNPACK_ALIGNMENT);
            rowLength = GL46C.glGetInteger(GL46C.GL_UNPACK_ROW_LENGTH);
            skipRows = GL46C.glGetInteger(GL46C.GL_UNPACK_SKIP_ROWS);
            skipPixels = GL46C.glGetInteger(GL46C.GL_UNPACK_SKIP_PIXELS);
            imageHeight = GL46C.glGetInteger(GL46C.GL_UNPACK_IMAGE_HEIGHT);
            skipImages = GL46C.glGetInteger(GL46C.GL_UNPACK_SKIP_IMAGES);
            swapBytes = GL46C.glGetInteger(GL46C.GL_UNPACK_SWAP_BYTES);
            lsbFirst = GL46C.glGetInteger(GL46C.GL_UNPACK_LSB_FIRST);
        }

        static PixelUnpackState capture() {
            return new PixelUnpackState();
        }

        void restore() {
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, alignment);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ROW_LENGTH, rowLength);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SKIP_ROWS, skipRows);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SKIP_IMAGES, skipImages);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_SWAP_BYTES, swapBytes);
            GL46C.glPixelStorei(GL46C.GL_UNPACK_LSB_FIRST, lsbFirst);
            GL46C.glBindBuffer(GL46C.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
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
