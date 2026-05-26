/* 文件职责：定义材质显隐界面访问模型上下文、材质列表与配置持久化的端口。 */
package com.shiroha.mmdskin.ui.selector.port;

import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MaterialVisibilityGateway {
    Optional<MaterialScreenContext> createPlayerContext();

    Optional<MaterialScreenContext> createMaidContext(UUID maidUuid, String maidName);

    List<MaterialEntryState> loadMaterials(MaterialScreenContext context);

    void setAllVisible(long modelHandle, boolean visible);

    void setMaterialVisible(long modelHandle, int materialIndex, boolean visible);

    void saveHiddenMaterials(String configModelName, Set<Integer> hiddenMaterials);
}
