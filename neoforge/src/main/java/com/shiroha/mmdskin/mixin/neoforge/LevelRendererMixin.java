package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.neoforge.YsmCompat;
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

/**
 * LevelRenderer Mixin — 强制渲染本地玩家实体
 * 1. 第一人称 MMD 模型模式（非 VR）
 * 2. VR 模式下 MMD 模型激活（确保身体可见）
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(
        method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
        at = @At("HEAD")
    )
    private void mmdskin$beginRenderFrame(CallbackInfo ci) {
        PlayerPerformanceGate.beginRenderFrame();
    }

    @Redirect(
        method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z", ordinal = 0)
    )
    private boolean onCameraIsDetached(Camera camera) {
        if (IrisCompat.isRenderingShadows()) {
            return camera.isDetached();
        }

        // TODO_1.21.11: Camera API 改名 getXxx -> xxx
        Entity entity = camera.entity();
        if (!(entity instanceof AbstractClientPlayer player)) {
            return camera.isDetached();
        }

        String playerName = player.getName().getString();
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, true);
        boolean isMmdDefault = selectedModel == null || selectedModel.isEmpty() || selectedModel.equals("默认 (原版渲染)");
        boolean isMmdActive = !isMmdDefault;
        boolean isVanilaMmdModel = isMmdActive && (selectedModel.equals("VanillaModel") || selectedModel.equalsIgnoreCase("vanilla")
                || selectedModel.equals("VanilaModel") || selectedModel.equalsIgnoreCase("vanila"));

        // VR 模式：MMD 模型激活时强制渲染身体
        if (isMmdActive && !isVanilaMmdModel && VRArmHider.isLocalPlayerInVR()) {
            return true;
        }

        // 非 VR：第一人称模型逻辑
        if (FirstPersonManager.shouldRenderFirstPerson() && isMmdActive && !isVanilaMmdModel) {
            if (YsmCompat.isYsmModelActive(player)) {
                if (YsmCompat.isDisableSelfModel()) {
                    // TODO_1.21.11: Camera.getXRot -> xRot
                    return camera.xRot() >= 0;
                }
                return false;
            }
            // TODO_1.21.11: Camera.getXRot -> xRot
            return camera.xRot() >= 0;
        }

        return camera.isDetached();
    }
}
