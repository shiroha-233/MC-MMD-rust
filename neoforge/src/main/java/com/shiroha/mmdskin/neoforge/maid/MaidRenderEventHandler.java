package com.shiroha.mmdskin.neoforge.maid;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 女仆渲染事件处理器
 * TODO_1.21.11: RenderLivingEvent 重构为三参数泛型 (T,S,M)，且不再暴露 LivingEntity / MultiBufferSource / packedLight；
 * 改用 RenderState + SubmitNodeCollector 后需重写 Maid 模型注入流程，暂时保留事件订阅占位以确保编译。
 */
@OnlyIn(Dist.CLIENT)
public class MaidRenderEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(MaidRenderEventHandler.class);
    private static boolean touhouLittleMaidLoaded = false;

    static {
        try {
            Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            touhouLittleMaidLoaded = true;
        } catch (ClassNotFoundException e) {
            touhouLittleMaidLoaded = false;
        }
    }

    /**
     * 在实体渲染前检查是否需要使用 MMD 模型渲染
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        // TODO_1.21.11: 渲染管线重写 — RenderLivingEvent 已 RenderState 化，无法直接获取 entity/MultiBufferSource/packedLight
        if (!touhouLittleMaidLoaded) {
            return;
        }
        // 原 MMD 模型注入逻辑暂时停用，待新管线适配完成后恢复
    }

    /**
     * 检查 TouhouLittleMaid 是否已加载
     */
    public static boolean isTouhouLittleMaidLoaded() {
        return touhouLittleMaidLoaded;
    }
}
