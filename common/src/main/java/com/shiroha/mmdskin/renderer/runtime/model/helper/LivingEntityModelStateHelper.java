package com.shiroha.mmdskin.renderer.runtime.model.helper;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 文件职责：同步生物实体到模型运行时的姿态状态。
 */
public final class LivingEntityModelStateHelper {
    private static final float MODEL_SCALE = 0.09f;

    private LivingEntityModelStateHelper() {
    }

    public static void syncModelState(NativeFunc nativeFunc,
                                      long modelHandle,
                                      LivingEntity entity,
                                      float entityYaw,
                                      float tickDelta,
                                      RenderContext context,
                                      String modelName,
                                      boolean stagePlaying,
                                      boolean vrActive) {
        if (stagePlaying) {
            nativeFunc.SetHeadAngle(modelHandle, 0.0f, 0.0f, 0.0f, context.isWorldScene());
        } else if (!vrActive) {
            HeadAngleHelper.updateHeadAngle(nativeFunc, modelHandle, entity, entityYaw, tickDelta, context);
            EyeTrackingHelper.updateEyeTracking(nativeFunc, modelHandle, entity, entityYaw, tickDelta, modelName);
        }

        Vec3 renderOrigin = entity instanceof Player player
                ? resolveRenderOrigin(player, tickDelta)
                : new Vec3(
                        Mth.lerp(tickDelta, entity.xo, entity.getX()),
                        Mth.lerp(tickDelta, entity.yo, entity.getY()),
                        Mth.lerp(tickDelta, entity.zo, entity.getZ())
                );
        if (entity instanceof Player player) {
            renderOrigin = renderOrigin.add(FirstPersonManager.getLocalVrModelRootOffset(player));
        }

        float posX = (float) (renderOrigin.x * MODEL_SCALE);
        float posY = (float) (renderOrigin.y * MODEL_SCALE);
        float posZ = (float) (renderOrigin.z * MODEL_SCALE);
        float bodyYaw = entity instanceof Player player
                ? resolveBodyYaw(player, tickDelta)
                : Mth.rotLerp(tickDelta, entity.yBodyRotO, entity.yBodyRot) * ((float) Math.PI / 180F);
        nativeFunc.SetModelPositionAndYaw(modelHandle, posX, posY, posZ, bodyYaw);
    }

    private static Vec3 resolveRenderOrigin(Player player, float tickDelta) {
        Vec3 renderOrigin = FirstPersonManager.vrRuntime().getRenderOrigin(player, tickDelta);
        if (renderOrigin != null) {
            return renderOrigin;
        }
        return new Vec3(
                Mth.lerp(tickDelta, player.xo, player.getX()),
                Mth.lerp(tickDelta, player.yo, player.getY()),
                Mth.lerp(tickDelta, player.zo, player.getZ())
        );
    }

    private static float resolveBodyYaw(Player player, float tickDelta) {
        float vrBodyYaw = FirstPersonManager.vrRuntime().getBodyYawRad(player, tickDelta);
        if (Float.isFinite(vrBodyYaw)) {
            return vrBodyYaw;
        }
        return Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180F);
    }
}
