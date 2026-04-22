package com.shiroha.mmdskin.render.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.Comparator;
import java.util.List;

/**
 * 可用于 MMD 替换的原版生物目标列表。
 */
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

        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return entityTypeId != null
            && "minecraft".equals(entityTypeId.getNamespace())
            && entityType.getCategory() != MobCategory.MISC;
    }

    public record Target(ResourceLocation entityTypeId, EntityType<?> entityType, Component displayName) {
    }
}
