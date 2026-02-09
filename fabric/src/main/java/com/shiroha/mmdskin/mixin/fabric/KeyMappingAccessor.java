package com.shiroha.mmdskin.mixin.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor，暴露 KeyMapping 的当前绑定键
 * Forge/NeoForge 通过扩展方法 getKey() 提供，Fabric 需要通过 Mixin 访问
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key mmd$getBoundKey();
}
