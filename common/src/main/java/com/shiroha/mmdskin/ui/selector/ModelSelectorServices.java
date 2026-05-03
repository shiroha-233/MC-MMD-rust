package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.bridge.runtime.NativeRuntimePort;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultMaterialVisibilityGateway;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultModelSelectionGateway;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultModelSelectionRuntimeGateway;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultModelSettingsGateway;
import com.shiroha.mmdskin.ui.selector.adapter.DefaultModelSettingsRuntimeGateway;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import com.shiroha.mmdskin.ui.selector.application.ModelSettingsApplicationService;
import java.util.function.Supplier;

/** 文件职责：集中装配模型选择与设置界面的应用服务。 */
public final class ModelSelectorServices {
    private static final Supplier<NativeRuntimePort> NATIVE_RUNTIME = NativeRuntimeBridgeHolder::get;
    private static final ModelSelectionApplicationService MODEL_SELECTION = new ModelSelectionApplicationService(
            new DefaultModelSelectionGateway(),
            new DefaultModelSelectionRuntimeGateway()
    );
    private static final ModelSettingsApplicationService MODEL_SETTINGS = new ModelSettingsApplicationService(
            new DefaultModelSettingsGateway(),
            new DefaultModelSettingsRuntimeGateway(NATIVE_RUNTIME::get)
    );
    private static final MaterialVisibilityApplicationService MATERIAL_VISIBILITY = new MaterialVisibilityApplicationService(
            new DefaultMaterialVisibilityGateway(NATIVE_RUNTIME::get, NATIVE_RUNTIME::get)
    );

    private ModelSelectorServices() {
    }

    public static ModelSelectionApplicationService modelSelection() {
        return MODEL_SELECTION;
    }

    public static ModelSettingsApplicationService modelSettings() {
        return MODEL_SETTINGS;
    }

    public static MaterialVisibilityApplicationService materialVisibility() {
        return MATERIAL_VISIBILITY;
    }
}
