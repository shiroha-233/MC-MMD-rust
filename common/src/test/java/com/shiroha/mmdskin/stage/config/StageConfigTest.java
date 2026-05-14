package com.shiroha.mmdskin.stage.config;

import com.google.gson.Gson;
import com.shiroha.mmdskin.config.StageConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageConfigTest {

    @Test
    void shouldClampLoadedAudioVolumeAndCameraHeight() throws Exception {
        StageConfig config = decode("""
                {
                  "lastStagePack": null,
                  "cameraHeightOffset": 99.0,
                  "audioVolume": -3.0
                }
                """);

        invokeNormalize(config, "{\"lastStagePack\":null,\"cameraHeightOffset\":99.0,\"audioVolume\":-3.0}");

        assertEquals("", config.lastStagePack);
        assertEquals(2.0f, config.cameraHeightOffset);
        assertEquals(0.0f, config.audioVolume);
    }

    @Test
    void shouldDefaultMissingAudioVolumeToOne() throws Exception {
        StageConfig config = decode("""
                {
                  "lastStagePack": "demo",
                  "cameraHeightOffset": 0.5
                }
                """);

        invokeNormalize(config, "{\"lastStagePack\":\"demo\",\"cameraHeightOffset\":0.5}");

        assertEquals("demo", config.lastStagePack);
        assertEquals(0.5f, config.cameraHeightOffset);
        assertEquals(1.0f, config.audioVolume);
    }

    private static StageConfig decode(String json) {
        return new Gson().fromJson(json, StageConfig.class);
    }

    private static void invokeNormalize(StageConfig config, String json) throws Exception {
        Method method = StageConfig.class.getDeclaredMethod("normalizeLoadedValues", com.google.gson.JsonObject.class);
        method.setAccessible(true);
        method.invoke(config, new Gson().fromJson(json, com.google.gson.JsonObject.class));
    }
}
