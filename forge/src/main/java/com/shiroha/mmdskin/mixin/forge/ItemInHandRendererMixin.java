package com.shiroha.mmdskin.mixin.forge;

import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.compat.vr.VRHandRenderer;
import com.shiroha.mmdskin.forge.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemInHandRenderer Mixin — 第一人称手臂隐藏 + VR 手部渲染
 */
@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void onRenderHandsWithItems(float partialTick, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int packedLight,
            CallbackInfo ci) {
        String playerName = player.getName().getString();
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, true);

        boolean isMmdDefault = selectedModel == null || selectedModel.isEmpty() || selectedModel.equals("默认 (原版渲染)");
        boolean isMmdActive = !isMmdDefault;
        boolean isVanilaMmdModel = isMmdActive && (selectedModel.equals("VanilaModel") || selectedModel.equalsIgnoreCase("vanila"));

        if (YsmCompat.isYsmModelActive(player)) {
            if (YsmCompat.isDisableSelfHands()) {
                ci.cancel();
            }
            return;
        }

        if (FirstPersonManager.shouldRenderFirstPerson() && isMmdActive && !isVanilaMmdModel) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void onRenderArmWithItem(AbstractClientPlayer player, float partialTick,
            float pitch, InteractionHand hand, float swingProgress, ItemStack itemStack,
            float equippedProgress, PoseStack poseStack, MultiBufferSource buffer,
            int combinedLight, CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {

            VRHandRenderer.renderHandItem(poseStack, buffer, combinedLight, hand);
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void onRenderPlayerArm(PoseStack poseStack, MultiBufferSource buffer,
            int combinedLight, float equippedProgress, float swingProgress,
            HumanoidArm side, CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            ci.cancel();
        }
    }
}

