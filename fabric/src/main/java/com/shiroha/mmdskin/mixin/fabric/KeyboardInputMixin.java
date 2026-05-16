package com.shiroha.mmdskin.mixin.fabric;

import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyboardInput Mixin — 舞台模式下清零移动输入
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    // TODO_1.21.11: Mixin 目标已变 - ClientInput 字段重构为 keyPresses(Input record) 与 moveVector，需要新方案重写舞台输入屏蔽
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void onStageTick(CallbackInfo ci) {
        // 原逻辑：在 stage 模式下清空移动/跳跃/潜行字段。1.21.11 字段已不存在，待重写为替换 keyPresses 记录。
    }
}
