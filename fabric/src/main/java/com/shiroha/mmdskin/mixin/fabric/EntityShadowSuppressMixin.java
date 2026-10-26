// 负责在 MMD 尸体接管实体后抑制该实体的原版影子。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 尸体接管后去掉原版影子。
 *
 * <p>1.20.1 是在 {@code EntityRenderDispatcher.renderShadow} 里判定的（那里拿得到实体）。
 * 26.3 把影子提取换成了 {@code EntityRenderer.extractShadow(state, minecraft, level)}，
 * 只拿得到 render state，所以判定结果由 {@code LivingEntityRendererStateCaptureMixin}
 * 提前记到 state 上。
 *
 * <p>不抑制的话，尸体接管后地上会留一个"没有身体的原版影子"——很容易被误读成渲染异常。
 */
@Mixin(EntityRenderer.class)
public abstract class EntityShadowSuppressMixin {

    @Inject(method = "extractShadow", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmd$suppressShadow(EntityRenderState state, Minecraft minecraft, Level level,
                                    CallbackInfo ci) {
        if (state instanceof MmdRenderStateExtension extension && extension.mmdskin$suppressShadow()) {
            ci.cancel();
        }
    }
}
