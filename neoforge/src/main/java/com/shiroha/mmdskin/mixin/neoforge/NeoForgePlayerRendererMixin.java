package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.animation.AnimationStateManager;
import com.shiroha.mmdskin.renderer.core.FirstPersonManager;
import com.shiroha.mmdskin.renderer.render.ItemRenderHelper;
import com.shiroha.mmdskin.renderer.render.InventoryRenderHelper;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.core.RenderParams;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.renderer.model.MMDModelManager.ModelWithEntityData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家渲染器 Mixin
 * 拦截玩家渲染过程，替换为 3D MMD 模型
 * 
 * 重构说明：
 * - 拆分动画管理到 AnimationStateManager
 * - 拆分物品渲染到 ItemRenderHelper
 * - 拆分库存渲染到 InventoryRenderHelper
 * - 减少单个文件的复杂度，提高可维护性
 * 
 * 设计原则：
 * - 单一职责：只负责渲染流程的协调
 * - 依赖倒置：依赖辅助类而非直接实现
 */
@Mixin(PlayerRenderer.class)
public abstract class NeoForgePlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public NeoForgePlayerRendererMixin(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    public void onRender(AbstractClientPlayer player, float entityYaw, float tickDelta, PoseStack matrixStack, 
                      MultiBufferSource vertexConsumers, int packedLight, CallbackInfo ci) {
        // 获取玩家选择的模型（使用同步管理器，支持联机）
        String playerName = player.getName().getString();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        
        // 如果选择了默认渲染或未选择模型，使用原版渲染
        if (selectedModel == null || selectedModel.isEmpty() || selectedModel.equals("默认 (原版渲染)")) {
            super.render(player, entityYaw, tickDelta, matrixStack, vertexConsumers, packedLight);
            return;
        }
        
        // 加载模型（使用玩家名作为缓存键）
        MMDModelManager.Model modelData = MMDModelManager.GetModel(selectedModel, playerName);
        
        // 如果模型加载失败，使用原版渲染
        if (modelData == null) {
            super.render(player, entityYaw, tickDelta, matrixStack, vertexConsumers, packedLight);
            return;
        }
        
        ModelWithEntityData modelWithData = (ModelWithEntityData) modelData;
        IMMDModel model = modelWithData.model;
        
        // 加载模型属性
        modelWithData.loadModelProperties(MmdSkinClient.reloadProperties);
        
        // 获取模型尺寸
        float[] size = getModelSize(modelWithData);

        // 第一人称模式管理（阶段一：管理头部隐藏状态，在 render 之前）
        float combinedScale = size[0] * ModelConfigManager.getConfig(selectedModel).modelScale;
        FirstPersonManager.preRender(NativeFunc.GetInst(), model.GetModelLong(), combinedScale, isLocalPlayer);
        boolean isFirstPerson = isLocalPlayer && FirstPersonManager.isActive();

        // 更新动画状态（委托给 AnimationStateManager）
        AnimationStateManager.updateAnimationState(player, modelWithData);
        
        // 计算渲染参数
        RenderParams params = calculateRenderParams(player, modelWithData, tickDelta);
        
        // 渲染模型
        if (InventoryRenderHelper.isInventoryScreen()) {
            // 库存屏幕渲染
            InventoryRenderHelper.renderInInventory(player, model, entityYaw, tickDelta, matrixStack, packedLight, size);
        } else {
            // 正常世界渲染
            matrixStack.scale(size[0], size[0], size[0]);
            RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
            RenderContext ctx = isFirstPerson ? RenderContext.FIRST_PERSON : RenderContext.WORLD;
            model.render(player, params.bodyYaw, params.bodyPitch, params.translation, tickDelta, matrixStack, packedLight, ctx);
        }

        // 第一人称模式（阶段二：render 之后获取当前帧的眼睛骨骼位置）
        if (isFirstPerson) {
            FirstPersonManager.postRender(NativeFunc.GetInst(), model.GetModelLong());
        }

        // 渲染手持物品（委托给 ItemRenderHelper）
        ItemRenderHelper.renderItems(player, modelWithData, matrixStack, vertexConsumers, packedLight);
        
        // 取消原版渲染
        ci.cancel();
    }
    
    /**
     * 计算渲染参数
     */
    private RenderParams calculateRenderParams(AbstractClientPlayer player, ModelWithEntityData modelData, float tickDelta) {
        RenderParams params = new RenderParams();
        params.bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        params.bodyPitch = 0.0f;
        params.translation = new Vector3f(0.0f);
        
        // 根据状态调整参数
        if (player.isFallFlying()) {
            params.bodyPitch = player.getXRot() + getPropertyFloat(modelData, "flyingPitch", 0.0f);
            params.translation = getPropertyVector(modelData, "flyingTrans");
        } else if (player.isSleeping()) {
            params.bodyYaw = player.getBedOrientation().toYRot() + 180.0f;
            params.bodyPitch = getPropertyFloat(modelData, "sleepingPitch", 0.0f);
            params.translation = getPropertyVector(modelData, "sleepingTrans");
        } else if (player.isSwimming()) {
            params.bodyPitch = player.getXRot() + getPropertyFloat(modelData, "swimmingPitch", 0.0f);
            params.translation = getPropertyVector(modelData, "swimmingTrans");
        } else if (player.isVisuallyCrawling()) {
            params.bodyPitch = getPropertyFloat(modelData, "crawlingPitch", 0.0f);
            params.translation = getPropertyVector(modelData, "crawlingTrans");
        }
        
        return params;
    }
    
    /**
     * 获取模型尺寸
     */
    private float[] getModelSize(ModelWithEntityData modelData) {
        float[] size = new float[2];
        size[0] = getPropertyFloat(modelData, "size", 1.0f);
        size[1] = getPropertyFloat(modelData, "size_in_inventory", 1.0f);
        return size;
    }
    
    /**
     * 获取属性浮点值
     */
    private float getPropertyFloat(ModelWithEntityData modelData, String key, float defaultValue) {
        String value = modelData.properties.getProperty(key);
        return value == null ? defaultValue : Float.parseFloat(value);
    }
    
    /**
     * 获取属性向量值
     */
    private Vector3f getPropertyVector(ModelWithEntityData modelData, String key) {
        String value = modelData.properties.getProperty(key);
        return value == null ? new Vector3f(0.0f) : MmdSkinClient.str2Vec3f(value);
    }
}
