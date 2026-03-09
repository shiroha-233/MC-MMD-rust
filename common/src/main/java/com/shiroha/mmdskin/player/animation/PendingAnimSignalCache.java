package com.shiroha.mmdskin.player.animation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待处理动画信号缓存。
 */
public final class PendingAnimSignalCache {

    public enum SignalType { RESET }

    private static final Map<UUID, SignalType> pending = new ConcurrentHashMap<>();
    private static final long SIGNAL_TTL_MS = 30_000;
    private static final Map<UUID, Long> signalTime = new ConcurrentHashMap<>();

    public static void put(UUID playerUUID, SignalType type) {
        pending.put(playerUUID, type);
        signalTime.put(playerUUID, System.currentTimeMillis());
    }

    public static SignalType consume(UUID playerUUID) {
        Long time = signalTime.get(playerUUID);
        if (time != null && System.currentTimeMillis() - time > SIGNAL_TTL_MS) {
            pending.remove(playerUUID);
            signalTime.remove(playerUUID);
            return null;
        }
        SignalType signal = pending.remove(playerUUID);
        if (signal != null) {
            signalTime.remove(playerUUID);
        }
        return signal;
    }

    public static void onDisconnect() {
        pending.clear();
        signalTime.clear();
    }

    private PendingAnimSignalCache() {}
}
