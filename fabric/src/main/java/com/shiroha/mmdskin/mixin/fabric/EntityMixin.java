package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 实体 Mixin，用于同步第一人称 MMD 视角下的眼睛位置与视线向量。 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void onGetEyePosition(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (FirstPersonManager.isActive() && FirstPersonManager.isEyeBoneValid()) {
            Entity entity = (Entity) (Object) this;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getUUID().equals(entity.getUUID())) {
                if (mc.options.getCameraType().isFirstPerson()) {
                    net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
                    if (camera.isInitialized() && camera.getEntity() == entity) {
                        cir.setReturnValue(camera.getPosition());
                        return;
                    }

                    Vec3 bonePos = FirstPersonManager.getRotatedEyePosition(entity, partialTick);
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

                    double targetX = bonePos.x + (double) (sinLookYaw * cosLookPitch * (float) (-forwardOffset));
                    double targetY = bonePos.y + (double) (sinLookPitch * (float) (-forwardOffset)) + verticalOffset;
                    double targetZ = bonePos.z + (double) (cosLookYaw * cosLookPitch * (float) forwardOffset);

                    cir.setReturnValue(new Vec3(targetX, targetY, targetZ));
                    return;
                }

                cir.setReturnValue(FirstPersonManager.getRotatedEyePosition(entity, partialTick));
            }
        }
    }

    @Inject(method = "getViewVector(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void onGetViewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (FirstPersonManager.isActive() && FirstPersonManager.isEyeBoneValid()) {
            Entity entity = (Entity) (Object) this;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getUUID().equals(entity.getUUID())) {
                if (mc.options.getCameraType().isFirstPerson()) {
                    net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
                    if (camera.isInitialized() && camera.getEntity() == entity) {
                        float originalYaw = camera.getYRot();
                        float originalPitch = camera.getXRot();
                        float pitchRad = originalPitch * ((float) Math.PI / 180F);
                        float yawRad = -originalYaw * ((float) Math.PI / 180F);
                        float cosYaw = Mth.cos(yawRad);
                        float sinYaw = Mth.sin(yawRad);
                        float cosPitch = Mth.cos(pitchRad);
                        float sinPitch = Mth.sin(pitchRad);
                        cir.setReturnValue(new Vec3((double) (sinYaw * cosPitch), (double) (-sinPitch), (double) (cosYaw * cosPitch)));
                    }
                }
            }
        }
    }
}
