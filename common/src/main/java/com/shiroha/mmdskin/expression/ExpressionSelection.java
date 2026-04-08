package com.shiroha.mmdskin.expression;

import java.util.Objects;

public record ExpressionSelection(Type type, String value) {
    public enum Type {
        RESET,
        PRESET,
        FILE
    }

    public ExpressionSelection {
        Objects.requireNonNull(type, "type");
        value = value == null ? "" : value;
    }

    public static ExpressionSelection reset() {
        return new ExpressionSelection(Type.RESET, "");
    }

    public static ExpressionSelection preset(String presetId) {
        return new ExpressionSelection(Type.PRESET, presetId);
    }

    public static ExpressionSelection file(String morphName) {
        return new ExpressionSelection(Type.FILE, morphName);
    }
}
