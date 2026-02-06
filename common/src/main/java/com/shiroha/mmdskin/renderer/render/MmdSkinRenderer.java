package com.shiroha.mmdskin.renderer.render;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.core.EntityAnimState;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class MmdSkinRenderer<T extends Entity> extends EntityRenderer<T> {
    protected String modelName;
    protected EntityRendererProvider.Context context;

    public MmdSkinRenderer(EntityRendererProvider.Context renderManager, String entityName) {
        super(renderManager);
        this.modelName = entityName.replace(':', '.');
        this.context = renderManager;
    }

    @Override
    public boolean shouldRender(T livingEntityIn, Frustum camera, double camX, double camY, double camZ) {
        return super.shouldRender(livingEntityIn, camera, camX, camY, camZ);
    }

    @Override
    public void render(T entityIn, float entityYaw, float tickDelta, PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn) {
        // 检查模型缓存清理
        MMDModelManager.tick();
        
        Minecraft MCinstance = Minecraft.getInstance();
        super.render(entityIn, entityYaw, tickDelta, matrixStackIn, bufferIn, packedLightIn);
        String animName = "";
        float bodyYaw = entityYaw;
        if(entityIn instanceof LivingEntity){
            bodyYaw = Mth.rotLerp(tickDelta, ((LivingEntity)entityIn).yBodyRotO, ((LivingEntity)entityIn).yBodyRot);
        }
        float bodyPitch = 0.0f;
        Vector3f entityTrans = new Vector3f(0.0f);
        MMDModelManager.Model model = MMDModelManager.GetModel(modelName, entityIn.getStringUUID());
        if(model == null){
            return;
        }
        MMDModelManager.ModelWithEntityData mwed = (MMDModelManager.ModelWithEntityData)model;
        model.loadModelProperties(false);
        float[] size = sizeOfModel(model);
        
        matrixStackIn.pushPose();
        if(entityIn instanceof LivingEntity){
            if(((LivingEntity) entityIn).getHealth() <= 0.0F){
                animName = "die";
                AnimStateChangeOnce(mwed, EntityAnimState.State.Die, 0);
            }else if(((LivingEntity) entityIn).isSleeping()){
                animName = "sleep";
                bodyYaw = ((LivingEntity) entityIn).getBedOrientation().toYRot() + 180.0f;
                bodyPitch = model.properties.getProperty("sleepingPitch") == null ? 0.0f : Float.valueOf(model.properties.getProperty("sleepingPitch"));
                entityTrans = model.properties.getProperty("sleepingTrans") == null ? new Vector3f(0.0f) : MmdSkinClient.str2Vec3f(model.properties.getProperty("sleepingTrans"));
                AnimStateChangeOnce(mwed, EntityAnimState.State.Sleep, 0);
            }
            if(((LivingEntity) entityIn).isBaby()){
                matrixStackIn.scale(0.5f, 0.5f, 0.5f);
            }
        }
        if(animName == ""){
            if (entityIn.isVehicle() && (entityIn.getX() - entityIn.xo != 0.0f || entityIn.getZ() - entityIn.zo != 0.0f)) {
                animName = "driven";
                AnimStateChangeOnce(mwed, EntityAnimState.State.Driven, 0);
            } else if (entityIn.isVehicle()) {
                animName = "ridden";
                AnimStateChangeOnce(mwed, EntityAnimState.State.Ridden, 0);
            } else if (entityIn.isSwimming()) {
                animName = "swim";
                AnimStateChangeOnce(mwed, EntityAnimState.State.Swim, 0);
            } else if ( (entityIn.getX() - entityIn.xo != 0.0f || entityIn.getZ() - entityIn.zo != 0.0f) && entityIn.getVehicle() == null) {
                animName = "walk";
                AnimStateChangeOnce(mwed, EntityAnimState.State.Walk, 0);
            } else {
                animName = "idle";
                AnimStateChangeOnce(mwed, EntityAnimState.State.Idle, 0);
            }
        }
        // MC 1.21.1: 使用传入的 PoseStack 进行渲染（移除 RenderSystem.getModelViewStack()）
        // 检测库存屏幕渲染
        boolean isInventory = MmdSkinClient.calledFrom(6).contains("Inventory") || MmdSkinClient.calledFrom(6).contains("class_490");
        if(isInventory) {
            matrixStackIn.pushPose();
            matrixStackIn.scale(20.0f, 20.0f, -20.0f);
            matrixStackIn.scale(size[1], size[1], size[1]);
            Quaternionf quaternionf = (new Quaternionf()).rotateZ((float)Math.PI);
            Quaternionf quaternionf1 = (new Quaternionf()).rotateX(-entityIn.getXRot() * ((float)Math.PI / 180F));
            Quaternionf quaternionf2 = (new Quaternionf()).rotateY(-entityIn.getYRot() * ((float)Math.PI / 180F));
            quaternionf.mul(quaternionf1);
            quaternionf.mul(quaternionf2);
            matrixStackIn.mulPose(quaternionf);
            RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
            model.model.render(entityIn, entityYaw, 0.0f, new Vector3f(0.0f), tickDelta, matrixStackIn, packedLightIn, RenderContext.INVENTORY);
            matrixStackIn.popPose();
        } else {
            matrixStackIn.scale(size[0], size[0], size[0]);
            RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
            model.model.render(entityIn, bodyYaw, bodyPitch, entityTrans, tickDelta, matrixStackIn, packedLightIn, RenderContext.WORLD);
        }
        matrixStackIn.popPose();
    }

    float[] sizeOfModel(MMDModelManager.Model model){
        float[] size = new float[2];
        size[0] = (model.properties.getProperty("size") == null) ? 1.0f : Float.valueOf(model.properties.getProperty("size"));
        size[1] = (model.properties.getProperty("size_in_inventory") == null) ? 1.0f : Float.valueOf(model.properties.getProperty("size_in_inventory"));
        return size;
    }

    void AnimStateChangeOnce(MMDModelManager.ModelWithEntityData model, EntityAnimState.State targetState, Integer layer) {
        String property = EntityAnimState.getPropertyName(targetState);
        if (model.entityData.stateLayers[layer] != targetState) {
            model.entityData.stateLayers[layer] = targetState;
            model.model.ChangeAnim(MMDAnimManager.GetAnimModel(model.model, property), layer);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return null;
    }
}
