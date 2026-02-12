package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.core.FirstPersonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 实体 Mixin — 修复第一人称下的交互射线起点 (Fabric)
 *
 * 当开启第一人称 MMD 渲染时，相机位置已经移动到了模型眼睛骨骼处。
 * 为了保持准星指向与实际交互（方块破坏、攻击）一致，
 * 必须让实体的眼睛位置（交互射线起点）也同步到骨骼位置。
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

                // 非第一人称，仅返回骨骼位置
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
