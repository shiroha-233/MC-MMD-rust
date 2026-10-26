package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.player.ragdoll.RagdollManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 有 MMD 尸体时取消原版死亡白烟。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathParticleMixin {

    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void mmd$onDeathPoof(CallbackInfo ci) {
        if (RagdollManager.getInstance().shouldHideDeathPoof((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
