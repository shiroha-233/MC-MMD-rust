package com.shiroha.mmdskin.forge.entity;

import com.shiroha.mmdskin.renderer.integration.entity.MobReplacementRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 原版生物的 MMD 替换渲染事件处理器。
 */
@OnlyIn(Dist.CLIENT)
public class MobReplacementRenderEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof AbstractClientPlayer) {
            return;
        }

        if (MobReplacementRenderer.render(entity, entity.getYRot(), event.getPartialTick(), event.getPoseStack(), event.getPackedLight())) {
            event.setCanceled(true);
        }
    }
}
