package com.shiroha.mmdskin.forge.maid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.maid.MaidMMDRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Forge 女仆渲染事件处理器，用于接管 TouhouLittleMaid 女仆的 MMD 渲染。 */
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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!touhouLittleMaidLoaded) {
            return;
        }

        LivingEntity entity = event.getEntity();
        String className = entity.getClass().getName();

        if (!className.contains("EntityMaid") && !className.contains("touhoulittlemaid")) {
            return;
        }

        if (!MaidMMDModelManager.hasMMDModel(entity.getUUID())) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        float partialTicks = event.getPartialTick();
        int packedLight = event.getPackedLight();

        poseStack.pushPose();
        poseStack.translate(0, 0.01, 0);

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

            event.setCanceled(true);
        }
    }

    public static boolean isTouhouLittleMaidLoaded() {
        return touhouLittleMaidLoaded;
    }
}
