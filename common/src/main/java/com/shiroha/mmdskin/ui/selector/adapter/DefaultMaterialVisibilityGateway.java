package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
import com.shiroha.mmdskin.ui.selector.port.MaterialVisibilityGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class DefaultMaterialVisibilityGateway implements MaterialVisibilityGateway {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public Optional<MaterialScreenContext> createPlayerContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Optional.empty();
        }

        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        String playerCacheKey = PlayerModelResolver.getCacheKey(minecraft.player);
        if (modelName == null || modelName.isEmpty()) {
            LOGGER.warn("玩家未选择模型");
            return Optional.empty();
        }

        MMDModelManager.Model model = MMDModelManager.GetModel(modelName, playerCacheKey);
        if (model == null) {
            LOGGER.warn("无法获取玩家模型: {}_{}", modelName, playerCacheKey);
            return Optional.empty();
        }

        return Optional.of(new MaterialScreenContext(model.model.getModelHandle(), modelName, modelName));
    }

    @Override
    public Optional<MaterialScreenContext> createMaidContext(UUID maidUuid, String maidName) {
        MMDModelManager.Model model = com.shiroha.mmdskin.maid.MaidMMDModelManager.getModel(maidUuid);
        if (model == null) {
            LOGGER.warn("无法获取女仆模型: {}", maidUuid);
            return Optional.empty();
        }

        String displayName = maidName != null
                ? maidName
                : Component.translatable("gui.mmdskin.maid.default_name").getString();
        return Optional.of(new MaterialScreenContext(model.model.getModelHandle(), displayName, model.getModelName()));
    }

    @Override
    public List<MaterialEntryState> loadMaterials(long modelHandle) {
        List<MaterialEntryState> materials = new ArrayList<>();
        NativeFunc nativeFunc = NativeFunc.GetInst();
        long materialCount = nativeFunc.GetMaterialCount(modelHandle);
        for (int i = 0; i < materialCount; i++) {
            String name = nativeFunc.GetMaterialName(modelHandle, i);
            boolean visible = nativeFunc.IsMaterialVisible(modelHandle, i);
            materials.add(new MaterialEntryState(i, name, visible));
        }
        return materials;
    }

    @Override
    public void setAllVisible(long modelHandle, boolean visible) {
        try {
            NativeFunc.GetInst().SetAllMaterialsVisible(modelHandle, visible);
        } catch (Exception e) {
            LOGGER.warn("材质操作失败，模型可能已被释放", e);
        }
    }

    @Override
    public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
        try {
            NativeFunc.GetInst().SetMaterialVisible(modelHandle, materialIndex, visible);
        } catch (Exception e) {
            LOGGER.warn("材质操作失败，模型可能已被释放", e);
        }
    }

    @Override
    public void saveHiddenMaterials(String configModelName, Set<Integer> hiddenMaterials) {
        try {
            ModelConfigData config = ModelConfigManager.getConfig(configModelName);
            config.hiddenMaterials = hiddenMaterials;
            ModelConfigManager.saveConfig(configModelName, config);
            LOGGER.debug("材质可见性已保存: {} (隐藏 {})", configModelName, hiddenMaterials.size());
        } catch (Exception e) {
            LOGGER.warn("保存材质可见性失败: {}", configModelName, e);
        }
    }
}
