package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.renderer.core.FirstPersonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity Mixin — 第一人称视线同步
 * 
 * 修正第一人称模式下实体眼睛位置和视向量。
 * 这对于解决十字准星交互偏移至关重要。
 */
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

                    // 兜底逻辑：手动计算，防止相机未初始化时崩溃
                    Vec3 bonePos = FirstPersonManager.getRotatedEyePosition(entity, partialTick);
                    float originalYaw = entity.getViewYRot(partialTick);
                    float originalPitch = entity.getViewXRot(partialTick);
                    float lookPitchRad = originalPitch * ((float) Math.PI / 180F);
                    float lookYawRad = originalYaw * ((float) Math.PI / 180F);
                    float cosLookPitch = net.minecraft.util.Mth.cos(lookPitchRad);
                    float sinLookPitch = net.minecraft.util.Mth.sin(lookPitchRad);
                    float cosLookYaw = net.minecraft.util.Mth.cos(lookYawRad);
                    float sinLookYaw = net.minecraft.util.Mth.sin(lookYawRad);

                    double forwardOffset = com.shiroha.mmdskin.config.ConfigManager.getFirstPersonCameraForwardOffset();
                    double verticalOffset = com.shiroha.mmdskin.config.ConfigManager.getFirstPersonCameraVerticalOffset();

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
                        float cosYaw = net.minecraft.util.Mth.cos(yawRad);
                        float sinYaw = net.minecraft.util.Mth.sin(yawRad);
                        float cosPitch = net.minecraft.util.Mth.cos(pitchRad);
                        float sinPitch = net.minecraft.util.Mth.sin(pitchRad);
                        cir.setReturnValue(new Vec3((double) (sinYaw * cosPitch), (double) (-sinPitch), (double) (cosYaw * cosPitch)));
                    }
                }
            }
        }
    }
}
