/* 文件职责：在 NeoForge 侧拦截原版生物渲染并接入 MMD 替换渲染。 */
package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.integration.entity.MobReplacementRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MobReplacementLivingEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mmdskin$renderMobReplacement(LivingEntity entity, float entityYaw, float partialTick,
                                              PoseStack poseStack, MultiBufferSource bufferSource,
                                              int packedLight, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer) {
            return;
        }

        if (MobReplacementRenderer.render(entity, entityYaw, partialTick, poseStack, packedLight)) {
            ci.cancel();
        }
    }
}
