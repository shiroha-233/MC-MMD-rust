package com.shiroha.mmdskin.stage.client.playback;

import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;

import java.util.List;
import java.util.Objects;

/** 文件职责：把本地舞台播放偏好适配为会话层可用的 port。 */
public final class DefaultStagePlaybackPreferencesPort implements StagePlaybackPreferencesPort {
    private final StageLocalPlaybackPreferences delegate;

    public DefaultStagePlaybackPreferencesPort(StageLocalPlaybackPreferences delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean isCustomMotionEnabled() {
        return delegate.isCustomMotionEnabled();
    }

    @Override
    public List<String> getSelectedMotionFiles() {
        return delegate.getSelectedMotionFiles();
    }

    @Override
    public String getSelectedPackName() {
        return delegate.getSelectedPackName();
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}
