package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 文件职责：提供语音运行时共享的键值与上下文规范化工具。 */
final class VoiceRuntimeSupport {
    private VoiceRuntimeSupport() {
    }

    static boolean isClientRuntimeReady() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null && minecraft.gameDirectory != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    static String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }
        return modelName;
    }

    static String buildSpeakerKey(com.shiroha.mmdskin.voice.VoiceTargetType type, String identity) {
        return type.name().toLowerCase() + ':' + identity;
    }

    static String resolvePlayerModelName(AbstractClientPlayer player, boolean localPlayer) {
        return normalizeModelName(PlayerModelSyncManager.getPlayerModel(
                player.getUUID(),
                player.getName().getString(),
                localPlayer
        ));
    }

    static String resolveLocalPlayerModelName() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return null;
            }
            return resolvePlayerModelName(minecraft.player, true);
        } catch (Throwable throwable) {
            return null;
        }
    }

    static List<String> normalizeDetailKeys(List<String> detailKeys) {
        if (detailKeys == null || detailKeys.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String detailKey : detailKeys) {
            if (detailKey == null || detailKey.isBlank()) {
                continue;
            }
            String normalized = detailKey.trim().replace('\\', '/').replace(':', '/');
            if (seen.add(normalized)) {
                results.add(normalized);
            }
            int slash = normalized.lastIndexOf('/');
            if (slash > 0) {
                String leaf = normalized.substring(slash + 1);
                if (seen.add(leaf)) {
                    results.add(leaf);
                }
            }
            int dot = normalized.lastIndexOf('.');
            while (dot > 0) {
                String parent = normalized.substring(0, dot);
                if (seen.add(parent)) {
                    results.add(parent);
                }
                dot = parent.lastIndexOf('.');
            }
        }
        return List.copyOf(results);
    }

    static String toDetailPath(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return id.getNamespace() + '/' + id.getPath();
    }
}
