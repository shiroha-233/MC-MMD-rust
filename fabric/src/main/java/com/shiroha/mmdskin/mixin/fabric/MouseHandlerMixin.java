/**
 * MouseHandler Mixin — 舞台模式下拦截鼠标点击和视角旋转。
 * 26.2 适配：onPress(long,int,int,int) -> onButton(long,MouseButtonInfo,int)。
 */
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onStageMousePress(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (!controller.shouldBlockInput()) return;

        if (action == 1 && info.button() == 1 && controller.isPlaying()) {
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
