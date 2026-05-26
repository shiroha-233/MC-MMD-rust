package com.shiroha.mmdskin.maid.service;

import com.shiroha.mmdskin.ui.wheel.service.ActionOption;

import java.util.List;
import java.util.UUID;

public interface MaidActionService {
    List<ActionOption> loadActions();

    void selectAction(UUID maidUUID, int maidEntityId, String animId);
}
