/** 文件职责：隐藏第一人称原版手臂并在 VR 下接管手持物渲染。 */
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.compat.vr.VRHandRenderer;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.fabric.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void onRenderHandsWithItems(float partialTick,
                                        PoseStack poseStack,
                                        MultiBufferSource.BufferSource bufferSource,
                                        LocalPlayer player,
                                        int packedLight,
                                        CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            ci.cancel();
            return;
        }

        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), player.getName().getString(), true);
        boolean hasMmdModel = selectedModel != null
            && !selectedModel.isEmpty()
            && !UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel);
        boolean useVanillaModel = hasMmdModel
            && ("VanilaModel".equals(selectedModel)
            || "VanillaModel".equals(selectedModel)
            || "vanila".equalsIgnoreCase(selectedModel)
            || "vanilla".equalsIgnoreCase(selectedModel));

        if (YsmCompat.isYsmModelActive(player)) {
            if (YsmCompat.isDisableSelfHands()) {
                ci.cancel();
            }
            return;
        }

        if (FirstPersonManager.shouldRenderFirstPerson() && hasMmdModel && !useVanillaModel) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void onRenderArmWithItem(AbstractClientPlayer player,
                                     float partialTick,
                                     float pitch,
                                     InteractionHand hand,
                                     float swingProgress,
                                     ItemStack itemStack,
                                     float equippedProgress,
                                     PoseStack poseStack,
                                     MultiBufferSource buffer,
                                     int combinedLight,
                                     CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            VRHandRenderer.renderHandItem(poseStack, buffer, combinedLight, hand);
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void onRenderPlayerArm(PoseStack poseStack,
                                   MultiBufferSource buffer,
                                   int combinedLight,
                                   float equippedProgress,
                                   float swingProgress,
                                   HumanoidArm side,
                                   CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            ci.cancel();
        }
    }
}
