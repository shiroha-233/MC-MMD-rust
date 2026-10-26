// 文件职责：在 GUI 实体预览（背包里的玩家模型）的**实际提交点**开启并收尾 MMD 背包队列。
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x 的背包预览走的是 Picture-in-Picture：实体渲染状态在
 * {@code InventoryScreen.extractEntityInInventoryFollowsMouse} 里提取，
 * 但**真正的绘制**发生在 GUI 阶段的 {@code GuiEntityRenderer.renderToTexture}。
 *
 * <p>两段不在同一个方法里，所以背包渲染会话（{@code InventoryRenderSession}）
 * 必须在提交点再开一次：否则提交时队列已经收尾，MMD 绘制请求进不去，
 * 直接抛 "inventory render session is not active" 把游戏带崩。
 */
@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {

    @Inject(
            method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("HEAD"))
    private void mmdskin$beginGuiEntityRender(GuiEntityRenderState state, PoseStack poseStack,
                                             SubmitNodeCollector collector, CallbackInfo callback) {
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.inventory().begin());
    }

    @Inject(
            method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("RETURN"))
    private void mmdskin$flushGuiEntityRender(GuiEntityRenderState state, PoseStack poseStack,
                                             SubmitNodeCollector collector, CallbackInfo callback) {
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.inventory().finish());
    }
}
