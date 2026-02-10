package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.renderer.core.FirstPersonManager;
import com.shiroha.mmdskin.renderer.core.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * LevelRenderer Mixin — 第一人称 MMD 模型渲染
 *
 * 在第一人称模式下，Minecraft 默认跳过渲染本地玩家实体。
 * 此 Mixin 通过在 renderLevel 方法内将 Camera.isDetached() 重定向为 true，
 * 使实体渲染循环不再跳过本地玩家，从而触发 PlayerRendererMixin 的 MMD 模型渲染。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Redirect(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z", ordinal = 0)
    )
    private boolean onCameraIsDetached(Camera camera) {
        if (FirstPersonManager.shouldRenderFirstPerson() && !IrisCompat.isRenderingShadows()) {
            return true;
        }
        return camera.isDetached();
    }
}
