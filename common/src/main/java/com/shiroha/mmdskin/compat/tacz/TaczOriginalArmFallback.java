package com.shiroha.mmdskin.compat.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;

import java.lang.reflect.Method;

/** 文件职责：在不建立 TaCZ 编译期硬依赖时安全回调其原版第一人称手臂。 */
public final class TaczOriginalArmFallback {
    private static volatile Method renderMethod;

    private TaczOriginalArmFallback() {
    }

    public static void render(LocalPlayer player, HumanoidArm hand, PoseStack poseStack, int light) {
        try {
            resolveMethod().invoke(null, player, hand, poseStack, light);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法回退到 TaCZ 原版第一人称手臂渲染", exception);
        }
    }

    private static Method resolveMethod() throws ReflectiveOperationException {
        Method cached = renderMethod;
        if (cached != null) {
            return cached;
        }
        Class<?> helper = Class.forName("com.tacz.guns.util.RenderHelper");
        cached = helper.getMethod("renderFirstPersonArm",
                LocalPlayer.class, HumanoidArm.class, PoseStack.class, int.class);
        renderMethod = cached;
        return cached;
    }
}
