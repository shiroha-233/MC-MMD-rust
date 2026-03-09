package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft Mixin — 舞台模式下拦截暂停和游戏按键
 *
 * pauseGame: 阻止 ESC/失焦 打开 PauseScreen（由 MMDCameraController 统一处理 ESC）
 * handleKeybinds: 阻止舞台播放期间的攻击、使用物品等游戏操作
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void onPauseGame(boolean showPauseMenu, CallbackInfo ci) {
        if (MMDCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void onHandleKeybinds(CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            ci.cancel();
        }
    }
}

