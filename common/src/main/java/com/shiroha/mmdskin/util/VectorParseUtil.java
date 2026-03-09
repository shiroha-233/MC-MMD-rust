package com.shiroha.mmdskin.util;

import org.joml.Vector3f;

public final class VectorParseUtil {
    private VectorParseUtil() {
    }

    public static Vector3f parseVec3f(String value) {
        if (value == null || value.isEmpty()) {
            return new Vector3f(0.0f);
        }

        String[] parts = value.split(",");
        if (parts.length != 3) {
            return new Vector3f(0.0f);
        }

        try {
            return new Vector3f(
                    Float.parseFloat(parts[0]),
                    Float.parseFloat(parts[1]),
                    Float.parseFloat(parts[2])
            );
        } catch (NumberFormatException e) {
            return new Vector3f(0.0f);
        }
    }
}
