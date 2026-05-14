/* 文件职责：验证玩家模型同步缓存的远端优先级与清理语义。 */
package com.shiroha.mmdskin.ui.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerModelSyncManagerTest {
    @AfterEach
    void tearDown() {
        PlayerModelSyncManager.setNetworkBroadcaster(null);
        PlayerModelSyncManager.onDisconnect();
    }

    @Test
    void shouldPreferCachedRemoteModelForRemotePlayers() {
        UUID playerId = UUID.randomUUID();
        PlayerModelSyncManager.onRemotePlayerModelReceived(playerId, "mmd/remote");

        assertEquals("mmd/remote", PlayerModelSyncManager.getPlayerModel(playerId, "Ignored", false));
    }

    @Test
    void shouldClearRemoteCacheOnEmptyPayloadAndDisconnect() {
        UUID playerId = UUID.randomUUID();
        PlayerModelSyncManager.onRemotePlayerModelReceived(playerId, "mmd/remote");
        PlayerModelSyncManager.onRemotePlayerModelReceived(playerId, "");
        assertTrue(PlayerModelSyncManager.getAllRemotePlayerModels().isEmpty());

        PlayerModelSyncManager.onRemotePlayerModelReceived(playerId, "mmd/remote");
        PlayerModelSyncManager.onDisconnect();
        assertTrue(PlayerModelSyncManager.getAllRemotePlayerModels().isEmpty());
    }

    @Test
    void shouldBroadcastLocalSelectionThroughConfiguredBroadcaster() {
        UUID playerId = UUID.randomUUID();
        AtomicReference<String> captured = new AtomicReference<>();
        PlayerModelSyncManager.setNetworkBroadcaster((uuid, modelName) -> {
            if (playerId.equals(uuid)) {
                captured.set(modelName);
            }
        });

        PlayerModelSyncManager.broadcastLocalModelSelection(playerId, "mmd/local");

        assertEquals("mmd/local", captured.get());
    }
}
