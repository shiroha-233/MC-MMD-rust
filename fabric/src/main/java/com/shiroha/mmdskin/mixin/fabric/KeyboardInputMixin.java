package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyboardInput Mixin — 舞台模式下清零移动输入
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onStageTick(boolean isSneaking, float sneakSpeedModifier, CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            this.up = false;
            this.down = false;
            this.left = false;
            this.right = false;
            this.forwardImpulse = 0.0f;
            this.leftImpulse = 0.0f;
            this.jumping = false;
            this.shiftKeyDown = false;
        }
    }
}

