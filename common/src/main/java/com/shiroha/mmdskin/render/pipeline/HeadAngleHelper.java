package com.shiroha.mmdskin.render.pipeline;

import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** 文件职责：计算并同步模型头部朝向。 */
public final class HeadAngleHelper {

    private static final float MAX_PITCH = 50.0f;

    private static final float MAX_YAW = 80.0f;

    private HeadAngleHelper() {
    }

    public static void updateHeadAngle(NativeScenePort scenePort,
                                       long modelHandle,
                                       LivingEntity entity,
                                       float entityYaw,
                                       float tickDelta,
                                       RenderScene context) {
        float headAngleX = Mth.clamp(entity.getXRot(), -MAX_PITCH, MAX_PITCH);
        float headYaw = Mth.lerp(tickDelta, entity.yHeadRotO, entity.yHeadRot);
        float headAngleY = (entityYaw - headYaw) % 360.0f;

        if (headAngleY < -180.0f) {
            headAngleY += 360.0f;
        } else if (headAngleY > 180.0f) {
            headAngleY -= 360.0f;
        }

        headAngleY = Mth.clamp(headAngleY, -MAX_YAW, MAX_YAW);

        float pitchRad = headAngleX * ((float) Math.PI / 180F);
        float yawRad = headAngleY * ((float) Math.PI / 180F);
        if (context.isInventoryScene()) {
            yawRad = -yawRad;
        }

        scenePort.setHeadAngle(modelHandle, pitchRad, yawRad, 0.0f, context.isWorldScene());
    }
}
