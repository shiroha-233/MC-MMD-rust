package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 玩家渲染器 Mixin
 * TODO_1.21.11: PlayerRenderer 已重命名为 AvatarRenderer，render(...) 改为 submit(AvatarRenderState, PoseStack, SubmitNodeCollector, CameraRenderState)；
 * 原渲染逻辑（FirstPersonManager/PlayerMixinDelegate）依赖 LivingEntity 与 packedLight，新管线下已不再可用，需重写后再启用。
 * 当前保留 Mixin 占位以确保编译通过。
 */
@Mixin(AvatarRenderer.class)
public abstract class NeoForgePlayerRendererMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void mmdskin$onSubmit(AvatarRenderState renderState, PoseStack poseStack,
                                  SubmitNodeCollector collector, CameraRenderState cameraRenderState,
                                  CallbackInfo ci) {
        // TODO_1.21.11: 渲染管线重写 — Mixin 目标已变（render -> submit, RenderState 化）
        // 原逻辑：FirstPersonManager/PlayerMixinDelegate.handleRender + renderSceneModel
    }
}
