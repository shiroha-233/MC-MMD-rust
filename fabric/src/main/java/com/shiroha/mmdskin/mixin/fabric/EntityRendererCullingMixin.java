// 负责在 Fabric 实体剔除入口合并已加载 MMD 模型的 bounds。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.entity.MmdCullingBounds;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererCullingMixin {
    // 26.3 起 getBoundingBoxForCulling 多了一个 partialTick 参数
    // （1.21.5 是 getBoundingBoxForCulling(T)）。
    @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
    private void mmdskin$expandCullingBounds(Entity entity, float partialTick,
                                             CallbackInfoReturnable<AABB> callback) {
        if (entity instanceof LivingEntity living) {
            callback.setReturnValue(MmdCullingBounds.expand(living, callback.getReturnValue()));
        }
    }
}
