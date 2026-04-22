package com.shiroha.mmdskin.model.port;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;

/** 文件职责：向外暴露模型仓储的最小获取与重载能力。 */
public interface ModelRepositoryPort {

    ManagedModel acquire(ModelRequestKey requestKey);

    boolean isPending(ModelRequestKey requestKey);

    void reloadModel(String modelName);

    void reloadSubject(ModelRequestKey requestKey);

    void reloadAll();

    void tick();
}
