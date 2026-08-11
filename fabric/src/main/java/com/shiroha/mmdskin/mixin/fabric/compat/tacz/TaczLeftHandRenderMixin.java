package com.shiroha.mmdskin.mixin.fabric.compat.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shiroha.mmdskin.compat.tacz.TaczFirstPersonFrameSnapshot;
import com.shiroha.mmdskin.compat.tacz.TaczOriginalArmFallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.model.functional.LeftHandRender", remap = false)
public abstract class TaczLeftHandRenderMixin {
    // 注入点紧跟 TaCZ 的 Z+ 180 度翻转，采样值正是原版手臂委托将使用的最终矩阵。
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;II)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", shift = At.Shift.AFTER), remap = false)
    private void mmdskin$captureFinalLeftMatrix(PoseStack poseStack, VertexConsumer vertexBuffer,
                                                   ItemDisplayContext transformType, int light, int overlay,
                                                   CallbackInfo ci) {
        if (transformType.firstPerson()) {
            TaczFirstPersonFrameSnapshot.captureHand(Minecraft.getInstance().player,
                    TaczFirstPersonFrameSnapshot.Hand.LEFT, poseStack.last().pose(), poseStack.last().normal(),
                    Minecraft.getInstance().isSameThread());
        }
    }

    // 只取消 TaCZ 已入队委托中的方块手臂调用；不改写或取消 gunModel 的 delegate 队列。
    @Redirect(method = "lambda$render$0(Lorg/joml/Matrix3f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;II)V", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/RenderHelper;renderFirstPersonArm(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;I)V"), remap = false, require = 1)
    private static void mmdskin$cancelOriginalLeftArm(LocalPlayer player, HumanoidArm hand, PoseStack poseStack, int light) {
        if (!TaczFirstPersonFrameSnapshot.shouldSuppressOriginalArm(player,
                TaczFirstPersonFrameSnapshot.Hand.LEFT, Minecraft.getInstance().isSameThread())) {
            TaczOriginalArmFallback.render(player, hand, poseStack, light);
        }
    }
}
