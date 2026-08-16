// 负责把 Fabric 相机入口适配到舞台相机与实例化第一人称相机模块。
// 26.2 适配：Camera.setup -> update(DeltaTracker)；实体/偏转量从 Camera 状态获取。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.fabric.compat.YsmCompat;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(DeltaTracker deltaTracker, CallbackInfo callback) {
        Camera camera = (Camera) (Object) this;
        MMDCameraController stageCamera = MMDCameraController.getInstance();
        if (stageCamera.isActive()) {
            stageCamera.checkEscapeKey();
            if (stageCamera.isActive()) {
                stageCamera.updateCamera();
                if (stageCamera.isActive()) {
                    setPosition(stageCamera.getCameraX(), stageCamera.getCameraY(), stageCamera.getCameraZ());
                    setRotation(stageCamera.getCameraYaw(), stageCamera.getCameraPitch());
                }
            }
            return;
        }

        Entity entity = camera.entity();
        if (entity instanceof LivingEntity living
                && YsmCompat.isYsmModelActive(living)
                && !YsmCompat.isDisableSelfModel()) {
            return;
        }
        var runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime == null) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        runtime.firstPerson().camera()
                .resolve(entity, partialTick, camera.isDetached())
                .ifPresent(pose -> {
                    setPosition(pose.position().x, pose.position().y, pose.position().z);
                    if (pose.applyRotation()) {
                        setRotation(pose.yaw(), pose.pitch());
                    }
                });
    }

    // 26.2 FOV 计算迁移到 Camera#getFov（原 GameRenderer#getFov 已移除）
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(CallbackInfoReturnable<Float> cir) {
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            cir.setReturnValue(controller.getCameraFov());
        }
    }
}
