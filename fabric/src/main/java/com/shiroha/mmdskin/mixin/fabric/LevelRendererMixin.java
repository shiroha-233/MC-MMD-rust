package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.fabric.YsmCompat;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.renderer.integration.player.PlayerPerformanceGate;
import com.shiroha.mmdskin.renderer.compat.IrisCompat;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LevelRenderer Mixin，用于在 MMD 第一人称与 VR 场景下决定本地玩家是否强制渲染。 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void mmdskin$beginRenderFrame(CallbackInfo ci) {
        PlayerPerformanceGate.beginRenderFrame();
    }

    @Redirect(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z", ordinal = 0)
    )
    private boolean onCameraIsDetached(Camera camera) {
        if (IrisCompat.isRenderingShadows()) {
            return camera.isDetached();
        }

        Entity entity = camera.entity();
        if (!(entity instanceof AbstractClientPlayer player)) {
            return camera.isDetached();
        }

        String playerName = player.getName().getString();
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, true);

        boolean isMmdDefault = selectedModel == null || selectedModel.isEmpty()
                || selectedModel.equals("默认 (原版渲染)");
        boolean isMmdActive = !isMmdDefault;
        boolean isVanilaMmdModel = isMmdActive && (selectedModel.equals("VanillaModel")
                || selectedModel.equalsIgnoreCase("vanilla")
                || selectedModel.equals("VanilaModel")
                || selectedModel.equalsIgnoreCase("vanila"));

        if (isMmdActive && !isVanilaMmdModel && VRArmHider.isLocalPlayerInVR()) {
            return true;
        }

        if (FirstPersonManager.shouldRenderFirstPerson() && isMmdActive && !isVanilaMmdModel) {

            if (YsmCompat.isYsmModelActive(player)) {
                if (YsmCompat.isDisableSelfModel()) {
                    return camera.xRot() >= 0;
                }
                return false;
            }

            return camera.xRot() >= 0;
        }

        return camera.isDetached();
    }
}
