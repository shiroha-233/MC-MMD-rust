package com.shiroha.mmdskin.model.port;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import java.util.List;

/** 文件职责：为调试与监控界面提供模型运行时诊断快照。 */
public interface ModelDiagnosticsPort {

    List<ManagedModel> loadedModels();

    int totalModelsLoaded();

    int pendingReleaseCount();
}
