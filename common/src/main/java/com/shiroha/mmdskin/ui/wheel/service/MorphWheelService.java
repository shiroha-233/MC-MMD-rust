package com.shiroha.mmdskin.ui.wheel.service;

import java.util.List;

public interface MorphWheelService {
    List<MorphOption> loadMorphs();

    void selectMorph(MorphOption option);
}
