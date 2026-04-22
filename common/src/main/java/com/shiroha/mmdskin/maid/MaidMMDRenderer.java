package com.shiroha.mmdskin.maid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.voice.runtime.VoicePlaybackManager;
import java.util.UUID;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

/** 文件职责：负责女仆实体的 MMD 渲染与基础动画切换。 */
public class MaidMMDRenderer {
    private static final Logger logger = LogManager.getLogger();

    public static boolean render(
            LivingEntity entity,
            UUID maidUUID,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            int packedLight) {
        if (!MaidMMDModelManager.hasMMDModel(maidUUID)) {
            return false;
        }

        ManagedModel modelData = MaidMMDModelManager.getModel(maidUUID);
        if (modelData == null || modelData.modelInstance() == null) {
            return false;
        }

        try {
            updateAnimationState(entity, modelData);
            VoicePlaybackManager.getInstance().onMaidFrame(
                    entity,
                    maidUUID.toString(),
                    MaidMMDModelManager.getBindingModelName(maidUUID));

            Vector3f entityTrans = getEntityTranslation(modelData);
            float modelSize = modelData.renderProperties().modelScale();
            poseStack.scale(modelSize, modelSize, modelSize);

            float originalXRot = entity.getXRot();
            float originalXRotO = entity.xRotO;
            if (modelSize != 1.0f && modelSize > 0.0f) {
                entity.setXRot(originalXRot * modelSize);
                entity.xRotO = originalXRotO * modelSize;
            }

            RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
            modelData.modelInstance().render(
                    entity,
                    entityYaw,
                    0.0f,
                    entityTrans,
                    partialTicks,
                    poseStack,
                    packedLight,
                    RenderScene.WORLD);

            if (modelSize != 1.0f && modelSize > 0.0f) {
                entity.setXRot(originalXRot);
                entity.xRotO = originalXRotO;
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to render maid MMD model {}", maidUUID, e);
            return false;
        }
    }

    private static void updateAnimationState(LivingEntity entity, ManagedModel modelData) {
        EntityAnimState entityData = modelData.entityState();
        EntityAnimState.State targetState;

        if (entity.getHealth() <= 0) {
            targetState = EntityAnimState.State.Die;
        } else if (entity.isSleeping()) {
            targetState = EntityAnimState.State.Sleep;
        } else if (entity.isPassenger()) {
            targetState = EntityAnimState.State.Ride;
        } else if (entity.isSwimming()) {
            targetState = EntityAnimState.State.Swim;
        } else if (entity.onClimbable()) {
            targetState = EntityAnimState.State.OnClimbable;
        } else if (entity.isSprinting()) {
            targetState = EntityAnimState.State.Sprint;
        } else if (hasMovement(entity)) {
            targetState = EntityAnimState.State.Walk;
        } else {
            targetState = EntityAnimState.State.Idle;
        }

        if (entityData.stateLayers[0] != targetState) {
            entityData.stateLayers[0] = targetState;
            String animationName = EntityAnimState.getPropertyName(targetState);
            modelData.modelInstance().changeAnim(modelData.animationLibrary().animation(animationName), 0);
        }
    }

    private static boolean hasMovement(LivingEntity entity) {
        return entity.getX() - entity.xo != 0.0 || entity.getZ() - entity.zo != 0.0;
    }

    private static Vector3f getEntityTranslation(ManagedModel modelData) {
        String translation = modelData.properties.getProperty("entityTrans");
        if (translation == null || translation.isBlank()) {
            return new Vector3f();
        }
        return com.shiroha.mmdskin.util.VectorParseUtil.parseVec3f(translation);
    }

    public static void setEntityVelocity(UUID maidUUID, float vx, float vy, float vz) {
        ManagedModel modelData = MaidMMDModelManager.getModel(maidUUID);
        if (modelData != null && modelData.modelInstance() != null) {
        }
    }
}
