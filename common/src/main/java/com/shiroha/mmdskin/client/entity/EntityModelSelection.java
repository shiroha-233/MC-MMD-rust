// 负责统一解析实体当前选择的玩家模型或配置式生物替换模型。
package com.shiroha.mmdskin.client.entity;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class EntityModelSelection {
    private EntityModelSelection() {
    }

    public static String resolveModelName(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return MobReplacementResolver.getReplacementModelName(entity);
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean local = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        String selected = PlayerModelSyncManager.getPlayerModel(
                player.getUUID(), player.getGameProfile().name(), local);
        if (selected == null || selected.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(selected)) {
            return null;
        }
        return selected;
    }
}
