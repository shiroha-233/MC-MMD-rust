/** 文件职责：隐藏第一人称原版手臂并在 VR 下接管手持物渲染。 */
package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.neoforge.compat.YsmCompat;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {
    /**
     * 26.3 的变化：{@code ItemInHandRenderer} 被拆成
     * {@code FirstPersonHandsAndItemsRenderer}，手臂与手持物的绘制合并进了
     * {@code submitHandsWithItems(...)}；旧的 {@code renderArmWithItem} /
     * {@code renderPlayerArm} 两个方法已经不存在，因此这两处注入合并到下面这一个。
     */
    @Inject(
            method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void onSubmitHandsWithItems(float partialTick,
                                        PoseStack poseStack,
                                        SubmitNodeCollector collector,
                                        PlayerRenderState playerState,
                                        FirstPersonHandsAndItemsRenderState handsState,
                                        CallbackInfo ci) {
        if (VRArmHider.shouldHideVRArms()) {
            ci.cancel();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
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
