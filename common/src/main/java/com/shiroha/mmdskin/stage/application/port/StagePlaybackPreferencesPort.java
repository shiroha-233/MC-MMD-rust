package com.shiroha.mmdskin.stage.application.port;

import java.util.List;

public interface StagePlaybackPreferencesPort {
    boolean isCustomMotionEnabled();

    List<String> getSelectedMotionFiles();

    String getSelectedPackName();

    void reset();
}
