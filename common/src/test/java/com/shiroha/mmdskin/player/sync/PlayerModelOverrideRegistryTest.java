/* 文件职责：验证本地「指定玩家模型」的键设计与优先级（纯本地，不涉及服务器）。 */
package com.shiroha.mmdskin.player.sync;

import com.shiroha.mmdskin.config.GameDirectoryProvider;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerModelOverrideRegistryTest {

    private static final UUID ALICE_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ALICE_UUID_NEW_SESSION = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        GameDirectoryProvider.setProvider(() -> tempDir.toFile());
        PlayerModelOverrideRegistry.resetForTests();
    }

    @AfterEach
    void tearDown() {
        PlayerModelOverrideRegistry.resetForTests();
        GameDirectoryProvider.setProvider(null);
    }

    @Test
    void shouldNormalizeNameCaseInsensitively() {
        assertEquals("steve", PlayerModelOverride.normalizeName("  Steve  "));
        assertEquals("steve", PlayerModelOverride.normalizeName("STEVE"));
        assertEquals("", PlayerModelOverride.normalizeName(null));
    }

    @Test
    void shouldMatchByNameEvenWhenUuidDiffers() {
        PlayerModelOverrideRegistry.setOverride("Steve", ALICE_UUID, "ModelA");

        // 离线模式 / 破译服务器上同一个名字每次进服 UUID 都不同：
        // 名字是主判断依据，UUID 变了也认。
        PlayerModelOverride found =
                PlayerModelOverrideRegistry.findOverride("steve", ALICE_UUID_NEW_SESSION);
        assertNotNull(found);
        assertEquals("ModelA", found.modelName());
    }

    @Test
    void shouldMatchByUuidWhenNameChanged() {
        PlayerModelOverrideRegistry.setOverride("Steve", ALICE_UUID, "ModelA");

        // 改了名：名字没命中，但 UUID 命中 → 仍然认得是同一个人。
        PlayerModelOverride found =
                PlayerModelOverrideRegistry.findOverride("SteveNew", ALICE_UUID);
        assertNotNull(found);
        assertEquals("ModelA", found.modelName());
    }

    @Test
    void shouldNotMatchUnrelatedPlayer() {
        PlayerModelOverrideRegistry.setOverride("Steve", ALICE_UUID, "ModelA");

        assertNull(PlayerModelOverrideRegistry.findOverride("Alex", UUID.randomUUID()));
        assertNull(PlayerModelOverrideRegistry.findOverride("", null));
    }

    @Test
    void shouldRemoveByName() {
        PlayerModelOverrideRegistry.setOverride("Steve", ALICE_UUID, "ModelA");
        assertTrue(PlayerModelOverrideRegistry.isOverridden("steve", ALICE_UUID));

        PlayerModelOverrideRegistry.removeOverride("STEVE");

        assertNull(PlayerModelOverrideRegistry.findOverride("steve", ALICE_UUID));
        assertNull(PlayerModelOverrideRegistry.findOverride("SteveNew", ALICE_UUID));
    }

    @Test
    void shouldStoreOfflineOverrideWithoutUuid() {
        PlayerModelOverrideRegistry.setOverride("Ghost", null, "ModelB");

        PlayerModelOverride found = PlayerModelOverrideRegistry.findOverride("ghost", null);
        assertNotNull(found);
        assertNull(found.uuid());
        assertEquals("ModelB", found.modelName());
    }

    @Test
    void shouldOverrideSelfSelectedModelInResolver() {
        PlayerModelOverrideRegistry.setOverride("Steve", ALICE_UUID, "ModelA");

        // 本地指定优先于"他自己选的模型"和"自动下载回来的模型"。
        assertEquals("ModelA",
                PlayerModelSyncManager.getPlayerModel(ALICE_UUID, "Steve", false));
    }

    @Test
    void shouldPersistToLocalConfigFile() {
        PlayerModelOverrideRegistry.setOverride("Steve", ALICE_UUID, "ModelA");

        assertTrue(PlayerModelOverrideRegistry.configFile().isFile());

        // 重新载入（模拟重启客户端）后记录还在。
        PlayerModelOverrideRegistry.resetForTests();
        PlayerModelOverride found = PlayerModelOverrideRegistry.findOverride("Steve", ALICE_UUID);
        assertNotNull(found);
        assertEquals("ModelA", found.modelName());
    }
}
