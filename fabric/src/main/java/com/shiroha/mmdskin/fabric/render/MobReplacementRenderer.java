/* 文件职责：负责 Fabric 侧原版生物的 MMD 替换渲染。 */
package com.shiroha.mmdskin.fabric.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.shiroha.mmdskin.renderer.integration.entity.EntityAnimationResolver;
import com.shiroha.mmdskin.renderer.integration.ModelPropertyHelper;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            MMDModelManager.Model model = MMDModelManager.GetModel(modelName, "mob_" + entity.getStringUUID());
            if (model == null || model.model == null) {
                return false;
            }

            model.loadModelProperties(false);
            float[] size = ModelPropertyHelper.getModelSize(model.properties);

            RenderParams params = new RenderParams();
            EntityAnimationResolver.resolve(entity, model, entityYaw, tickDelta, params);

            poseStack.pushPose();
            try {
                poseStack.translate(0.0D, 0.01D, 0.0D);
                if (entity.isBaby()) {
                    poseStack.scale(0.5F, 0.5F, 0.5F);
                }
                poseStack.scale(size[0], size[0], size[0]);
                model.model.render(entity, params.bodyYaw, params.bodyPitch, params.translation,
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
