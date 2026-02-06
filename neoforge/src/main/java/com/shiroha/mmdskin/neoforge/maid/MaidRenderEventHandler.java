package com.shiroha.mmdskin.neoforge.maid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.maid.MaidMMDRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 女仆渲染事件处理器
 * 
 * 使用 NeoForge 事件系统替代 Mixin，实现对 TouhouLittleMaid 女仆的 MMD 模型渲染。
 * 这种方式不需要编译时依赖 TouhouLittleMaid 模组。
 */
@OnlyIn(Dist.CLIENT)
public class MaidRenderEventHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MaidRenderEventHandler.class);
    private static boolean touhouLittleMaidLoaded = false;
    
    static {
        try {
            Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            touhouLittleMaidLoaded = true;
            logger.info("检测到 TouhouLittleMaid 模组，启用女仆 MMD 渲染支持");
        } catch (ClassNotFoundException e) {
            touhouLittleMaidLoaded = false;
            logger.info("未检测到 TouhouLittleMaid 模组");
        }
    }
    
    /**
     * 在实体渲染前检查是否需要使用 MMD 模型渲染
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!touhouLittleMaidLoaded) {
            return;
        }
        
        LivingEntity entity = event.getEntity();
        String className = entity.getClass().getName();
        
        // 检查是否是女仆实体
        if (!className.contains("EntityMaid") && !className.contains("touhoulittlemaid")) {
            return;
        }
        
        // 检查是否有绑定的 MMD 模型
        if (!MaidMMDModelManager.hasMMDModel(entity.getUUID())) {
            return;
        }
        
        // 使用 MMD 模型渲染
        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        int packedLight = event.getPackedLight();
        
        poseStack.pushPose();
        poseStack.translate(0, 0.01, 0); // 轻微抬高避免 Z-fighting
        
        boolean rendered = MaidMMDRenderer.render(
            entity,
            entity.getUUID(),
            entity.getYRot(),
            partialTicks,
            poseStack,
            packedLight
        );
        
        poseStack.popPose();
        
        if (rendered) {
            // 成功渲染 MMD 模型，取消原版渲染
            event.setCanceled(true);
        }
    }
    
    /**
     * 检查 TouhouLittleMaid 是否已加载
     */
    public static boolean isTouhouLittleMaidLoaded() {
        return touhouLittleMaidLoaded;
    }
}
