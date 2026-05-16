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

/**
 * KeyboardInput Mixin — 舞台模式下清零移动输入
 * TODO_1.21.11: KeyboardInput.tick() 已变为无参；输入字段重构为 Input record (keyPresses) + Vec2 moveVector
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void onStageTick(CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            // TODO_1.21.11: 输入字段重构 — 用 Input.EMPTY 与 Vec2.ZERO 清零
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }
    }
}
