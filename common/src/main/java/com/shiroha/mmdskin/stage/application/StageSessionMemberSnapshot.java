package com.shiroha.mmdskin.stage.application;

import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;

import java.util.UUID;

public record StageSessionMemberSnapshot(UUID playerId, String playerName,
                                         StageMemberState state, StageCameraMode cameraMode) {
}
