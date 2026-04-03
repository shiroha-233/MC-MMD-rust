package com.shiroha.mmdskin.renderer.integration.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.shiroha.mmdskin.renderer.integration.ModelPropertyHelper;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
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

        try {
            MMDModelManager.Model model = MMDModelManager.GetModel(modelName, entity.getStringUUID());
            if (model == null || model.model == null || model.entityData == null) {
                return false;
            }

            model.loadModelProperties(false);
            float modelScale = ModelPropertyHelper.getModelSize(model.properties)[0];

            RenderParams params = new RenderParams();
            EntityAnimationResolver.resolve(entity, model, entityYaw, tickDelta, params);

            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, 0.01D, 0.0D);

                if (entity.isBaby()) {
                    poseStack.scale(0.5f, 0.5f, 0.5f);
                }

                poseStack.scale(modelScale, modelScale, modelScale);
                RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
                model.model.render(entity, params.bodyYaw, params.bodyPitch, params.translation,
                    tickDelta, poseStack, packedLight, RenderContext.WORLD);
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
