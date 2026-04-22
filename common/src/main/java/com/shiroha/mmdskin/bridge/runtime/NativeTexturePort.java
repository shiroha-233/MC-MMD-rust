package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

/** 文件职责：集中定义纹理解码与像素拷贝的 native 能力。 */
public interface NativeTexturePort {

    long loadTexture(String filename);

    int textureWidth(long textureHandle);

    int textureHeight(long textureHandle);

    long textureData(long textureHandle);

    boolean textureHasAlpha(long textureHandle);

    void copyTextureData(ByteBuffer targetBuffer, long sourceAddress, int size);

    void deleteTexture(long textureHandle);
}
