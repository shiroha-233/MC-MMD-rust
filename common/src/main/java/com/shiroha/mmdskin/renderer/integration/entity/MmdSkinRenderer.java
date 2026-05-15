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
            ResourceLocation.fromNamespaceAndPath(MmdSkin.MOD_ID, "textures/entity/placeholder.png");

    protected final String modelName;

    private final RenderParams reusableParams = new RenderParams();
    private final Quaternionf reusableQuat = new Quaternionf();
    private final Vector3f reusableVec = new Vector3f();
    private final float[] reusableSize = new float[2];

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
        float[] size = parseModelSize(model, reusableSize);

        reusableParams.reset();
        EntityAnimationResolver.resolve(entityIn, model, entityYaw, tickDelta, reusableParams);

        matrixStackIn.pushPose();

        if (entityIn instanceof LivingEntity living && living.isBaby()) {
            matrixStackIn.scale(0.5f, 0.5f, 0.5f);
        }

        if (InventoryRenderHelper.isInventoryScreen()) {
            renderInInventory(entityIn, model, entityYaw, tickDelta, matrixStackIn, packedLightIn, size);
        } else {
            matrixStackIn.scale(size[0], size[0], size[0]);
            RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
            model.model.render(entityIn, reusableParams.bodyYaw, reusableParams.bodyPitch, reusableParams.translation,
                             tickDelta, matrixStackIn, packedLightIn, RenderContext.WORLD);
        }

        matrixStackIn.popPose();
    }

    private void renderInInventory(T entityIn, MMDModelManager.Model model, float entityYaw,
                                    float tickDelta, PoseStack matrixStack, int packedLight, float[] size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        matrixStack.pushPose();
        matrixStack.scale(20.0f, 20.0f, -20.0f);
        matrixStack.scale(size[1], size[1], size[1]);

        reusableQuat.identity()
                .rotateZ((float) Math.PI)
                .rotateX(-entityIn.getXRot() * ((float) Math.PI / 180F))
                .rotateY(-entityIn.getYRot() * ((float) Math.PI / 180F));
        matrixStack.mulPose(reusableQuat);

        RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
        reusableVec.set(0.0f);
        model.model.render(entityIn, entityYaw, 0.0f, reusableVec,
                          tickDelta, matrixStack, packedLight, RenderContext.INVENTORY);
        matrixStack.popPose();
    }

    private static float[] parseModelSize(MMDModelManager.Model model, float[] out) {
        float[] size = ModelPropertyHelper.getModelSize(model.properties);
        out[0] = size[0];
        out[1] = size[1];
        return out;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return PLACEHOLDER_TEXTURE;
    }
}
