/** 文件职责：定义女仆模型选择界面的应用服务入口。 */
package com.shiroha.mmdskin.compat.maid.service;

import java.util.List;
import java.util.UUID;

public interface MaidModelSelectionService {
    List<String> loadAvailableModels();

    String getCurrentModel(UUID maidUUID);

    void selectModel(UUID maidUUID, int maidEntityId, String modelName);
}
