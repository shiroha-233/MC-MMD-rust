// 负责在 26.2 世界渲染帧开始时挂接 MMD 帧钩子。
// 26.2 适配：fabric-api 移除了旧 WorldRenderEvents；帧开始仍由 LevelRenderer 提供，
// 世界帧 flush 改在 fabric LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN 回调中执行
// （frame graph 处理完成后、主目标深度可用的时机）。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererFrameMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void mmdskin$beginFrame(
            com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker, boolean advancedGameTime, CameraRenderState camera,
            org.joml.Matrix4fc matrix, com.mojang.blaze3d.buffers.GpuBufferSlice fog,
            org.joml.Vector4f clearColor, boolean renderLevel, CallbackInfo ci) {
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.beginFrame(deltaTracker.getGameTimeDeltaTicks()));
    }
}
