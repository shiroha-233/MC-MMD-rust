package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.core.FirstPersonManager;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 相机 Mixin — 舞台模式相机接管 & 第一人称 MMD 模型相机高度调整
 * 在 Camera.setup() 尾部覆盖位置和旋转
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void onSetup(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        // 舞台模式相机接管
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            controller.checkEscapeKey();
            if (!controller.isActive()) return;
            controller.updateCamera();
            if (!controller.isActive()) return;
            setPosition(controller.getCameraX(), controller.getCameraY(), controller.getCameraZ());
            setRotation(controller.getCameraYaw(), controller.getCameraPitch());
            return;
        }

        // 第一人称 MMD 模型相机：跟踪眼睛骨骼动画位置
        if (FirstPersonManager.isActive() && FirstPersonManager.isEyeBoneValid() && !detached) {
            float[] eyeOffset = new float[3];
            FirstPersonManager.getEyeWorldOffset(eyeOffset);
            {
                double px = Mth.lerp(partialTick, entity.xo, entity.getX());
                double py = Mth.lerp(partialTick, entity.yo, entity.getY());
                double pz = Mth.lerp(partialTick, entity.zo, entity.getZ());
                float bodyYaw = (entity instanceof LivingEntity le)
                    ? Mth.rotLerp(partialTick, le.yBodyRotO, le.yBodyRot)
                    : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
                float yawRad = (float) Math.toRadians(bodyYaw);
                double sinYaw = Mth.sin(yawRad);
                double cosYaw = Mth.cos(yawRad);
                double worldOffX = eyeOffset[0] * cosYaw - eyeOffset[2] * sinYaw;
                double worldOffZ = eyeOffset[0] * sinYaw + eyeOffset[2] * cosYaw;
                setPosition(px + worldOffX, py + eyeOffset[1], pz + worldOffZ);
            }
        }
    }
}
