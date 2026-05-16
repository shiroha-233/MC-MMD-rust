/* 文件职责：MMD 自定义实体渲染器，按 1.21.11 RenderState 流水线驱动 MMD 模型渲染。 */
package com.shiroha.mmdskin.renderer.integration.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.renderer.api.RenderContext;
import com.shiroha.mmdskin.renderer.integration.ModelPropertyHelper;
import com.shiroha.mmdskin.renderer.integration.player.InventoryRenderHelper;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class MmdSkinRenderer<T extends Entity> extends EntityRenderer<T, MmdSkinRenderState> {

    private static final Identifier PLACEHOLDER_TEXTURE =
            Identifier.fromNamespaceAndPath(MmdSkin.MOD_ID, "textures/entity/placeholder.png");

    protected final String modelName;

    public MmdSkinRenderer(EntityRendererProvider.Context renderManager, String entityName) {
        super(renderManager);
        this.modelName = entityName.replace(':', '.');
    }

    @Override
    public MmdSkinRenderState createRenderState() {
        return new MmdSkinRenderState();
    }

    @Override
    public void extractRenderState(T entity, MmdSkinRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.partialTick = partialTick;
        state.entityYaw = entity.getYRot(partialTick);
        state.entityXRot = entity.getXRot(partialTick);
        state.isBaby = entity instanceof LivingEntity living && living.isBaby();
        state.isInInventoryScreen = InventoryRenderHelper.isInventoryScreen();

        MMDModelManager.Model model = MMDModelManager.GetModel(modelName, entity.getStringUUID());
        state.model = model;
        if (model == null) {
            return;
        }
        model.loadModelProperties(false);
        float[] size = ModelPropertyHelper.getModelSize(model.properties);
        state.size[0] = size[0];
        state.size[1] = size[1];

        state.params.reset();
        EntityAnimationResolver.resolve(entity, model, state.entityYaw, partialTick, state.params);
    }

    @Override
    public void submit(MmdSkinRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        super.submit(state, poseStack, collector, cameraState);

        MMDModelManager.Model model = state.model;
        Entity entity = state.entity;
        if (model == null || model.model == null || entity == null) {
            return;
        }

        poseStack.pushPose();
        try {
            if (state.isBaby) {
                poseStack.scale(0.5f, 0.5f, 0.5f);
            }

            if (state.isInInventoryScreen) {
                renderInInventory(entity, model, state, poseStack);
            } else {
                poseStack.scale(state.size[0], state.size[0], state.size[0]);
                model.model.render(entity, state.params.bodyYaw, state.params.bodyPitch,
                        state.params.translation, state.partialTick, poseStack,
                        state.lightCoords, RenderContext.WORLD);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderInInventory(Entity entity, MMDModelManager.Model model,
                                   MmdSkinRenderState state, PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        poseStack.pushPose();
        try {
            poseStack.scale(20.0f, 20.0f, -20.0f);
            poseStack.scale(state.size[1], state.size[1], state.size[1]);

            Quaternionf rotation = new Quaternionf()
                    .rotateZ((float) Math.PI)
                    .rotateX(-state.entityXRot * ((float) Math.PI / 180F))
                    .rotateY(-state.entityYaw * ((float) Math.PI / 180F));
            poseStack.mulPose(rotation);

            model.model.render(entity, state.entityYaw, 0.0f, new Vector3f(0.0f),
                    state.partialTick, poseStack, state.lightCoords, RenderContext.INVENTORY);
        } finally {
            poseStack.popPose();
        }
    }
}
