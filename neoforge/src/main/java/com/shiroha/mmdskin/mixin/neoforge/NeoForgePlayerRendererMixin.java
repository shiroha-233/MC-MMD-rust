package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.neoforge.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.renderer.integration.player.PlayerMixinDelegate;
import com.shiroha.mmdskin.renderer.integration.player.PlayerMixinDelegate.RenderAction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 玩家渲染器 Mixin
 * 核心渲染逻辑委托给 PlayerMixinDelegate（DRY 原则）
 */
@Mixin(PlayerRenderer.class)
public abstract class NeoForgePlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public NeoForgePlayerRendererMixin(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    public void onRender(AbstractClientPlayer player, float entityYaw, float tickDelta, PoseStack matrixStack,
                         MultiBufferSource vertexConsumers, int packedLight, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalPlayer = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        if (isLocalPlayer && minecraft.options.getCameraType().isFirstPerson()
                && !FirstPersonManager.shouldRenderFirstPerson() && !VRArmHider.isLocalPlayerInVR()) {
            FirstPersonManager.reset();
            return;
        }

        RenderAction action = PlayerMixinDelegate.handleRender(
                player, entityYaw, tickDelta, matrixStack, vertexConsumers, packedLight,
                YsmCompat.isYsmActive(player));

        PlayerMixinDelegate.renderSceneModel(player, tickDelta, matrixStack, packedLight);

        switch (action) {
            case CANCEL -> ci.cancel();
            case SUPER_RENDER -> {
                ci.cancel();
                super.render(player, entityYaw, tickDelta, matrixStack, vertexConsumers, packedLight);
                return;
            }
            case FALLTHROUGH -> { /* 不干预，原版流程继续 */ }
        }
    }
}
