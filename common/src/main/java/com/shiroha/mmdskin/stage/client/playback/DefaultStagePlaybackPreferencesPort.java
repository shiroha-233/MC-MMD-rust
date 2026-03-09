package com.shiroha.mmdskin.stage.client.playback;

import com.shiroha.mmdskin.stage.application.port.StagePlaybackPreferencesPort;

import java.util.List;

public final class DefaultStagePlaybackPreferencesPort implements StagePlaybackPreferencesPort {
    public static final DefaultStagePlaybackPreferencesPort INSTANCE = new DefaultStagePlaybackPreferencesPort();

    private final StageLocalPlaybackPreferences delegate = StageLocalPlaybackPreferences.getInstance();

    private DefaultStagePlaybackPreferencesPort() {
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
