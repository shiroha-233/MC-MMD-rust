package com.shiroha.mmdskin.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.function.Function;

public class KeyMappingUtil {
    private static Function<KeyMapping, InputConstants.Key> boundKeyGetter = (k) -> InputConstants.UNKNOWN;

    /**
     * 设置按键获取逻辑。由于 Fabric 和 Forge/NeoForge 获取 KeyMapping 绑定的物理按键方式不同，
     * 我们需要根据平台注入不同的逻辑。
     *
     * @param getter 获取 KeyMapping 对应 InputConstants.Key 的函数
     */
    public static void setBoundKeyGetter(Function<KeyMapping, InputConstants.Key> getter) {
        boundKeyGetter = getter;
    }

    /**
     * 获取 KeyMapping 绑定的物理按键。
     *
     * @param keyMapping KeyMapping 对象
     * @return 绑定的物理按键
     */
    public static InputConstants.Key getBoundKey(KeyMapping keyMapping) {
        if (keyMapping == null) {
            return InputConstants.UNKNOWN;
        }
        return boundKeyGetter.apply(keyMapping);
    }
}
