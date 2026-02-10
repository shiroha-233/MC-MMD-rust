package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameRenderer Mixin — 舞台模式 FOV 覆盖
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue((double) controller.getCameraFov());
        }
    }
}
