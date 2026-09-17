// 负责使用 Minecraft 26.2 GpuTexture API 创建并上传 RGBA8 纹理。
package com.shiroha.mmdskin.client.texture;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MinecraftTextureDevice implements TextureDevice {
    @Override
    public void assertRenderThread() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public TextureResource createAndUpload(Upload upload) {
        assertRenderThread();
        GpuTexture texture = RenderSystem.getDevice().createTexture(
                () -> "MMD texture " + upload.key().nativePath(),
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM,
                upload.width(),
                upload.height(),
                1,
                1);
        try {
            ByteBuffer bytes = upload.rgbaPixels().duplicate().order(ByteOrder.nativeOrder());
            // 26.2 签名: (texture, buffer, mipLevel, z, x, y, width, height)
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    texture, bytes, 0, 0, 0, 0, upload.width(), upload.height());
            return new MinecraftTextureResource(texture, upload.width(), upload.height(), upload.hasAlpha());
        } catch (RuntimeException exception) {
            texture.close();
            throw exception;
        }
    }
}
