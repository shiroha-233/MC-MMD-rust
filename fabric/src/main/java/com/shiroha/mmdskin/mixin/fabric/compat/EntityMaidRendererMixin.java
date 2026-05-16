/* 文件职责：TouhouLittleMaid 女仆渲染兼容 Mixin（适配 1.21.11 LivingEntityRenderer.submit API）。 */
package com.shiroha.mmdskin.mixin.fabric.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.maid.MaidMMDRenderer;
import com.shiroha.mmdskin.renderer.runtime.state.LivingEntityRenderStateBinder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class EntityMaidRendererMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mmdskin$onRenderMaid(LivingEntityRenderState state, PoseStack poseStack,
                                       SubmitNodeCollector collector, CameraRenderState cameraState,
                                       CallbackInfo ci) {

        if (!MaidCompatMixinPlugin.isMaidModLoaded()) {
            return;
        }

        LivingEntityRenderStateBinder.Binding binding = LivingEntityRenderStateBinder.get(state);
        if (binding == null) {
            return;
        }
        LivingEntity entity = binding.entity();

        String className = entity.getClass().getName();
        if (!className.contains("EntityMaid") && !className.contains("touhoulittlemaid")) {
            return;
        }

        if (!MaidMMDModelManager.hasMMDModel(entity.getUUID())) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0, 0.01, 0);

        boolean rendered = MaidMMDRenderer.render(
            entity,
            entity.getUUID(),
            state.bodyRot,
            binding.partialTick(),
            poseStack,
            state.lightCoords
        );

        poseStack.popPose();

        if (rendered) {
            ci.cancel();
        }
    }
}
