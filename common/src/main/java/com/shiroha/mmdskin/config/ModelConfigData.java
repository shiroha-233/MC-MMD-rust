/* 文件职责：保存单个模型的独立配置数据并提供归一化边界。 */
package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class ModelConfigData {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final float DEFAULT_EYE_MAX_ANGLE = 0.1745f;
    public static final float MIN_EYE_MAX_ANGLE = 0.05f;
    public static final float MAX_EYE_MAX_ANGLE = 1.0f;
    public static final float DEFAULT_MODEL_SCALE = 1.0f;
    public static final float MIN_MODEL_SCALE = 0.5f;
    public static final float MAX_MODEL_SCALE = 2.0f;
    public static final float DEFAULT_HELD_ITEM_SCALE = 1.0f;
    public static final float MIN_HELD_ITEM_SCALE = 0.25f;
    public static final float MAX_HELD_ITEM_SCALE = 2.0f;

    public boolean eyeTrackingEnabled = true;
    public float eyeMaxAngle = DEFAULT_EYE_MAX_ANGLE;
    public float modelScale = DEFAULT_MODEL_SCALE;

    @SerializedName(value = "heldItemScale", alternate = {"firstPersonHeldBlockScale"})
    public float heldItemScale = DEFAULT_HELD_ITEM_SCALE;

    public Set<Integer> hiddenMaterials = new HashSet<>();

    public static ModelConfigData load(File configFile) {
        if (!configFile.exists()) {
            return new ModelConfigData();
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            ModelConfigData config = GSON.fromJson(reader, ModelConfigData.class);
            if (config == null) {
                return new ModelConfigData();
            }
            return config.normalizeInPlace();
        } catch (Exception e) {
            LOGGER.warn("加载模型配置失败: {}, 使用默认配置", e.getMessage());
            return new ModelConfigData();
        }
    }

    public ModelConfigData copy() {
        ModelConfigData copy = new ModelConfigData();
        copy.eyeTrackingEnabled = eyeTrackingEnabled;
        copy.eyeMaxAngle = eyeMaxAngle;
        copy.modelScale = modelScale;
        copy.heldItemScale = heldItemScale;
        copy.hiddenMaterials = hiddenMaterials == null ? new HashSet<>() : new HashSet<>(hiddenMaterials);
        return copy;
    }

    public ModelConfigData normalizedCopy() {
        return copy().normalizeInPlace();
    }

    public ModelConfigData normalizeInPlace() {
        eyeMaxAngle = clampOrDefault(eyeMaxAngle, MIN_EYE_MAX_ANGLE, MAX_EYE_MAX_ANGLE, DEFAULT_EYE_MAX_ANGLE);
        modelScale = clampOrDefault(modelScale, MIN_MODEL_SCALE, MAX_MODEL_SCALE, DEFAULT_MODEL_SCALE);
        heldItemScale = clampOrDefault(heldItemScale, MIN_HELD_ITEM_SCALE, MAX_HELD_ITEM_SCALE, DEFAULT_HELD_ITEM_SCALE);
        hiddenMaterials = normalizeHiddenMaterials(hiddenMaterials);
        return this;
    }

    public void save(File configFile) {
        PathConstants.ensureDirectoryExists(configFile.getParentFile());
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
            LOGGER.debug("模型配置已保存: {}", configFile.getName());
        } catch (IOException e) {
            LOGGER.error("保存模型配置失败: {}", e.getMessage());
        }
    }

    private static float clampOrDefault(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Set<Integer> normalizeHiddenMaterials(Set<Integer> source) {
        if (source == null || source.isEmpty()) {
            return new HashSet<>();
        }
        Set<Integer> normalized = new HashSet<>();
        for (Integer index : source) {
            if (index != null && index >= 0) {
                normalized.add(index);
            }
        }
        return normalized;
    }
}
