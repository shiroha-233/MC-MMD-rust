/* 文件职责：验证全局客户端配置的归一化、复制与 UTF-8 持久化行为。 */
package com.shiroha.mmdskin.config;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ConfigDataTest {
    @Test
    void shouldClampPerformancePhysicsAndVrFields() throws Exception {
        ConfigData data = new ConfigData();
        data.performanceLogIntervalSeconds = 0;
        data.maxVisibleModelsPerFrame = 0;
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
        data.mobModelReplacements = null;

        invokeNormalize(data);

        assertEquals(1.0f, data.vrArmIKStrength);
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

    private static void invokeNormalize(ConfigData data) throws Exception {
        Method method = ConfigData.class.getDeclaredMethod("normalize");
        method.setAccessible(true);
        method.invoke(data);
    }
}
