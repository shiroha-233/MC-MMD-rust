package com.shiroha.mmdskin.mixin.fabric.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.maid.MaidMMDRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TouhouLittleMaid (Orihime) 女仆渲染兼容 Mixin
 *
 * 注入 LivingEntityRenderer.render() 的 HEAD，
 * 运行时通过类名检测女仆实体，若已绑定 MMD 模型则替换渲染。
 *
 * 与 Forge 版使用 RenderLivingEvent.Pre 的思路一致：
 * - 目标类为 vanilla 类，始终存在，无需条件加载
 * - 通过 isMaidModLoaded() 快速跳过（未安装女仆模组时零开销）
 * - 通过类名 contains 判断是否为女仆实体（无编译期依赖）
 *
 * 注意：EntityMaidRenderer.render() 在非 YSM/GeckoLib 路径下
 * 会调用 super.render()，最终进入 LivingEntityRenderer.render()，
 * 此时本 Mixin 生效，可取消原版 Bedrock 模型渲染。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class EntityMaidRendererMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mmdskin$onRenderMaid(LivingEntity entity, float entityYaw, float partialTicks,
                                       PoseStack poseStack, MultiBufferSource bufferIn,
                                       int packedLightIn, CallbackInfo ci) {

        if (!MaidCompatMixinPlugin.isMaidModLoaded()) {
            return;
        }

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
            entityYaw,
            partialTicks,
            poseStack,
            packedLightIn
        );

        poseStack.popPose();

        if (rendered) {

            ci.cancel();
        }
    }
}

