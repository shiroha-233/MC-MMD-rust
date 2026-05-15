package com.shiroha.mmdskin.render.material;

import java.util.Locale;

/** MMD 模型材质定义。 */
public class ModelMaterial {
    private static final String[] FACIAL_TOKENS = {
            "eye", "eyes", "eyeline", "eyelash", "eyelid", "iris", "pupil", "brow", "eyebrow",
            "mouth", "lip", "teeth", "tooth", "tongue", "gum", "lash", "highlight", "eyeshadow",
            "瞳", "目", "眉", "睫", "口", "唇", "牙", "舌", "ハイライト", "まつげ", "くち",
            "くちびる", "アイ", "アイライン", "アイラッシュ"
    };

    public int tex = 0;
    public boolean hasAlpha = false;
    public String name = "";
    public String texturePath = "";
    public boolean ownsTexture = false;

    private Boolean cachedIsFacialFeature;

    public boolean isFacialFeature() {
        if (cachedIsFacialFeature == null) {
            cachedIsFacialFeature = containsFacialToken(name) || containsFacialToken(texturePath);
        }
        return cachedIsFacialFeature;
    }

    private static boolean containsFacialToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String token : FACIAL_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
