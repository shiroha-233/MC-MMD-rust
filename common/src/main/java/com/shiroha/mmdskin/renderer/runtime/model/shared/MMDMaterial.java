package com.shiroha.mmdskin.renderer.runtime.model.shared;

/**
 * MMD 模型材质（统一定义，避免多个渲染器重复定义）。
 */
public class MMDMaterial {
    public int tex = 0;
    public boolean hasAlpha = false;
    public String name = "";
    public String texturePath = "";
    public boolean outlineEnabled = true;

    public boolean ownsTexture = false;

    public void updateOutlinePolicy() {
        outlineEnabled = shouldDrawOutline(name, texturePath);
    }

    private static boolean shouldDrawOutline(String materialName, String texturePath) {
        return !containsFacialFeatureToken(materialName) && !containsFacialFeatureToken(texturePath);
    }

    private static boolean containsFacialFeatureToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        String[] tokens = {
                "eye", "eyes", "eyeline", "eyelash", "eyelid", "iris", "pupil", "brow", "eyebrow",
                "mouth", "lip", "teeth", "tooth", "tongue", "gum", "lash", "highlight", "eyeshadow",
                "瞳", "目", "眉", "睫", "口", "唇", "牙", "舌", "ハイライト", "まつげ", "くち",
                "くちびる", "アイ", "アイライン", "アイラッシュ"
        };

        for (String token : tokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
