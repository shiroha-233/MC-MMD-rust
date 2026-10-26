// 负责封装 Minecraft GpuTexture 并保证渲染线程上的幂等释放。
package com.shiroha.mmdskin.client.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MinecraftTextureResource implements TextureResource {
    private final GpuTexture texture;
    private final int width;
    private final int height;
    private final boolean hasAlpha;
    private final long estimatedGpuBytes;
    private final AtomicBoolean closed = new AtomicBoolean();

    MinecraftTextureResource(GpuTexture texture, int width, int height, boolean hasAlpha) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.width = width;
        this.height = height;
        this.hasAlpha = hasAlpha;
        this.estimatedGpuBytes = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
    }

    public GpuTexture gpuTexture() {
        if (closed.get()) {
            throw new IllegalStateException("texture resource is closed");
        }
        return texture;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public boolean hasAlpha() {
        return hasAlpha;
    }

    @Override
    public long estimatedGpuBytes() {
        return estimatedGpuBytes;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.get()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (closed.compareAndSet(false, true)) {
            texture.close();
        }
    }
}
