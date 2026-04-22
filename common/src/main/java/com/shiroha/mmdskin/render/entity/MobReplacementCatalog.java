package com.shiroha.mmdskin.render.entity;

import java.util.List;
import net.minecraft.world.entity.EntityType;

/** 文件职责：向配置界面暴露可替换的原版生物目录。 */
public final class MobReplacementCatalog {
    private MobReplacementCatalog() {
    }

    public static List<MobReplacementTargets.Target> all() {
        return MobReplacementTargets.all();
    }

    public static boolean isSupported(EntityType<?> entityType) {
        return MobReplacementTargets.isSupported(entityType);
    }
}
