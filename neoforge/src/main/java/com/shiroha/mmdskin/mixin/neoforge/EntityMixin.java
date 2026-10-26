// 负责让 NeoForge 实体眼位与视线查询读取实例化第一人称相机模块。
//
// 26.3 适配要点：Minecraft.gameRenderer 是 public final 字段，Camera 的取法从
// 1.21.5 的 gameRenderer.getMainCamera() 改成 gameRenderer.mainCamera()。
package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void onGetEyePosition(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        var runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime == null) {
            return;
        }
        runtime.firstPerson().camera()
                .resolveInteractionEyePosition(entity, partialTick, minecraft.gameRenderer.mainCamera())
                .ifPresent(callback::setReturnValue);
    }

    @Inject(method = "getViewVector(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void onGetViewVector(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        var runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
        if (runtime == null) {
            return;
        }
        runtime.firstPerson().camera()
                .resolveViewVector(entity, minecraft.gameRenderer.mainCamera())
                .ifPresent(callback::setReturnValue);
    }
}
