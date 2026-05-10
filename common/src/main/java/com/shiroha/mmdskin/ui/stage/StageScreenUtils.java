package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import net.minecraft.network.chat.Component;

/** 文件职责：StageSelectScreen 使用的无状态静态工具方法。 */
final class StageScreenUtils {

    private StageScreenUtils() {
    }

    static String shortPackStats(StagePack pack) {
        int motion = 0;
        int camera = 0;
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasBones || info.hasMorphs) motion++;
            if (info.hasCamera) camera++;
        }
        return "M" + motion + "/C" + camera + "/A" + pack.getAudioFiles().size();
    }

    static int brighten(int color) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + 18);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + 18);
        int b = Math.min(255, (color & 0xFF) + 18);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static String motionTag(StagePack.VmdFileInfo info) {
        StringBuilder sb = new StringBuilder();
        if (info.hasBones) sb.append('B');
        if (info.hasMorphs) { if (sb.length() > 0) sb.append('/'); sb.append('M'); }
        if (info.hasCamera) { if (sb.length() > 0) sb.append('/'); sb.append('C'); }
        return sb.toString();
    }

    static String stripExtension(String text) {
        int dot = text.lastIndexOf('.');
        return dot > 0 ? text.substring(0, dot) : text;
    }

    static String shorten(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return maxChars <= 2 ? text.substring(0, Math.max(0, maxChars)) : text.substring(0, maxChars - 2) + "..";
    }

    static String wb(String suffix, Object... args) {
        return Component.translatable("gui.mmdskin.stage.workbench." + suffix, args).getString();
    }
}
