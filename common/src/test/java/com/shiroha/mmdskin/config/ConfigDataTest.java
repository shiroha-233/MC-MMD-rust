/* 文件职责：验证全局客户端配置的归一化、复制与 UTF-8 持久化行为。 */
package com.shiroha.mmdskin.config;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDataTest {
    @Test
    void shouldClampPerformancePhysicsAndVrFields() throws Exception {
        ConfigData data = new ConfigData();
        data.performanceLogIntervalSeconds = 0;
        data.maxVisibleModelsPerFrame = 0;
        data.modelLoadMaxDistance = -3.0f;
        data.animationLodMediumDistance = -1.0f;
        data.animationLodFarDistance = 2.0f;
        data.animationLodMediumUpdateInterval = 0;
        data.animationLodFarUpdateInterval = 0;
        data.maxPhysicsModelsPerFrame = 0;
        data.physicsLodMaxDistance = -5.0f;
        data.vrArmIKStrength = 9.0f;

        invokeNormalize(data);

        assertEquals(1, data.performanceLogIntervalSeconds);
        assertEquals(1, data.maxVisibleModelsPerFrame);
        assertEquals(0.0f, data.modelLoadMaxDistance);
        assertEquals(0.0f, data.animationLodMediumDistance);
        assertEquals(2.0f, data.animationLodFarDistance);
        assertEquals(1, data.animationLodMediumUpdateInterval);
        assertEquals(1, data.animationLodFarUpdateInterval);
        assertEquals(1, data.maxPhysicsModelsPerFrame);
        assertEquals(0.0f, data.physicsLodMaxDistance);
        assertEquals(1.0f, data.vrArmIKStrength);
    }

    @Test
    void shouldFallbackVrStrengthAndKeepMobReplacementMapInitialized() throws Exception {
        ConfigData data = new ConfigData();
        data.vrArmIKStrength = Float.NaN;
        data.modelLoadMaxDistance = Float.NaN;
        data.mobModelReplacements = null;

        invokeNormalize(data);

        assertEquals(1.0f, data.vrArmIKStrength);
        // NaN 必须回退到默认闸门距离，否则距离比较恒不成立（等于永不加载模型）。
        assertEquals(64.0f, data.modelLoadMaxDistance);
        assertNotNull(data.mobModelReplacements);
    }

    @Test
    void shouldPreserveReplacementMappingsWhenCopying() {
        ConfigData source = new ConfigData();
        source.mobModelReplacements = new LinkedHashMap<>();
        source.mobModelReplacements.put("minecraft:zombie", "mmd/zombie");

        ConfigData target = new ConfigData();
        source.copyTo(target);

        assertEquals("mmd/zombie", target.mobModelReplacements.get("minecraft:zombie"));
        assertEquals(1, target.mobModelReplacements.size());
    }

    @Test
    void shouldCopyIntoProvidedTargetInstance() {
        ConfigData source = new ConfigData();
        source.mobModelReplacements.put("minecraft:skeleton", "mmd/skeleton");
        ConfigData target = new ConfigData();

        source.copyTo(target);

        assertNotSame(source.mobModelReplacements, target.mobModelReplacements);
        assertEquals(source.textureCacheBudgetMB, target.textureCacheBudgetMB);
        assertEquals("mmd/skeleton", target.mobModelReplacements.get("minecraft:skeleton"));
    }

    @Test
    void shouldPersistUtf8ConfigAndReloadNormalizedValues(@TempDir Path tempDir) {
        ConfigData source = new ConfigData();
        source.mobModelReplacements.put("minecraft:苦力怕", "模型-测试");
        source.vrArmIKStrength = 3.0f;
        source.performanceLogIntervalSeconds = 0;

        source.save(tempDir);
        ConfigData loaded = ConfigData.load(tempDir);

        assertEquals("模型-测试", loaded.mobModelReplacements.get("minecraft:苦力怕"));
        assertEquals(1.0f, loaded.vrArmIKStrength);
        assertEquals(1, loaded.performanceLogIntervalSeconds);
    }

    @Test
    void shouldRemoveTemporaryFileAfterAtomicSave(@TempDir Path tempDir) throws IOException {
        new ConfigData().save(tempDir);

        try (var files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void shouldDefaultAutoDownloadSettingsLikeReferenceBranch() {
        ConfigData data = new ConfigData();

        // 与 1.20.1 参考实现一致：默认关闭，半径 48 格。
        assertFalse(data.autoDownloadNearbyModels);
        assertEquals(48, data.autoDownloadRadius);
    }

    @Test
    void shouldAddAutoDownloadKeysToLegacyConfigFile(@TempDir Path tempDir) throws IOException {
        // 旧配置里没有这两个键（26.3 移植前不存在）。
        Files.writeString(tempDir.resolve("config.json"), "{\n  \"schemaVersion\": 3\n}", StandardCharsets.UTF_8);

        ConfigData loaded = ConfigData.load(tempDir);

        assertFalse(loaded.autoDownloadNearbyModels);
        assertEquals(48, loaded.autoDownloadRadius);

        String written = Files.readString(tempDir.resolve("config.json"), StandardCharsets.UTF_8);
        assertTrue(written.contains("\"autoDownloadNearbyModels\""));
        assertTrue(written.contains("\"autoDownloadRadius\""));
    }

    @Test
    void shouldExposeAutoDownloadSettingsThroughConfigManager() {
        ConfigData data = new ConfigData();
        data.autoDownloadNearbyModels = true;
        data.autoDownloadRadius = 96;

        try {
            ConfigManager.init(new AbstractMmdSkinConfig(data) {
            });

            assertTrue(ConfigManager.isAutoDownloadNearbyModels());
            assertEquals(96, ConfigManager.getAutoDownloadRadius());

            // 没有 provider 时回落到默认值：关闭 + 半径 48。
            ConfigManager.init(null);

            assertFalse(ConfigManager.isAutoDownloadNearbyModels());
            assertEquals(48, ConfigManager.getAutoDownloadRadius());
        } finally {
            ConfigManager.init(null);
        }
    }

    private static void invokeNormalize(ConfigData data) throws Exception {
        Method method = ConfigData.class.getDeclaredMethod("normalize");
        method.setAccessible(true);
        method.invoke(data);
    }
}
