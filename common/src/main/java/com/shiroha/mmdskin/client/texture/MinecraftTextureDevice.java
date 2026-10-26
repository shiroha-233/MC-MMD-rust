// 负责使用 Minecraft 26.3 GpuTexture API 创建并上传 RGBA8 纹理。
package com.shiroha.mmdskin.client.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;

import java.nio.ByteBuffer;

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
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                upload.width(),
                upload.height(),
                1,
                1);
        try {
            // 26.3 的过滤与 mipmap 设置不再挂在纹理上，采样器由绘制方按需提供。
            //
            // writeToTexture 的参数顺序是
            //   (texture, data, mipLevel, layer, x, y, width, height)
            // 注意 y 在 width 之前、且没有 depth 参数（深度固定 1）。
            // 26.3 移植时按 (…, 0, 0, 0, width, height, 1) 传，
            // 结果 y 被填成 width(2048)、width/height 整体错位一格，
            // 抛 "Dest texture (2048x2048) is not large enough to write a rectangle of 2048x1 at 0x2048"
            // → 所有纹理上传失败 → 模型只能渲染成白色（用户看到的"白膜"）。
            ByteBuffer bytes = upload.rgbaPixels().duplicate();
            com.mojang.renderpearl.api.commands.CommandEncoder encoder =
                    RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToTexture(texture, bytes, 0, 0, 0, 0, upload.width(), upload.height());
            // 26.3 的 CommandEncoder 必须 submit() 才会真正下发到 GPU。
            // 漏掉这一句 → 纹理在 GPU 上始终是空的，模型只能渲染成"白膜"。
            encoder.submit();
            return new MinecraftTextureResource(texture, upload.width(), upload.height(), upload.hasAlpha());
        } catch (RuntimeException exception) {
            texture.close();
            throw exception;
        }
    }
}
