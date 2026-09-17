package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gui Mixin — 舞台模式下拦截游戏按键（26.2 中 handleKeybinds 从 Minecraft 迁移到 Gui）
 */
@Mixin(Gui.class)
public abstract class MinecraftGuiMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void onHandleKeybinds(CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            ci.cancel();
        }
    }
}
