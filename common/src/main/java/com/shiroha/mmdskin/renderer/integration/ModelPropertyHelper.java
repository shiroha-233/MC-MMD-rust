package com.shiroha.mmdskin.renderer.integration;

import org.joml.Vector3f;

import java.util.Properties;

/**
 * 模型渲染属性读取工具。
 */
public final class ModelPropertyHelper {

    private ModelPropertyHelper() {
    }

    public static float[] getModelSize(Properties properties) {
        return new float[] {
            getFloat(properties, "size", 1.0f),
            getFloat(properties, "size_in_inventory", 1.0f)
        };
    }

    public static float getFloat(Properties properties, String key, float defaultValue) {
        String value = getString(properties, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Vector3f getVector(Properties properties, String key) {
        String value = getString(properties, key);
        return value == null ? new Vector3f(0.0f) : com.shiroha.mmdskin.util.VectorParseUtil.parseVec3f(value);
    }

    public static String getString(Properties properties, String key) {
        return properties == null ? null : properties.getProperty(key);
    }
}
