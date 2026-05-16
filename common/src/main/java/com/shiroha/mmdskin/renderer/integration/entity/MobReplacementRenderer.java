/* 文件职责：渲染按配置启用的原版生物 MMD 替换模型。 */
package com.shiroha.mmdskin.renderer.integration.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.shiroha.mmdskin.renderer.integration.ModelPropertyHelper;
import com.shiroha.mmdskin.renderer.integration.player.PlayerPerformanceGate;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MobReplacementRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final RenderParams REUSABLE_PARAMS = new RenderParams();

    private MobReplacementRenderer() {
    }

    public static boolean render(LivingEntity entity, float entityYaw, float tickDelta, PoseStack poseStack, int packedLight) {
        String modelName = MobReplacementService.getReplacementModelName(entity);
        if (modelName == null || !PlayerPerformanceGate.allowsMobReplacement(entity)) {
            return false;
        }

        try {
            MMDModelManager.Model model = MMDModelManager.GetModel(modelName, "mob_" + entity.getStringUUID());
            if (model == null || model.model == null) {
                return false;
            }

            model.loadModelProperties(false);
            float[] size = ModelPropertyHelper.getModelSize(model.properties);

            REUSABLE_PARAMS.reset();
            EntityAnimationResolver.resolve(entity, model, entityYaw, tickDelta, REUSABLE_PARAMS);

            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, 0.01D, 0.0D);
                if (entity.isBaby()) {
                    poseStack.scale(0.5F, 0.5F, 0.5F);
                }
                poseStack.scale(size[0], size[0], size[0]);
                // TODO_1.21.11: 渲染管线重写 - RenderSystem.setShader 已被移除
                // RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
                model.model.render(entity, REUSABLE_PARAMS.bodyYaw, REUSABLE_PARAMS.bodyPitch, REUSABLE_PARAMS.translation,
                        tickDelta, poseStack, packedLight, RenderContext.WORLD);
                return true;
            } finally {
                poseStack.popPose();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to render mob replacement for {}", entity.getType(), exception);
            return false;
        }
    }
}
