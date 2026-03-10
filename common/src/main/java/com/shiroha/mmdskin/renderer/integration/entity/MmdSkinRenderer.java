package com.shiroha.mmdskin.renderer.integration.entity;

import com.shiroha.mmdskin.renderer.integration.player.InventoryRenderHelper;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.shiroha.mmdskin.renderer.integration.ModelPropertyHelper;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * MMD 自定义实体渲染器。
 */
public class MmdSkinRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final ResourceLocation PLACEHOLDER_TEXTURE =
            new ResourceLocation(MmdSkin.MOD_ID, "textures/entity/placeholder.png");

    protected final String modelName;

    public MmdSkinRenderer(EntityRendererProvider.Context renderManager, String entityName) {
        super(renderManager);
        this.modelName = entityName.replace(':', '.');
    }

    @Override
    public void render(T entityIn, float entityYaw, float tickDelta, PoseStack matrixStackIn,
                       MultiBufferSource bufferIn, int packedLightIn) {
        super.render(entityIn, entityYaw, tickDelta, matrixStackIn, bufferIn, packedLightIn);

        MMDModelManager.Model model = MMDModelManager.GetModel(modelName, entityIn.getStringUUID());
        if (model == null) return;

        model.loadModelProperties(false);
        float[] size = parseModelSize(model);

        RenderParams params = new RenderParams();
        EntityAnimationResolver.resolve(entityIn, model, entityYaw, tickDelta, params);

        matrixStackIn.pushPose();

        if (entityIn instanceof LivingEntity living && living.isBaby()) {
            matrixStackIn.scale(0.5f, 0.5f, 0.5f);
        }

        if (InventoryRenderHelper.isInventoryScreen()) {
            renderInInventory(entityIn, model, entityYaw, tickDelta, matrixStackIn, packedLightIn, size);
        } else {
            matrixStackIn.scale(size[0], size[0], size[0]);
            RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
            model.model.render(entityIn, params.bodyYaw, params.bodyPitch, params.translation,
                             tickDelta, matrixStackIn, packedLightIn, RenderContext.WORLD);
        }

        matrixStackIn.popPose();
    }

    private void renderInInventory(T entityIn, MMDModelManager.Model model, float entityYaw,
                                    float tickDelta, PoseStack matrixStack, int packedLight, float[] size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        PoseStack modelViewStack = RenderSystem.getModelViewStack();

        int posX = (mc.screen.width - 176) / 2;
        int posY = (mc.screen.height - 166) / 2;
        modelViewStack.translate(posX + 51, posY + 60, 50.0);
        modelViewStack.pushPose();
        modelViewStack.scale(20.0f, 20.0f, -20.0f);
        modelViewStack.scale(size[1], size[1], size[1]);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        rotation.mul(new Quaternionf().rotateX(-entityIn.getXRot() * ((float) Math.PI / 180F)));
        rotation.mul(new Quaternionf().rotateY(-entityIn.getYRot() * ((float) Math.PI / 180F)));
        modelViewStack.mulPose(rotation);

        RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
        model.model.render(entityIn, entityYaw, 0.0f, new Vector3f(0.0f),
                          tickDelta, modelViewStack, packedLight, RenderContext.INVENTORY);
        modelViewStack.popPose();
    }

    private static float[] parseModelSize(MMDModelManager.Model model) {
        return ModelPropertyHelper.getModelSize(model.properties);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return PLACEHOLDER_TEXTURE;
    }
}
