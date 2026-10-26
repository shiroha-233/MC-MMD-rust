// 负责在 Fabric Living renderer 入口路由 MMD 主体并保留基类绘制。
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.draw.EntityBaseRenderBridge;
import com.shiroha.mmdskin.client.draw.MmdRenderRouter;
import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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

    // 26.3 起 LivingEntityRenderer 的入口从 render(...) 换成 submit(...)，
    // 并且不再传 packedLight（光照等级在 EntityRenderState.lightCoords 里）。
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void mmdskin$routeBody(LivingEntityRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector collector, CameraRenderState camera,
                                   CallbackInfo callback) {
        if (!(state instanceof MmdRenderStateExtension extension)) {
            return;
        }
        int packedLight = state.lightCoords;
        MmdRenderRouter.Result result = state instanceof AvatarRenderState playerState
                ? MmdRenderRouter.routePlayer(extension.mmdskin$snapshot(), playerState,
                        poseStack, collector, packedLight, false)
                : MmdRenderRouter.route(extension.mmdskin$snapshot(), poseStack, packedLight, false);
        if (result == MmdRenderRouter.Result.FALLTHROUGH) {
            return;
        }
        if (result == MmdRenderRouter.Result.SUPPRESSED) {
            // 尸体已接管：本体整体不画（原版主体也不画），避免死亡瞬间出现两重身影。
            callback.cancel();
            return;
        }
        mmdskin$renderEntityBase(state, poseStack, collector, camera);
        callback.cancel();
    }

    @Override
    public void mmdskin$renderEntityBase(EntityRenderState state, PoseStack poseStack,
                                         SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit((LivingEntityRenderState) state, poseStack, collector, camera);
    }
}
