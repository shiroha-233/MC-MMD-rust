package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GameRenderer Mixin — 舞台模式 FOV 覆盖与 Roll 注入 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue((double) controller.getCameraFov());
        }
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void onApplyCameraRoll(float partialTick, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            float roll = controller.getCameraRoll();
            if (roll != 0.0f) {
                poseStack.mulPose(new Quaternionf().rotationZ(roll));
            }
        }
    }
}

