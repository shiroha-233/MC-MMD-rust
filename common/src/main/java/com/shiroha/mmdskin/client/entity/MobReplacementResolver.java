// 负责解析配置式生物模型替换并过滤无效目标。
package com.shiroha.mmdskin.client.entity;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.UIConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

public final class MobReplacementResolver {
    private MobReplacementResolver() {
    }

    public static String getReplacementModelName(LivingEntity entity) {
        if (entity == null || entity.getType() == EntityTypes.PLAYER) {
            return null;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null) {
            return null;
        }
        String modelName = ConfigManager.getMobModelReplacement(id.toString());
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }
        return ModelInfo.findByFolderName(modelName) == null ? null : modelName;
    }
}
