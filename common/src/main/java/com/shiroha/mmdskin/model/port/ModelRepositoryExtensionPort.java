package com.shiroha.mmdskin.model.port;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import java.util.Collection;
import java.util.List;

/** 文件职责：为模型仓储提供域外模型引用与生命周期扩展点。 */
public interface ModelRepositoryExtensionPort {

    default Collection<ManagedModel> additionalLoadedModels() {
        return List.of();
    }

    default void onManagedModelDisposed(ManagedModel model) {
    }

    default void onRepositoryReloadAll() {
    }
}
