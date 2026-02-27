package com.shiroha.mmdskin.renderer.animation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待处理动画信号缓存
 * 当收到 RESET_PHYSICS / STAGE_END 时目标玩家不在渲染范围，
 * 缓存该信号，等玩家重新出现时由渲染循环应用。
 */
public final class PendingAnimSignalCache {

    public enum SignalType { RESET, STAGE_END }

    private static final Map<UUID, SignalType> pending = new ConcurrentHashMap<>();
    private static final long SIGNAL_TTL_MS = 30_000;
    private static final Map<UUID, Long> signalTime = new ConcurrentHashMap<>();

    public static void put(UUID playerUUID, SignalType type) {
        pending.put(playerUUID, type);
        signalTime.put(playerUUID, System.currentTimeMillis());
    }

    /**
     * 消费指定玩家的待处理信号（取出后删除）
     */
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