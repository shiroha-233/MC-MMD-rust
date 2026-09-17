// 负责在 26.2 画中画（PIP）实体渲染阶段开启/结束物品栏局部队列。
// PIP 的 EntityRenderDispatcher.submit 发生在 GuiEntityRenderer.renderToTexture 内，
// 此时 RenderSystem.outputColorTextureOverride 已指向 PIP 离屏纹理，
// 且 MmdPipState 已捕获正交投影 —— MMD 提交可正常入队并 flush 到离屏纹理。
package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.render.state.MmdPipState;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {
    @Inject(method = "renderToTexture", at = @At("HEAD"))
    private void mmdskin$pipRenderBegin(GuiEntityRenderState state, PoseStack poseStack,
                                        SubmitNodeCollector collector, CallbackInfo ci) {
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.inventory().begin());
    }

    @Inject(method = "renderToTexture", at = @At("TAIL"))
    private void mmdskin$pipRenderEnd(GuiEntityRenderState state, PoseStack poseStack,
                                      SubmitNodeCollector collector, CallbackInfo ci) {
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.inventory().finish());
    }
}
