package com.shiroha.mmdskin.mixin.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.forge.YsmCompat;
import com.shiroha.mmdskin.renderer.integration.player.PlayerMixinDelegate;
import com.shiroha.mmdskin.renderer.integration.player.PlayerRenderAction;

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

/** Forge 玩家渲染入口，委托共享玩家渲染逻辑。 */
@Mixin(PlayerRenderer.class)
public abstract class ForgePlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public ForgePlayerRendererMixin(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void onRender(AbstractClientPlayer player, float entityYaw, float tickDelta, PoseStack matrixStack,
                         MultiBufferSource vertexConsumers, int packedLight, CallbackInfo ci) {
        PlayerRenderAction action = PlayerMixinDelegate.handleRender(
                player, entityYaw, tickDelta, matrixStack, vertexConsumers, packedLight,
                YsmCompat.isYsmActive(player));

        switch (action) {
            case CANCEL -> ci.cancel();
            case SUPER_RENDER -> {
                ci.cancel();
                super.render(player, entityYaw, tickDelta, matrixStack, vertexConsumers, packedLight);
                return;
            }
            case FALLTHROUGH -> {  }
        }
    }
}
