package com.shiroha.mmdskin.mixin.forge.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VRArmHelper", remap = false)
public abstract class VivecraftVRArmHelperMixin {

    @Inject(method = "renderVRHands", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mmdskin$cancelVivecraftHands(float partialTick,
                                                     boolean renderMainHand,
                                                     boolean renderOffhand,
                                                     boolean menuMainHand,
                                                     boolean menuOffhand,
                                                     PoseStack poseStack,
                                                     CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            ci.cancel();
        }
    }
}
