package com.shiroha.mmdskin.stage.server.domain.model;

import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;

import java.util.List;
import java.util.UUID;

public final class StageServerSessionMember {
    private final UUID uuid;
    private final String name;
    private StageMemberState state;
    private StageCameraMode cameraMode;
    private String motionPackName;
    private List<String> motionFiles = List.of();

    public StageServerSessionMember(UUID uuid, String name, StageMemberState state, StageCameraMode cameraMode) {
        this.uuid = uuid;
        this.name = name;
        this.state = state;
        this.cameraMode = cameraMode;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public StageMemberState getState() {
        return state;
    }

    public void setState(StageMemberState state) {
        this.state = state;
    }

    public StageCameraMode getCameraMode() {
        return cameraMode;
    }

    public void setCameraMode(StageCameraMode cameraMode) {
        this.cameraMode = cameraMode;
    }

    public String getMotionPackName() {
        return motionPackName;
    }

    public void setMotionPackName(String motionPackName) {
        this.motionPackName = motionPackName;
    }

    public List<String> getMotionFiles() {
        return motionFiles;
    }

    public void setMotionFiles(List<String> motionFiles) {
        this.motionFiles = motionFiles;
    }
}
