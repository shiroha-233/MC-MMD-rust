/**
 * 文件职责：在 NeoForge 的世界渲染入口开启 MMD 帧队列。
 *
 * <p>26.3 适配要点：
 * <ul>
 *   <li>1.21.5 的 {@code GameRenderer.getFov} / {@code GameRenderer.pick} 都已不存在：
 *       FOV 改由 {@code Camera.calculateFov} 接管（见 {@code CameraMixin}），
 *       准星拾取改由 {@code Minecraft.pick} 接管（见 {@code MinecraftMixin}）。
 *       所以这个 mixin 只剩下"开启帧队列"这一件事。</li>
 *   <li>{@code beginFrame} 会清空帧队列，必须落在实体提交之前：
 *       26.3 的实体提交发生在 {@code LevelRenderer.submitFeatures}（世界渲染阶段），
 *       因此注入点选在 {@code GameRenderer.renderLevel} 的 HEAD。</li>
 * </ul>
 */
package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void mmdskin$beginFrame(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        float deltaTicks = minecraft.getDeltaTracker().getGameTimeDeltaTicks();
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.beginFrame(deltaTicks));
    }
}
