package com.shiroha.mmdskin.render.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.policy.RenderPriorityService;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 原版生物的 MMD 替换渲染器。
 */
public final class MobReplacementRenderer {

    private static final Logger LOGGER = LogManager.getLogger();

    private MobReplacementRenderer() {
    }

    public static boolean render(LivingEntity entity, float entityYaw, float tickDelta, PoseStack poseStack, int packedLight) {
        String modelName = MobReplacementService.getReplacementModelName(entity);
        if (modelName == null) {
            return false;
        }

        if (!RenderPriorityService.get().shouldUseMobReplacement(entity)) {
            return false;
        }

        try {
            ManagedModel model = ClientRenderRuntime.get().modelRepository()
                    .acquire(ModelRequestKey.mob(entity, modelName));
            if (model == null || model.modelInstance() == null || model.entityState() == null) {
                return false;
            }

            float modelScale = model.renderProperties().modelScale();

            MutableRenderPose params = new MutableRenderPose();
            EntityAnimationResolver.resolve(entity, model, entityYaw, tickDelta, params);

            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, 0.01D, 0.0D);

                if (entity.isBaby()) {
                    poseStack.scale(0.5f, 0.5f, 0.5f);
                }

                poseStack.scale(modelScale, modelScale, modelScale);
                RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
                model.modelInstance().render(entity, params.bodyYaw, params.bodyPitch, params.translation,
                    tickDelta, poseStack, packedLight, RenderScene.WORLD);
                return true;
            } finally {
                poseStack.popPose();
            }
        } catch (Exception e) {
            LOGGER.warn("生物 MMD 渲染失败，回退原版渲染: {} -> {}", entity.getType(), modelName, e);
            return false;
        }
    }
}
