/* 文件职责：验证模型配置管理器的副本语义、保存归一化与空值回退。 */
package com.shiroha.mmdskin.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ModelConfigManagerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        ModelConfigManager.invalidateAll();
        ModelConfigManager.setConfigRootDirSupplierForTesting(null);
    }

    @Test
    void shouldReturnDefensiveCopyWithoutMutatingCachedLiveConfig() {
        ModelConfigManager.setConfigRootDirSupplierForTesting(() -> tempDir.toFile());

        ModelConfigData source = new ModelConfigData();
        source.heldItemScale = 1.25f;
        ModelConfigManager.saveConfig("demo", source);

        ModelConfigData editable = ModelConfigManager.getConfig("demo");
        ModelConfigData live = ModelConfigManager.getLiveConfig("demo");

        assertNotSame(editable, live);
        editable.heldItemScale = 2.0f;

        assertEquals(1.25f, ModelConfigManager.getLiveConfig("demo").heldItemScale);
    }

    @Test
    void shouldNormalizeAndFallbackWhenSavingNullConfig() {
        ModelConfigManager.setConfigRootDirSupplierForTesting(() -> tempDir.toFile());

        ModelConfigData outOfRange = new ModelConfigData();
        outOfRange.heldItemScale = 99.0f;
        ModelConfigManager.saveConfig("normalized", outOfRange);

        assertEquals(ModelConfigData.MAX_HELD_ITEM_SCALE, ModelConfigManager.getLiveConfig("normalized").heldItemScale);

        ModelConfigManager.saveConfig("fallback", null);
        assertEquals(ModelConfigData.DEFAULT_HELD_ITEM_SCALE, ModelConfigManager.getLiveConfig("fallback").heldItemScale);
    }
}
