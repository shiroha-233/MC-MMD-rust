package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Fabric 专属 Mixin accessor，暴露 KeyMapping 的当前绑定键
 * 解决 Fabric 运行时映射问题
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key mmd$getBoundKey();
}
