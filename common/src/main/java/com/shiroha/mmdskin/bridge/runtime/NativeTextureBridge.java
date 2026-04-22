package com.shiroha.mmdskin.bridge.runtime;

import com.shiroha.mmdskin.NativeFunc;
import java.nio.ByteBuffer;

/** 文件职责：收口纹理解码与像素拷贝的 native 调用。 */
public final class NativeTextureBridge implements NativeTexturePort {

    private NativeFunc nativeFunc() {
        return NativeFunc.GetInst();
    }

    @Override
    public long loadTexture(String filename) {
        return nativeFunc().LoadTexture(filename);
    }

    @Override
    public int textureWidth(long textureHandle) {
        return nativeFunc().GetTextureX(textureHandle);
    }

    @Override
    public int textureHeight(long textureHandle) {
        return nativeFunc().GetTextureY(textureHandle);
    }

    @Override
    public long textureData(long textureHandle) {
        return nativeFunc().GetTextureData(textureHandle);
    }

    @Override
    public boolean textureHasAlpha(long textureHandle) {
        return nativeFunc().TextureHasAlpha(textureHandle);
    }

    @Override
    public void copyTextureData(ByteBuffer targetBuffer, long sourceAddress, int size) {
        nativeFunc().CopyDataToByteBuffer(targetBuffer, sourceAddress, size);
    }

    @Override
    public void deleteTexture(long textureHandle) {
        nativeFunc().DeleteTexture(textureHandle);
    }
}
