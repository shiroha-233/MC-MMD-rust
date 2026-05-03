package com.shiroha.mmdskin.model.runtime;

import com.shiroha.mmdskin.util.VectorParseUtil;
import java.util.Properties;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** 文件职责：缓存模型渲染属性的已解析视图，避免热路径重复读取 Properties。 */
public record ModelRenderProperties(
        float modelScale,
        float inventoryScale,
        float sleepingPitch,
        Vector3fc sleepingTranslation,
        float flyingPitch,
        Vector3fc flyingTranslation,
        float swimmingPitch,
        Vector3fc swimmingTranslation,
        float crawlingPitch,
        Vector3fc crawlingTranslation) {

    public ModelRenderProperties {
        sleepingTranslation = copyVector(sleepingTranslation);
        flyingTranslation = copyVector(flyingTranslation);
        swimmingTranslation = copyVector(swimmingTranslation);
        crawlingTranslation = copyVector(crawlingTranslation);
    }

    public static final ModelRenderProperties DEFAULT =
            new ModelRenderProperties(
                    1.0f,
                    1.0f,
                    0.0f,
                    new Vector3f(),
                    0.0f,
                    new Vector3f(),
                    0.0f,
                    new Vector3f(),
                    0.0f,
                    new Vector3f());

    public static ModelRenderProperties from(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return DEFAULT;
        }
        return new ModelRenderProperties(
                getFloat(properties, "size", 1.0f),
                getFloat(properties, "size_in_inventory", 1.0f),
                getFloat(properties, "sleepingPitch", 0.0f),
                getVector(properties, "sleepingTrans"),
                getFloat(properties, "flyingPitch", 0.0f),
                getVector(properties, "flyingTrans"),
                getFloat(properties, "swimmingPitch", 0.0f),
                getVector(properties, "swimmingTrans"),
                getFloat(properties, "crawlingPitch", 0.0f),
                getVector(properties, "crawlingTrans"));
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

    private static Vector3fc copyVector(Vector3fc value) {
        return value == null ? new Vector3f() : new Vector3f(value);
    }
}
