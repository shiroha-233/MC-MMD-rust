package com.shiroha.mmdskin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelConfigDataTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldClampHeldItemScaleIntoSupportedRange() {
        ModelConfigData data = new ModelConfigData();
        data.heldItemScale = 99.0f;

        ModelConfigData normalized = data.normalizeInPlace();

        assertEquals(ModelConfigData.MAX_HELD_ITEM_SCALE, normalized.heldItemScale);
    }

    @Test
    void shouldFallbackHeldItemScaleWhenValueIsNotFinite() {
        ModelConfigData data = new ModelConfigData();
        data.heldItemScale = Float.NaN;

        ModelConfigData normalized = data.normalizeInPlace();

        assertEquals(ModelConfigData.DEFAULT_HELD_ITEM_SCALE, normalized.heldItemScale);
    }

    @Test
    void shouldLoadLegacyHeldItemScaleAlias() throws Exception {
        Path configFile = tempDir.resolve("legacy-model.json");
        Files.writeString(configFile, """
                {
                  "firstPersonHeldBlockScale": 1.75
                }
                """, StandardCharsets.UTF_8);

        ModelConfigData loaded = ModelConfigData.load(configFile.toFile());

        assertEquals(1.75f, loaded.heldItemScale);
    }

    @Test
    void shouldDropNegativeHiddenMaterialIndices() {
        ModelConfigData data = new ModelConfigData();
        data.hiddenMaterials.add(-1);
        data.hiddenMaterials.add(2);

        ModelConfigData normalized = data.normalizeInPlace();

        assertEquals(1, normalized.hiddenMaterials.size());
        assertTrue(normalized.hiddenMaterials.contains(2));
    }
}
