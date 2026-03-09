package com.shiroha.mmdskin.mixin.forge;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MouseHandler Mixin — 舞台模式下拦截鼠标点击和视角旋转
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void onStageMousePress(long window, int button, int action, int mods, CallbackInfo ci) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (!controller.shouldBlockInput()) return;

        if (action == 1 && button == 1 && controller.isPlaying()) {
            controller.toggleMouseGrab();
        }
        ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onStageTurnPlayer(CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            ci.cancel();
        }
    }
}

