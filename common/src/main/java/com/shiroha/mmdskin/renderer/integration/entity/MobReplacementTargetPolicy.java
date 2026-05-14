/* 文件职责：定义可参与生物替换的实体目标过滤规则。 */
package com.shiroha.mmdskin.renderer.integration.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class MobReplacementTargetPolicy {
    private MobReplacementTargetPolicy() {
    }

    public static boolean isSupported(EntityType<?> entityType) {
        if (entityType == null || entityType == EntityType.PLAYER) {
            return false;
        }

        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return entityTypeId != null
                && "minecraft".equals(entityTypeId.getNamespace())
                && entityType.getCategory() != MobCategory.MISC;
    }
}
