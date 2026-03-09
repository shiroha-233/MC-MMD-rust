package com.shiroha.mmdskin.mixin.forge;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.forge.YsmCompat;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 相机 Mixin，用于接管舞台模式与第一人称 MMD 相机位置。 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void onSetup(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {

        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            controller.checkEscapeKey();
            if (controller.isActive()) {
                controller.updateCamera();
                if (controller.isActive()) {
                    this.setPosition(controller.getCameraX(), controller.getCameraY(), controller.getCameraZ());
                    this.setRotation(controller.getCameraYaw(), controller.getCameraPitch());
                }
            }
        } else {

            if (FirstPersonManager.isActive() && FirstPersonManager.isEyeBoneValid() && !detached) {

                if (entity instanceof LivingEntity living) {
                    boolean ysmActive = YsmCompat.isYsmModelActive(living);
                    boolean ysmDisableSelf = YsmCompat.isDisableSelfModel();
                    if (ysmActive && !ysmDisableSelf) {
                        return;
                    }
                }

                Vec3 boneEyePos = FirstPersonManager.getRotatedEyePosition(entity, partialTick);
                float originalYaw = entity.getViewYRot(partialTick);
                float originalPitch = entity.getViewXRot(partialTick);
                float lookPitchRad = originalPitch * ((float) Math.PI / 180F);
                float lookYawRad = originalYaw * ((float) Math.PI / 180F);
                float cosLookPitch = Mth.cos(lookPitchRad);
                float sinLookPitch = Mth.sin(lookPitchRad);
                float cosLookYaw = Mth.cos(lookYawRad);
                float sinLookYaw = Mth.sin(lookYawRad);

                double forwardOffset = ConfigManager.getFirstPersonCameraForwardOffset();
                double verticalOffset = ConfigManager.getFirstPersonCameraVerticalOffset();

                double rotatedY = verticalOffset * cosLookPitch - forwardOffset * sinLookPitch;
                double horizontalDist = verticalOffset * sinLookPitch + forwardOffset * cosLookPitch;

                double targetX = boneEyePos.x + (double) (sinLookYaw * (float) (-horizontalDist));
                double targetY = boneEyePos.y + (double) rotatedY;
                double targetZ = boneEyePos.z + (double) (cosLookYaw * (float) horizontalDist);

                Vec3 finalPos = new Vec3(targetX, targetY, targetZ);
                FirstPersonManager.setLastCameraPos(finalPos);

                this.setPosition(finalPos.x, finalPos.y, finalPos.z);
                this.setRotation(originalYaw, originalPitch);
            }
        }
    }
}
