package com.shiroha.mmdskin.render.pipeline;

import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 文件职责：同步生物实体到模型运行时的姿态状态。 */
public final class LivingEntityModelStateHelper {
    private static final NativeScenePort NOOP_SCENE_PORT = new NativeScenePort() {
        @Override
        public void setHeadAngle(long modelHandle, float x, float y, float z, boolean worldSpace) {
        }

        @Override
        public void setModelPositionAndYaw(long modelHandle, float x, float y, float z, float yawRadians) {
        }

        @Override
        public void setAutoBlinkEnabled(long modelHandle, boolean enabled) {
        }

        @Override
        public void setEyeTrackingEnabled(long modelHandle, boolean enabled) {
        }

        @Override
        public void setEyeMaxAngle(long modelHandle, float maxAngle) {
        }

        @Override
        public void setEyeAngle(long modelHandle, float eyeX, float eyeY) {
        }
    };

    private static final float MODEL_SCALE = 0.09f;
    private static volatile NativeScenePort scenePort = NOOP_SCENE_PORT;

    private LivingEntityModelStateHelper() {
    }

    public static void configureRuntimeCollaborators(NativeScenePort scenePort) {
        LivingEntityModelStateHelper.scenePort = scenePort != null ? scenePort : NOOP_SCENE_PORT;
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
            scenePort.setHeadAngle(modelHandle, 0.0f, 0.0f, 0.0f, context.isWorldScene());
        } else if (!vrActive) {
            HeadAngleHelper.updateHeadAngle(scenePort, modelHandle, entity, entityYaw, tickDelta, context);
            EyeTrackingHelper.updateEyeTracking(scenePort, modelHandle, entity, entityYaw, tickDelta, modelName);
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
        scenePort.setModelPositionAndYaw(modelHandle, posX, posY, posZ, bodyYaw);
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
