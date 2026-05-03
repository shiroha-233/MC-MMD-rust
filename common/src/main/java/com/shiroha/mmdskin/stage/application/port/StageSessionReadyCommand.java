package com.shiroha.mmdskin.stage.application.port;

import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;

import java.util.List;
import java.util.UUID;

/** 舞台会话就绪同步出站命令。 */
public record StageSessionReadyCommand(UUID hostUUID, UUID sessionId, boolean ready,
                                       StageCameraMode cameraMode, String motionPackName,
                                       List<String> motionFiles) {
    public StageSessionReadyCommand {
        cameraMode = cameraMode != null ? cameraMode : StageCameraMode.HOST_CAMERA;
        motionPackName = sanitizeName(motionPackName);
        motionFiles = motionFiles == null ? List.of() : motionFiles.stream()
                .map(StageSessionReadyCommand::sanitizeName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .toList();
    }

    public boolean useHostCamera() {
        return cameraMode.usesHostCamera();
    }

    private static String sanitizeName(String value) {
        return value != null && !value.isEmpty()
                && !value.contains("..")
                && !value.contains("/")
                && !value.contains("\\")
                ? value
                : null;
    }
}
