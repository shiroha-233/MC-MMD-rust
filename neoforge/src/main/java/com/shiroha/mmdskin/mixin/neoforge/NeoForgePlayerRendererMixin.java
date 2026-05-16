/* 文件职责：NeoForge 侧拦截玩家渲染入口（AvatarRenderer.submit），为启用 MMD 替换的玩家阻断原版渲染。 */
package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.neoforge.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.renderer.integration.player.PlayerPerformanceGate;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class NeoForgePlayerRendererMixin {

    private static final String DEFAULT_RENDER_LABEL = "默认 (原版渲染)";

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mmdskin$onSubmit(LivingEntityRenderState renderState, PoseStack poseStack,
                                  SubmitNodeCollector collector, CameraRenderState cameraRenderState,
                                  CallbackInfo ci) {
        if (!(renderState instanceof AvatarRenderState avatarState)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(avatarState.id);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        boolean isLocalFirstPerson = isLocalPlayer && mc.options.getCameraType().isFirstPerson();
        if (isLocalFirstPerson && !FirstPersonManager.shouldRenderFirstPerson()
                && !VRArmHider.isLocalPlayerInVR()) {
            return;
        }

        if (YsmCompat.isYsmActive(player)) {
            return;
        }

        String selectedModel = PlayerModelSyncManager.getPlayerModel(
                player.getUUID(), player.getName().getString(), isLocalPlayer);
        if (selectedModel == null || selectedModel.isEmpty()
                || DEFAULT_RENDER_LABEL.equals(selectedModel)
                || player.isSpectator()) {
            return;
        }

        if (!PlayerPerformanceGate.allowsMmd(player)) {
            return;
        }

        ci.cancel();
    }
}
