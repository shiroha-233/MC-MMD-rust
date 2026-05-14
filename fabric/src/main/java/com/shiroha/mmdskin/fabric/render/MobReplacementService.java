/* 文件职责：解析 Fabric 侧生物替换配置并过滤无效目标。 */
package com.shiroha.mmdskin.fabric.render;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.UIConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class MobReplacementService {
    private MobReplacementService() {
    }

    public static String getReplacementModelName(LivingEntity entity) {
        if (entity == null
            || !MobReplacementTargets.isSupported(entity.getType())
            || isMaidEntity(entity)) {
            return null;
        }

        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityTypeId == null) {
            return null;
        }

        String modelName = ConfigManager.getMobModelReplacement(entityTypeId.toString());
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }

        return ModelInfo.findByFolderName(modelName) != null ? modelName : null;
    }

    private static boolean isMaidEntity(LivingEntity entity) {
        String className = entity.getClass().getName();
        return className.contains("EntityMaid") || className.contains("touhoulittlemaid");
    }
}
