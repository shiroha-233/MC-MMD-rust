package com.shiroha.mmdskin.mixin.forge.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.forge.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.renderer.integration.player.PlayerMixinDelegate;
import com.shiroha.mmdskin.renderer.integration.player.PlayerRenderAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.vivecraft.client.render.VRPlayerRenderer", remap = false)
public abstract class VivecraftVRPlayerRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void mmdskin$renderMmdInsteadOfVivecraftPlayer(AbstractClientPlayer player,
                                                           float entityYaw,
                                                           float tickDelta,
                                                           PoseStack matrixStack,
                                                           MultiBufferSource vertexConsumers,
                                                           int packedLight,
                                                           CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalPlayer = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        if (isLocalPlayer && minecraft.options.getCameraType().isFirstPerson()
                && !FirstPersonManager.shouldRenderFirstPerson()
                && !VRArmHider.isLocalPlayerInVR()) {
            FirstPersonManager.reset();
        }

        PlayerRenderAction action = PlayerMixinDelegate.handleRender(
                player, entityYaw, tickDelta, matrixStack, vertexConsumers, packedLight,
                YsmCompat.isYsmActive(player));

        PlayerMixinDelegate.renderSceneModel(player, tickDelta, matrixStack, packedLight);

        if (action == PlayerRenderAction.CANCEL) {
            ci.cancel();
        }
    }
}
