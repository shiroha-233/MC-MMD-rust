package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft Mixin — 舞台模式下拦截暂停
 *
 * pauseGame: 阻止 ESC/失焦 打开 PauseScreen（由 MMDCameraController 统一处理 ESC）
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void onPauseGame(boolean showPauseMenu, CallbackInfo ci) {
        if (MMDCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
