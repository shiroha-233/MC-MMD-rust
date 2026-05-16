/* 文件职责：维护 Fabric 侧可配置生物替换的目标列表。 */
package com.shiroha.mmdskin.fabric.render;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class MobReplacementTargets {
    private static final List<Target> TARGETS = BuiltInRegistries.ENTITY_TYPE.stream()
        .filter(MobReplacementTargets::isSupported)
        .map(entityType -> new Target(
            BuiltInRegistries.ENTITY_TYPE.getKey(entityType),
            entityType,
            entityType.getDescription()))
        .sorted(Comparator.comparing(target -> target.entityTypeId().toString()))
        .toList();

    private MobReplacementTargets() {
    }

    public static List<Target> all() {
        return TARGETS;
    }

    public static boolean isSupported(EntityType<?> entityType) {
        if (entityType == EntityType.PLAYER) {
            return false;
        }
        Identifier entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return entityTypeId != null
            && "minecraft".equals(entityTypeId.getNamespace())
            && entityType.getCategory() != MobCategory.MISC;
    }

    public record Target(Identifier entityTypeId, EntityType<?> entityType, Component displayName) {
    }
}
