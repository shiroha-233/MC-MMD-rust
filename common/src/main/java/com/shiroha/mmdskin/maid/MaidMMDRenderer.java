package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.core.EntityAnimState;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 女仆 MMD 模型渲染器
 * 
 * 负责渲染女仆的 MMD 模型，处理动画状态转换。
 */
public class MaidMMDRenderer {
    private static final Logger logger = LogManager.getLogger();
    
    /**
     * 渲染女仆的 MMD 模型
     * 
     * @param entity 女仆实体
     * @param maidUUID 女仆 UUID
     * @param entityYaw 实体偏航角
     * @param partialTicks 插值时间
     * @param poseStack 变换矩阵栈
     * @param packedLight 光照值
     * @return 是否成功渲染（如果返回 false，应使用原版渲染）
     */
    public static boolean render(LivingEntity entity, UUID maidUUID, float entityYaw, 
                                  float partialTicks, PoseStack poseStack, int packedLight) {
        if (!MaidMMDModelManager.hasMMDModel(maidUUID)) {
            return false;
        }
        
        MMDModelManager.Model modelData = MaidMMDModelManager.getModel(maidUUID);
        if (modelData == null || modelData.model == null) {
            return false;
        }
        
        try {
            modelData.loadModelProperties(false);
            updateAnimationState(entity, modelData);
            
            Vector3f entityTrans = getEntityTranslation(modelData);
            float entityPitch = 0.0f;
            
            float modelSize = getModelSize(modelData);
            poseStack.scale(modelSize, modelSize, modelSize);
            
            // 按模型缩放比例修正头部 pitch，防止小模型因 eyeHeight 不匹配而翻白眼
            float originalXRot = entity.getXRot();
            float originalXRotO = entity.xRotO;
            if (modelSize != 1.0f && modelSize > 0.0f) {
                entity.setXRot(originalXRot * modelSize);
                entity.xRotO = originalXRotO * modelSize;
            }
            
            RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
            modelData.model.render(entity, entityYaw, entityPitch, entityTrans, partialTicks, poseStack, packedLight, RenderContext.WORLD);
            
            // 恢复 xRot
            if (modelSize != 1.0f && modelSize > 0.0f) {
                entity.setXRot(originalXRot);
                entity.xRotO = originalXRotO;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("渲染女仆 MMD 模型失败: {}", maidUUID, e);
            return false;
        }
    }
    
    /**
     * 更新动画状态（针对女仆实体的简化版本）
     */
    private static void updateAnimationState(LivingEntity entity, MMDModelManager.Model modelData) {
        if (modelData.entityData == null) {
            return;
        }
        
        EntityAnimState entityData = modelData.entityData;
        EntityAnimState.State targetState;
        
        if (entity.getHealth() <= 0) {
            targetState = EntityAnimState.State.Die;
        } else if (entity.isSleeping()) {
            targetState = EntityAnimState.State.Sleep;
        } else if (entity.isPassenger()) {
            targetState = EntityAnimState.State.Ride;
        } else if (entity.isSwimming()) {
            targetState = EntityAnimState.State.Swim;
        } else if (entity.onClimbable()) {
            targetState = EntityAnimState.State.OnClimbable;
        } else if (entity.isSprinting()) {
            targetState = EntityAnimState.State.Sprint;
        } else if (hasMovement(entity)) {
            targetState = EntityAnimState.State.Walk;
        } else {
            targetState = EntityAnimState.State.Idle;
        }
        
        if (entityData.stateLayers[0] != targetState) {
            entityData.stateLayers[0] = targetState;
            String animName = EntityAnimState.getPropertyName(targetState);
            modelData.model.changeAnim(MMDAnimManager.GetAnimModel(modelData.model, animName), 0);
        }
    }
    
    private static boolean hasMovement(LivingEntity entity) {
        return entity.getX() - entity.xo != 0.0 || entity.getZ() - entity.zo != 0.0;
    }
    
    /**
     * 获取实体渲染偏移
     */
    private static Vector3f getEntityTranslation(MMDModelManager.Model modelData) {
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
        if (modelData.properties != null) {
            String transStr = modelData.properties.getProperty("entityTrans");
            if (transStr != null) {
                String[] parts = transStr.split(",");
                if (parts.length == 3) {
                    try {
                        x = Float.parseFloat(parts[0].trim());
                        y = Float.parseFloat(parts[1].trim());
                        z = Float.parseFloat(parts[2].trim());
                    } catch (NumberFormatException e) {
                        // 使用默认值
                    }
                }
            }
        }
        
        return new Vector3f(x, y, z);
    }
    
    /**
     * 获取模型缩放大小
     */
    private static float getModelSize(MMDModelManager.Model modelData) {
        if (modelData.properties != null) {
            String value = modelData.properties.getProperty("size");
            if (value != null) {
                try {
                    return Float.parseFloat(value);
                } catch (NumberFormatException e) {
                    // 使用默认值
                }
            }
        }
        return 1.0f;
    }
    
    /**
     * 设置实体速度（用于惯性效果）
     */
    public static void setEntityVelocity(UUID maidUUID, float vx, float vy, float vz) {
        MMDModelManager.Model modelData = MaidMMDModelManager.getModel(maidUUID);
        if (modelData != null && modelData.model != null) {
            // 调用物理系统设置速度
            // 这需要通过 JNI 调用，暂时留空
        }
    }
}
