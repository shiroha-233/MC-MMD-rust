package com.shiroha.mmdskin.ui.wheel;

import com.shiroha.mmdskin.ui.config.ActionWheelConfigScreen;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import com.shiroha.mmdskin.ui.wheel.service.ActionWheelService;
import com.shiroha.mmdskin.ui.wheel.service.DefaultActionWheelService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 动作选择轮盘界面。 */
public class ActionWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = new WheelStyle(
            0.80f, 0.25f,
            0xFF60A0D0, 0xCC60A0D0, 0x60FFFFFF,
            0xE0182030, 0xFF60A0D0, 0xFF000000
    );

    private final ActionWheelService actionWheelService;
    private final List<ActionSlot> actionSlots;

    public ActionWheelScreen() {
        this(new DefaultActionWheelService());
    }

    ActionWheelScreen(ActionWheelService actionWheelService) {
        super(Component.translatable("gui.mmdskin.action_wheel"), STYLE);
        this.actionWheelService = actionWheelService;
        this.actionSlots = new ArrayList<>();
        initActionSlots();
    }

    private void initActionSlots() {
        List<ActionOption> actions = actionWheelService.loadActions();
        for (int i = 0; i < actions.size(); i++) {
            ActionOption action = actions.get(i);
            actionSlots.add(new ActionSlot(i, action.displayName(), action.animId()));
        }
    }

    @Override
    protected int getSlotCount() {
        return actionSlots.size();
    }

    @Override
    protected void init() {
        super.init();
        initWheelLayout();

        this.addRenderableWidget(createWheelIconButton(Component.literal("⚙"), btn -> {
            this.minecraft.setScreen(new ActionWheelConfigScreen(this));
        }));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (actionSlots.isEmpty()) {
            renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, List.of());
            renderEmptyState(guiGraphics, Component.translatable("gui.mmdskin.action_wheel.no_actions"));
            renderCenterBubble(guiGraphics, Component.translatable("gui.mmdskin.select_action").getString(), style.lineColor());
        } else {
            renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, buildEntries());

            String centerText = selectedSlot >= 0
                    ? Component.translatable("gui.mmdskin.action_wheel.click_select").getString()
                    : Component.translatable("gui.mmdskin.select_action").getString();
            renderCenterBubble(guiGraphics, centerText, style.lineColor());
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private List<WheelEntry> buildEntries() {
        List<WheelEntry> entries = new ArrayList<>(actionSlots.size());
        for (ActionSlot slot : actionSlots) {
            entries.add(new WheelEntry(slot.name, null));
        }
        return entries;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && selectedSlot >= 0 && selectedSlot < actionSlots.size()) {
            ActionSlot slot = actionSlots.get(selectedSlot);
            executeAction(slot);
            this.onClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void executeAction(ActionSlot slot) {
        actionWheelService.selectAction(slot.animId);
    }

    private static class ActionSlot {
        @SuppressWarnings("unused")
        final int index;
        final String name;
        final String animId;

        ActionSlot(int index, String name, String animId) {
            this.index = index;
            this.name = name;
            this.animId = animId;
        }
    }
}
