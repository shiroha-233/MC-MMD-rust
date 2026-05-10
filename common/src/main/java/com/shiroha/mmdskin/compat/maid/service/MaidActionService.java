/** 文件职责：定义女仆动作选择界面的应用服务入口。 */
package com.shiroha.mmdskin.compat.maid.service;

import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import java.util.List;
import java.util.UUID;

public interface MaidActionService {
    List<ActionOption> loadActions();

    void selectAction(UUID maidUUID, int maidEntityId, String animId);
}
