package com.shiroha.mmdskin.player.model;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家模型解析工具。
 */
public final class PlayerModelResolver {

    private PlayerModelResolver() {
    }

    public record Result(MMDModelManager.Model model, String playerName) {}

    public static String getCacheKey(Player player) {
        if (player == null) return "unknown";

        String uuid = player.getStringUUID();
        if (uuid != null && !uuid.isEmpty()) {
            return uuid;
        }

        return player.getName().getString();
    }

    public static Result resolve(Player player) {
        if (player == null) return null;

        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);

        if (selectedModel == null || selectedModel.isEmpty()
                || selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            return null;
        }

        MMDModelManager.Model m = MMDModelManager.GetModel(selectedModel, getCacheKey(player));
        if (m == null) {
            return null;
        }

        return new Result(m, playerName);
    }
}
