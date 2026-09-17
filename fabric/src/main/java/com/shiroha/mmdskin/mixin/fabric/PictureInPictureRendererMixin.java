// 负责在 26.2 画中画（PIP）渲染期间捕获离屏投影，供 MMD 绘制使用。
// PIP（物品栏纸娃娃/GUI 实体预览）在 prepare 中设置正交投影并绑定离屏纹理，
// prepare 结束后恢复世界投影；我们在 prepareTexturesAndProjection 之后捕获正交投影，
// 在 prepare 结束（renderToTexture 已执行完、flush 已完成）时结束 PIP 状态。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.render.state.MmdPipState;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
    @Shadow
    private Projection projection;

    // prepareTexturesAndProjection 在 renderToTexture 之前设置好正交投影；
    // 此时标记 PIP 状态开始，renderToTexture 内的 MMD flush 会使用正交投影 + 离屏纹理。
    @Inject(method = "prepareTexturesAndProjection", at = @At("TAIL"))
    private void mmdskin$captureProjection(boolean needsResize, int width, int height, CallbackInfo ci) {
        Matrix4f matrix = new Matrix4f();
        this.projection.getMatrix(matrix);
        MmdPipState.begin(matrix);
    }

    // prepare 结束（renderToTexture / renderAllFeatures 已执行完）时结束 PIP 状态
    @Inject(method = "prepare", at = @At("TAIL"))
    private void mmdskin$pipPrepareEnd(CallbackInfo ci) {
        MmdPipState.end();
    }
}
