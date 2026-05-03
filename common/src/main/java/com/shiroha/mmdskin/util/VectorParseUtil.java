package com.shiroha.mmdskin.util;

import org.joml.Vector3f;

public final class VectorParseUtil {
    private VectorParseUtil() {
    }

    public static Vector3f parseVec3f(String value) {
        if (value == null || value.isEmpty()) {
            return new Vector3f(0.0f);
        }

        int firstComma = value.indexOf(',');
        if (firstComma < 0) {
            return new Vector3f(0.0f);
        }

        int secondComma = value.indexOf(',', firstComma + 1);
        if (secondComma < 0 || value.indexOf(',', secondComma + 1) >= 0) {
            return new Vector3f(0.0f);
        }

        try {
            return new Vector3f(
                    Float.parseFloat(value.substring(0, firstComma)),
                    Float.parseFloat(value.substring(firstComma + 1, secondComma)),
                    Float.parseFloat(value.substring(secondComma + 1))
            );
        } catch (NumberFormatException e) {
            return new Vector3f(0.0f);
        }
    }
}
