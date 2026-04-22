package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.render.entity.MobReplacementRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版生物的 MMD 替换渲染入口。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MobReplacementLivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true)
    private void mmdskin$renderMobReplacement(LivingEntity entity, float entityYaw, float partialTicks,
                                              PoseStack poseStack, MultiBufferSource bufferIn,
                                              int packedLightIn, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer) {
            return;
        }

        if (MobReplacementRenderer.render(entity, entityYaw, partialTicks, poseStack, packedLightIn)) {
            ci.cancel();
        }
    }
}
