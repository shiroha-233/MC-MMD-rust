/* 文件职责：把远端同步过来的表情选择应用到本地玩家模型实例。 */
package com.shiroha.mmdskin.player.sync;

import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.expression.ExpressionApplicationService;
import com.shiroha.mmdskin.expression.ExpressionSelection;
import com.shiroha.mmdskin.expression.ExpressionSelectionCodec;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import java.io.File;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MorphSyncHelper {
    private static final Logger LOGGER = LogManager.getLogger();

    private MorphSyncHelper() {
    }

    public static void applyRemoteMorph(Player player, String morphName) {
        if (player == null || morphName == null || morphName.isEmpty()) {
            return;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) {
            return;
        }

        ExpressionSelection selection = ExpressionSelectionCodec.decode(morphName);
        if (selection.type() == ExpressionSelection.Type.FILE) {
            String filePath = findVpdFile(selection.value(), resolved.model().getModelName());
            if (filePath == null) {
                LOGGER.warn("[MorphSync] Local VPD file not found: {}", selection.value());
                return;
            }
            selection = ExpressionSelection.file(filePath);
        }
        ExpressionApplicationService.apply(
                resolved.model().model.getModelHandle(),
                selection,
                resolved.playerName());
    }

    private static String findVpdFile(String morphName, String modelName) {
        String path = PathConstants.getCustomMorphPath(morphName);
        if (new File(path).exists()) {
            return path;
        }

        path = PathConstants.getDefaultMorphPath(morphName);
        if (new File(path).exists()) {
            return path;
        }

        if (modelName != null && !modelName.isEmpty()) {
            path = PathConstants.getModelMorphPath(modelName, morphName);
            if (new File(path).exists()) {
                return path;
            }
        }
        return null;
    }
}
