package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "pick", at = @At("RETURN"), require = 0)
    private void mmdskin$adjustPickResult(float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !FirstPersonManager.shouldUseVanillaReachValidation(player)) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 viewDir = player.getViewVector(partialTick);
        if (viewDir.lengthSqr() < 1.0E-6) {
            return;
        }

        Vec3 vanillaEyePos = FirstPersonManager.getVanillaEyePosition(player, partialTick);
        double blockRange = player.blockInteractionRange();
        double entityRange = player.entityInteractionRange();
        HitResult missHit = player.pick(0.0D, partialTick, false);
        HitResult blockHit = player.pick(blockRange, partialTick, false);
        HitResult vanillaRayBlockHit = mc.level.clip(
                new ClipContext(
                        vanillaEyePos,
                        vanillaEyePos.add(viewDir.scale(blockRange)),
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player
                )
        );
        HitResult allowedBlockHit = mmdskin$filterBlockHitByVanillaReach(blockHit, vanillaRayBlockHit);
        HitResult finalHit = allowedBlockHit != null ? allowedBlockHit : missHit;
        mc.crosshairPickEntity = null;

        if (entityRange > 1.0E-6D) {
            Vec3 entityEnd = cameraPos.add(viewDir.scale(entityRange));
            AABB searchBox = player.getBoundingBox().expandTowards(viewDir.scale(entityRange)).inflate(1.0D, 1.0D, 1.0D);
            double maxDistanceSqr = mmdskin$getHitDistanceSqr(cameraPos, blockHit, entityRange);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    player,
                    cameraPos,
                    entityEnd,
                    searchBox,
                    target -> !target.isSpectator() && target.isPickable(),
                    maxDistanceSqr
            );
            EntityHitResult allowedEntityHit = mmdskin$filterEntityHitByVanillaReach(vanillaEyePos, entityHit, entityRange);
            if (allowedEntityHit != null) {
                double entityDistanceSqr = cameraPos.distanceToSqr(allowedEntityHit.getLocation());
                double blockDistanceSqr = mmdskin$getHitDistanceSqr(cameraPos, allowedBlockHit, entityRange);
                if (entityDistanceSqr <= entityRange * entityRange + 1.0E-6D && entityDistanceSqr <= blockDistanceSqr) {
                    finalHit = allowedEntityHit;
                    Entity target = allowedEntityHit.getEntity();
                    if (target instanceof LivingEntity || target instanceof ItemFrame) {
                        mc.crosshairPickEntity = target;
                    }
                }
            }
        }

        mc.hitResult = finalHit;

    }

    private static HitResult mmdskin$filterBlockHitByVanillaReach(HitResult cameraHit, HitResult vanillaHit) {
        if (cameraHit == null || vanillaHit == null) {
            return null;
        }

        if (cameraHit.getType() != HitResult.Type.BLOCK || vanillaHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockHitResult cameraBlockHit = (BlockHitResult) cameraHit;
        BlockHitResult vanillaBlockHit = (BlockHitResult) vanillaHit;
        return cameraBlockHit.getBlockPos().equals(vanillaBlockHit.getBlockPos())
                ? cameraHit
                : null;
    }

    private static EntityHitResult mmdskin$filterEntityHitByVanillaReach(Vec3 vanillaEyePos, EntityHitResult hitResult, double maxRange) {
        if (hitResult == null) {
            return null;
        }
        return vanillaEyePos.distanceToSqr(hitResult.getLocation()) <= maxRange * maxRange + 1.0E-6D
                ? hitResult
                : null;
    }

    private static double mmdskin$getHitDistanceSqr(Vec3 cameraPos, HitResult hitResult, double fallbackRange) {
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return fallbackRange * fallbackRange;
        }
        return cameraPos.distanceToSqr(hitResult.getLocation());
    }
}

