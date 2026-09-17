// 负责在 Fabric Living renderer 入口路由 MMD 主体并保留基类绘制。
// 26.2 适配：render -> submit（SubmitNodeCollector 提交体系）。
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.draw.EntityBaseRenderBridge;
import com.shiroha.mmdskin.client.draw.MmdRenderRouter;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class FabricPlayerRendererMixin
        extends EntityRenderer<LivingEntity, LivingEntityRenderState>
        implements EntityBaseRenderBridge {

    protected FabricPlayerRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void mmdskin$routeBody(LivingEntityRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector collector, CameraRenderState camera,
                                   CallbackInfo callback) {
        if (!(state instanceof MmdRenderStateExtension extension)) {
            return;
        }
        MmdRenderRouter.Result result;
        MmdRenderSnapshot snapshot = extension.mmdskin$snapshot();
        if (snapshot != null) {
            LivingEntity entity = resolveEntity(snapshot);
            result = MmdRenderRouter.routePlayer(snapshot, state, entity,
                    poseStack, collector, state.lightCoords, false);
        } else {
            result = MmdRenderRouter.route(extension.mmdskin$snapshot(), poseStack,
                    state.lightCoords, false);
        }
        if (result == MmdRenderRouter.Result.FALLTHROUGH) {
            return;
        }
        mmdskin$renderEntityBase(state, poseStack, collector, camera);
        callback.cancel();
    }

    private static LivingEntity resolveEntity(MmdRenderSnapshot snapshot) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        var entity = Minecraft.getInstance().level.getEntity(snapshot.entityId());
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    public void mmdskin$renderEntityBase(EntityRenderState state, PoseStack poseStack,
                                         SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit((LivingEntityRenderState) state, poseStack, collector, camera);
    }
}
