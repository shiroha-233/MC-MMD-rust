package com.shiroha.mmdskin.mixin.fabric;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Fabric 玩家渲染入口，1.21.11 渲染管线重写后暂为空壳。 */
// TODO_1.21.11: Mixin 目标已变 - PlayerRenderer 重命名为 AvatarRenderer，render 方法被替换为 extractRenderState + submit 流程，原玩家委托链需重写为 RenderState 驱动
@Mixin(AvatarRenderer.class)
public abstract class FabricPlayerRendererMixin {
    // 原 onRender(@Inject HEAD render(...)) 方法体在 1.21.11 中已无对应签名，整体注入待重写。
}
