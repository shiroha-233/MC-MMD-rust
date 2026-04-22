package com.shiroha.mmdskin.render.pipeline;

import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.compat.vr.VRDataProvider;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 文件职责：同步生物实体到模型运行时的姿态状态。 */
public final class LivingEntityModelStateHelper {

    private static final float MODEL_SCALE = 0.09f;

    private LivingEntityModelStateHelper() {
    }

    public static void syncModelState(long modelHandle,
                                      LivingEntity entity,
                                      float entityYaw,
                                      float tickDelta,
                                      RenderScene context,
                                      String modelName,
                                      boolean stagePlaying,
                                      boolean vrActive) {
        if (stagePlaying) {
            NativeRuntimeBridgeHolder.get().setHeadAngle(modelHandle, 0.0f, 0.0f, 0.0f, context.isWorldScene());
        } else if (!vrActive) {
            HeadAngleHelper.updateHeadAngle(modelHandle, entity, entityYaw, tickDelta, context);
            EyeTrackingHelper.updateEyeTracking(modelHandle, entity, entityYaw, tickDelta, modelName);
        }

        Vec3 renderOrigin = entity instanceof Player player
                ? VRDataProvider.getRenderOrigin(player, tickDelta)
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
                ? VRDataProvider.getBodyYawRad(player, tickDelta)
                : Mth.rotLerp(tickDelta, entity.yBodyRotO, entity.yBodyRot) * ((float) Math.PI / 180F);
        NativeRuntimeBridgeHolder.get().setModelPositionAndYaw(modelHandle, posX, posY, posZ, bodyYaw);
    }
}
