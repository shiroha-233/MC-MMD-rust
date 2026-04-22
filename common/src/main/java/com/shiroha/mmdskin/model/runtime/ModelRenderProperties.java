package com.shiroha.mmdskin.model.runtime;

import com.shiroha.mmdskin.util.VectorParseUtil;
import java.util.Properties;
import org.joml.Vector3f;

/** 文件职责：缓存模型渲染属性的已解析视图，避免热路径重复读取 Properties。 */
public record ModelRenderProperties(
        float modelScale,
        float inventoryScale,
        float sleepingPitch,
        Vector3f sleepingTranslation) {

    public static final ModelRenderProperties DEFAULT =
            new ModelRenderProperties(1.0f, 1.0f, 0.0f, new Vector3f());

    public static ModelRenderProperties from(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return DEFAULT;
        }
        return new ModelRenderProperties(
                getFloat(properties, "size", 1.0f),
                getFloat(properties, "size_in_inventory", 1.0f),
                getFloat(properties, "sleepingPitch", 0.0f),
                getVector(properties, "sleepingTrans"));
    }

    private static float getFloat(Properties properties, String key, float defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static Vector3f getVector(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? new Vector3f() : VectorParseUtil.parseVec3f(value);
    }
}
