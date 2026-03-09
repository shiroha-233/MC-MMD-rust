package com.shiroha.mmdskin.ui;

import com.shiroha.mmdskin.ui.selector.ModelSelectorServices;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 快捷模型切换器
 * 处理快捷键触发的模型切换逻辑，由平台层（Fabric/Forge）在按键事件中调用。
 */
public final class QuickModelSwitcher {
    private static final ModelSelectionApplicationService SERVICE = ModelSelectorServices.modelSelection();

    private QuickModelSwitcher() {}

    public static void switchToSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ModelSelectionApplicationService.QuickSwitchResult result = SERVICE.switchToSlot(slot);
        switch (result.status()) {
            case UNBOUND -> mc.gui.getChat().addMessage(
                    Component.translatable("message.mmdskin.quick_model.unbound", slot + 1));
            case RESET_TO_DEFAULT -> mc.gui.getChat().addMessage(
                    Component.translatable("message.mmdskin.quick_model.reset"));
            case SWITCHED -> mc.gui.getChat().addMessage(
                    Component.translatable("message.mmdskin.quick_model.switched", result.targetModelName()));
        }
    }
}
