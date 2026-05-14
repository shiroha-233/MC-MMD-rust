/* 文件职责：收敛舞台工作台界面共用的文案与格式化工具。 */
package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.network.chat.Component;

/** 文件职责：收敛舞台工作台界面共用的文案与格式化工具。 */
final class StageScreenUtils {

    private StageScreenUtils() {
    }

    static String shortPackStats(StagePack pack) {
        int motion = 0;
        int camera = 0;
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasBones || info.hasMorphs) {
                motion++;
            }
            if (info.hasCamera) {
                camera++;
            }
        }
        return wb("packs.stats.short", motion, camera, pack.getAudioFiles().size());
    }

    static int brighten(int color) {
        return TranslucentTrayChrome.brighten(color, 18);
    }

    static String motionTag(StagePack.VmdFileInfo info) {
        StringBuilder builder = new StringBuilder();
        if (info.hasBones) {
            builder.append('B');
        }
        if (info.hasMorphs) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append('M');
        }
        if (info.hasCamera) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append('C');
        }
        return builder.toString();
    }

    static String stripExtension(String text) {
        int dot = text.lastIndexOf('.');
        return dot > 0 ? text.substring(0, dot) : text;
    }

    static String shorten(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return maxChars <= 2 ? text.substring(0, Math.max(0, maxChars)) : text.substring(0, maxChars - 2) + "..";
    }

    static String wb(String suffix, Object... args) {
        return Component.translatable("gui.mmdskin.stage.workbench." + suffix, args).getString();
    }
}
