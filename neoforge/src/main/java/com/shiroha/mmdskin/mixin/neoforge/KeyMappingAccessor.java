package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * NeoForge 专属 Mixin accessor，暴露 KeyMapping 的当前绑定键
 * 解决按键映射在 NeoForge 下的读取问题
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key mmd$getBoundKey();
}
