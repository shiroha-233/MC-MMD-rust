package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.model.port.ModelRepositoryExtensionPort;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import java.util.Collection;
import java.util.List;

/** 文件职责：把女仆域的模型引用接入统一模型仓储扩展点。 */
public enum MaidModelRepositoryExtension implements ModelRepositoryExtensionPort {
    INSTANCE;

    @Override
    public Collection<ManagedModel> additionalLoadedModels() {
        return List.copyOf(MaidMMDModelManager.getLoadedMaidModels());
    }

    @Override
    public void onManagedModelDisposed(ManagedModel model) {
        MaidMMDModelManager.onModelDisposed(model);
    }

    @Override
    public void onRepositoryReloadAll() {
        MaidMMDModelManager.invalidateLoadedModels();
    }
}
