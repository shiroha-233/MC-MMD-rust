package com.shiroha.mmdskin.renderer.runtime.model.helper;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * LivingEntity 模型状态同步辅助类。
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

        float posX = (float) (Mth.lerp(tickDelta, entity.xo, entity.getX()) * MODEL_SCALE);
        float posY = (float) (Mth.lerp(tickDelta, entity.yo, entity.getY()) * MODEL_SCALE);
        float posZ = (float) (Mth.lerp(tickDelta, entity.zo, entity.getZ()) * MODEL_SCALE);
        float bodyYaw = Mth.lerp(tickDelta, entity.yBodyRotO, entity.yBodyRot) * ((float) Math.PI / 180F);
        nativeFunc.SetModelPositionAndYaw(modelHandle, posX, posY, posZ, bodyYaw);
    }
}
