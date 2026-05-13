/* 文件职责：验证模型独立配置的归一化和防御性复制。 */
package com.shiroha.mmdskin.config;

import java.io.File;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ModelConfigDataTest {

    @Test
    void shouldNormalizeOutOfRangeValuesAndHiddenMaterials() {
        ModelConfigData config = new ModelConfigData();
        config.eyeMaxAngle = Float.NaN;
        config.modelScale = 100.0f;
        config.heldItemScale = -1.0f;
        config.hiddenMaterials = new HashSet<>(Set.of(-2, 3));

        ModelConfigData normalized = config.normalizedCopy();

        assertEquals(ModelConfigData.DEFAULT_EYE_MAX_ANGLE, normalized.eyeMaxAngle);
        assertEquals(ModelConfigData.MAX_MODEL_SCALE, normalized.modelScale);
        assertEquals(ModelConfigData.MIN_HELD_ITEM_SCALE, normalized.heldItemScale);
        assertEquals(Set.of(3), normalized.hiddenMaterials);
    }

    @Test
    void shouldCopyHiddenMaterialsDefensively() {
        ModelConfigData config = new ModelConfigData();
        config.hiddenMaterials.add(7);

        ModelConfigData copied = config.copy();
        copied.hiddenMaterials.add(9);

        assertNotSame(config.hiddenMaterials, copied.hiddenMaterials);
        assertEquals(Set.of(7), config.hiddenMaterials);
        assertEquals(Set.of(7, 9), copied.hiddenMaterials);
    }

    @Test
    void shouldLoadLegacyHeldBlockScaleField() {
        File tempFile = new File("build/tmp/test-legacy-model-config.json");
        tempFile.getParentFile().mkdirs();
        ModelConfigData legacy = new ModelConfigData();
        legacy.heldItemScale = 1.0f;
        legacy.save(tempFile);

        String legacyJson = """
                {
                  "firstPersonHeldBlockScale": 0.6
                }
                """;
        tempFile.delete();
        try {
            java.nio.file.Files.writeString(tempFile.toPath(), legacyJson, java.nio.charset.StandardCharsets.UTF_8);
            ModelConfigData loaded = ModelConfigData.load(tempFile);
            assertEquals(0.6f, loaded.heldItemScale);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            tempFile.delete();
        }
    }
}
