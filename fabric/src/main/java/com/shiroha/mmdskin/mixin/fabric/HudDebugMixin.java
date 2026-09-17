// 负责在 26.2 HUD 渲染时挂接 MMD 调试面板（fabric-api HudRenderCallback 已移除）。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.debug.client.PerformanceHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudDebugMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mmdskin$renderDebugHud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker,
                                        CallbackInfo ci) {
        PerformanceHud.render(guiGraphics);
    }
}
