/** 文件职责：隐藏第一人称原版手臂并在 VR 下接管手持物渲染。
    26.2 适配：renderHandsWithItems -> submitHandsWithItems；renderArmWithItem/renderPlayerArm 已移除。 */
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.compat.vr.VRHandRenderer;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.fabric.compat.YsmCompat;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void onSubmitHandsWithItems(float partialTick,
                                        PoseStack poseStack,
                                        SubmitNodeCollector collector,
                                        LocalPlayer player,
                                        int packedLight,
                                        CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            VRHandRenderer.renderHandItem(poseStack, collector, packedLight, InteractionHand.MAIN_HAND);
            VRHandRenderer.renderHandItem(poseStack, collector, packedLight, InteractionHand.OFF_HAND);
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

        var runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime != null && runtime.firstPerson().session().desktopActive()
                && hasMmdModel && !useVanillaModel) {
            ci.cancel();
        }
    }
}
