package com.shiroha.mmdskin.mixin.forge.compat.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.compat.tacz.TaczFirstPersonFrameSnapshot;
import com.shiroha.mmdskin.compat.tacz.TaczFirstPersonPostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Pseudo
@Mixin(targets = "com.tacz.guns.client.renderer.item.GunItemRendererWrapper", remap = false)
public abstract class TaczGunItemRendererWrapperMixin {
    // 每次进入均替换线程局部帧，避免 ifPresent 早退或上一帧异常留下的矩阵跨帧复用。
    @Inject(method = "renderFirstPerson(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("HEAD"), remap = false)
    private void mmdskin$beginTaczFrame(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                        PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick,
                                        CallbackInfo ci) {
        TaczFirstPersonFrameSnapshot.beginFrame(player, stack, poseStack.last().pose(),
                Minecraft.getInstance().isSameThread());
        // 特殊方块渲染跳过本地玩家实体入口时，在枪械节点捕获前恢复 MMD 手臂所有权。
        TaczFirstPersonPostRenderer.ensureDeferredAtTaczHead(player, stack, partialTick, light);
    }

    // TaCZ 枪械和 buffer 全部完成后才消费锚点，避免在功能节点内重入完整 MMD renderer。
    @Inject(method = "renderFirstPerson(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("RETURN"), remap = false)
    private void mmdskin$finishTaczFrame(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                         PoseStack poseStack, MultiBufferSource buffers, int light, float partialTick,
                                         CallbackInfo ci) {
        TaczFirstPersonPostRenderer.finish(TaczFirstPersonFrameSnapshot
                .finishFrame(Minecraft.getInstance().isSameThread()).orElse(null));
    }
}
