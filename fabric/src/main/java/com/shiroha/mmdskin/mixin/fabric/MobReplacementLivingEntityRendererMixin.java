/* 文件职责：在 Fabric 侧通过 LivingEntityRenderer.submit 拦截原版生物渲染并接入 MMD 替换渲染。 */
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.integration.entity.MobReplacementRenderer;
import com.shiroha.mmdskin.renderer.runtime.state.LivingEntityRenderStateBinder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MobReplacementLivingEntityRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void mmdskin$bindEntity(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        LivingEntityRenderStateBinder.bind(state, entity, partialTick);
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mmdskin$renderMobReplacement(LivingEntityRenderState state, PoseStack poseStack,
                                              SubmitNodeCollector collector, CameraRenderState cameraState,
                                              CallbackInfo ci) {
        LivingEntityRenderStateBinder.Binding binding = LivingEntityRenderStateBinder.get(state);
        if (binding == null) {
            return;
        }
        LivingEntity entity = binding.entity();
        if (entity instanceof AbstractClientPlayer) {
            return;
        }
        if (MobReplacementRenderer.render(entity, state.bodyRot, binding.partialTick(), poseStack, state.lightCoords)) {
            ci.cancel();
        }
    }
}
