package com.shiroha.mmdskin.ui.follow;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程玩家动画ID缓存，用于跳舞跟随时获取目标动画
 */
public final class RemoteAnimCache {

    private static final ConcurrentHashMap<UUID, String> cache = new ConcurrentHashMap<>();

    private RemoteAnimCache() {}

    public static void put(UUID playerUUID, String animId) {
        if (playerUUID != null && animId != null && !animId.isEmpty()) {
            cache.put(playerUUID, animId);
        }
    }

    public static String get(UUID playerUUID) {
        return playerUUID != null ? cache.get(playerUUID) : null;
    }

    public static void remove(UUID playerUUID) {
        if (playerUUID != null) cache.remove(playerUUID);
    }

    public static void clear() {
        cache.clear();
    }
}
