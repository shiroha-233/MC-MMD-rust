// 文件职责：NeoForge 端 KeyboardInput Mixin，舞台模式下清零移动输入
package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void onStageTick(CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }
    }
}
