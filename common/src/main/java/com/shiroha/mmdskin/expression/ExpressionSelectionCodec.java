package com.shiroha.mmdskin.expression;

public final class ExpressionSelectionCodec {
    public static final String RESET_TOKEN = "__reset__";
    private static final String PRESET_PREFIX = "preset:";
    private static final String FILE_PREFIX = "vpd:";

    private ExpressionSelectionCodec() {
    }

    public static String encode(ExpressionSelection selection) {
        if (selection == null) {
            return "";
        }
        return switch (selection.type()) {
            case RESET -> RESET_TOKEN;
            case PRESET -> PRESET_PREFIX + selection.value();
            case FILE -> FILE_PREFIX + selection.value();
        };
    }

    public static ExpressionSelection decode(String token) {
        if (token == null || token.isEmpty() || RESET_TOKEN.equals(token)) {
            return ExpressionSelection.reset();
        }
        if (token.startsWith(PRESET_PREFIX)) {
            return ExpressionSelection.preset(token.substring(PRESET_PREFIX.length()));
        }
        if (token.startsWith(FILE_PREFIX)) {
            return ExpressionSelection.file(token.substring(FILE_PREFIX.length()));
        }
        return ExpressionSelection.file(token);
    }
}
