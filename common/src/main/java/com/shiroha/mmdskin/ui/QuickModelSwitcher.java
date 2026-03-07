package com.shiroha.mmdskin.ui;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 快捷模型切换器
 * 处理快捷键触发的模型切换逻辑，由平台层（Fabric/Forge）在按键事件中调用。
 */
public final class QuickModelSwitcher {
    private static final Logger logger = LogManager.getLogger();

    private QuickModelSwitcher() {}

    /**
     * 触发快捷模型切换
     * @param slot 槽位索引（0-3）
     */
    public static void switchToSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ModelSelectorConfig config = ModelSelectorConfig.getInstance();
        String modelName = config.getQuickSlotModel(slot);

        if (modelName == null || modelName.isEmpty()) {
            // 槽位未绑定，提示玩家
            mc.gui.getChat().addMessage(
                Component.translatable("message.mmdskin.quick_model.unbound", slot + 1));
            return;
        }

        String currentModel = config.getSelectedModel();
        if (modelName.equals(currentModel)) {
            // 已经是当前模型，切换回默认
            applyModel(UIConstants.DEFAULT_MODEL_NAME);
            mc.gui.getChat().addMessage(
                Component.translatable("message.mmdskin.quick_model.reset"));
            return;
        }

        applyModel(modelName);
        mc.gui.getChat().addMessage(
            Component.translatable("message.mmdskin.quick_model.switched", modelName));
    }

    /**
     * 应用模型选择（与 ModelSelectorScreen.selectModel 逻辑一致）
     */
    private static void applyModel(String modelName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String playerName = mc.player.getName().getString();

        // 保存配置（内部已广播联机同步）
        ModelSelectorConfig.getInstance().setSelectedModel(modelName);

        // 通知服务器
        ModelSelectorNetworkHandler.sendModelChangeToServer(modelName);

        // 强制重载模型缓存
        MMDModelManager.forceReloadPlayerModels(playerName);

        logger.info("快捷切换模型: {}", modelName);
    }
}
