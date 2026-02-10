package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.renderer.core.FirstPersonManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemInHandRenderer Mixin — 第一人称手臂隐藏
 * 
 * 在第一人称 MMD 模型模式下，跳过原版手臂和手持物品的渲染。
 * 直接拦截 renderHandsWithItems 而非 GameRenderer.renderItemInHand，
 * 确保即使 Iris 等模组重写渲染管线也能生效。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void onRenderHandsWithItems(float partialTick, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int packedLight,
            CallbackInfo ci) {
        if (FirstPersonManager.isActive()) {
            ci.cancel();
        }
    }
}
