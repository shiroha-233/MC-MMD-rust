package com.shiroha.mmdskin.render.entity;

import com.shiroha.mmdskin.player.render.InventoryRenderHelper;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
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

        ManagedModel model = ClientRenderRuntime.get().modelRepository()
                .acquire(ModelRequestKey.mob(entityIn, modelName));
        if (model == null) return;

        float[] size = parseModelSize(model);

        MutableRenderPose params = new MutableRenderPose();
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
            model.modelInstance().render(entityIn, params.bodyYaw, params.bodyPitch, params.translation,
                             tickDelta, matrixStackIn, packedLightIn, RenderScene.WORLD);
        }

        matrixStackIn.popPose();
    }

    private void renderInInventory(T entityIn, ManagedModel model, float entityYaw,
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
        model.modelInstance().render(entityIn, entityYaw, 0.0f, new Vector3f(0.0f),
                          tickDelta, modelViewStack, packedLight, RenderScene.INVENTORY);
        modelViewStack.popPose();
    }

    private static float[] parseModelSize(ManagedModel model) {
        return new float[] {
                model.renderProperties().modelScale(),
                model.renderProperties().inventoryScale()
        };
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return PLACEHOLDER_TEXTURE;
    }
}
