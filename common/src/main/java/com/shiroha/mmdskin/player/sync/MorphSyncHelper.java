package com.shiroha.mmdskin.player.sync;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * 表情远程同步。
 */
public final class MorphSyncHelper {

    private static final Logger logger = LogManager.getLogger();

    private static final String RESET_TOKEN = "__reset__";

    private MorphSyncHelper() {
    }

    public static void applyRemoteMorph(Player player, String morphName) {
        if (player == null || morphName == null || morphName.isEmpty()) return;

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) return;

        long modelHandle = resolved.model().model.getModelHandle();
        NativeFunc nf = NativeFunc.GetInst();

        if (RESET_TOKEN.equals(morphName)) {
            nf.ResetAllMorphs(modelHandle);
        } else {
            applyMorphFromFile(nf, modelHandle, morphName, resolved.playerName(),
                    resolved.model().model.getModelName());
        }
    }

    private static void applyMorphFromFile(NativeFunc nf, long modelHandle,
                                            String morphName, String playerName, String modelName) {
        String vpdPath = findVpdFile(morphName, modelName);
        if (vpdPath != null) {
            int result = nf.ApplyVpdMorph(modelHandle, vpdPath);
            if (result < 0) {
                logger.warn("[表情同步] 远程玩家 {} 表情应用失败: {} ({})", playerName, morphName, result);
            }
        } else {
            logger.warn("[表情同步] 本地未找到表情文件: {}", morphName);
        }
    }

    private static String findVpdFile(String morphName, String modelName) {

        String path = PathConstants.getCustomMorphPath(morphName);
        if (new File(path).exists()) return path;

        path = PathConstants.getDefaultMorphPath(morphName);
        if (new File(path).exists()) return path;

        if (modelName != null && !modelName.isEmpty()) {
            path = PathConstants.getModelMorphPath(modelName, morphName);
            if (new File(path).exists()) return path;
        }

        return null;
    }
}
