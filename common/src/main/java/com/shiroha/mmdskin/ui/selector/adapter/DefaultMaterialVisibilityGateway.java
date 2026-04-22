package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
import com.shiroha.mmdskin.ui.selector.port.MaterialVisibilityGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：为材质可见性界面提供模型上下文与材质读写能力。 */
public class DefaultMaterialVisibilityGateway implements MaterialVisibilityGateway {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public Optional<MaterialScreenContext> createPlayerContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Optional.empty();
        }

        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        if (modelName == null || modelName.isEmpty()) {
            LOGGER.warn("Player has no selected model");
            return Optional.empty();
        }

        ManagedModel model = ClientRenderRuntime.get().modelRepository()
                .acquire(ModelRequestKey.player(minecraft.player, modelName));
        if (model == null) {
            LOGGER.warn("Cannot resolve player model {}", modelName);
            return Optional.empty();
        }

        return Optional.of(new MaterialScreenContext(model.modelInstance().getModelHandle(), modelName, modelName));
    }

    @Override
    public Optional<MaterialScreenContext> createMaidContext(UUID maidUuid, String maidName) {
        ManagedModel model = MaidMMDModelManager.getModel(maidUuid);
        if (model == null) {
            LOGGER.warn("Cannot resolve maid model {}", maidUuid);
            return Optional.empty();
        }

        String displayName = maidName != null
                ? maidName
                : Component.translatable("gui.mmdskin.maid.default_name").getString();
        return Optional.of(new MaterialScreenContext(
                model.modelInstance().getModelHandle(),
                displayName,
                model.modelInstance().getModelName()));
    }

    @Override
    public List<MaterialEntryState> loadMaterials(long modelHandle) {
        List<MaterialEntryState> materials = new ArrayList<>();
        var nativeBridge = NativeRuntimeBridgeHolder.get();
        int materialCount = nativeBridge.getMaterialCount(modelHandle);
        for (int i = 0; i < materialCount; i++) {
            materials.add(new MaterialEntryState(
                    i,
                    nativeBridge.getMaterialName(modelHandle, i),
                    nativeBridge.isMaterialVisible(modelHandle, i)));
        }
        return materials;
    }

    @Override
    public void setAllVisible(long modelHandle, boolean visible) {
        try {
            NativeRuntimeBridgeHolder.get().setAllMaterialsVisible(modelHandle, visible);
        } catch (Exception e) {
            LOGGER.warn("Failed to update material visibility", e);
        }
    }

    @Override
    public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
        try {
            NativeRuntimeBridgeHolder.get().setMaterialVisible(modelHandle, materialIndex, visible);
        } catch (Exception e) {
            LOGGER.warn("Failed to update material visibility", e);
        }
    }

    @Override
    public void saveHiddenMaterials(String configModelName, Set<Integer> hiddenMaterials) {
        try {
            ModelConfigData config = ModelConfigManager.getConfig(configModelName);
            config.hiddenMaterials = hiddenMaterials;
            ModelConfigManager.saveConfig(configModelName, config);
        } catch (Exception e) {
            LOGGER.warn("Failed to save hidden materials for {}", configModelName, e);
        }
    }
}
