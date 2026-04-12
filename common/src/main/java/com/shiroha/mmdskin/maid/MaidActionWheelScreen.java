package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.maid.service.DefaultMaidActionService;
import com.shiroha.mmdskin.maid.service.MaidActionService;
import com.shiroha.mmdskin.ui.wheel.AbstractWheelScreen;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 女仆动作选择轮盘界面。 */
public class MaidActionWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = new WheelStyle(
            0.70f, 0.25f,
            0xFFD060A0, 0xCCD060A0, 0x60FFFFFF,
            0xE0301828, 0xFFD060A0, 0xFF000000
    );

    private final MaidActionService maidActionService;
    private final List<ActionSlot> actionSlots;
    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;

    public MaidActionWheelScreen(UUID maidUUID, int maidEntityId, String maidName) {
        this(maidUUID, maidEntityId, maidName, new DefaultMaidActionService());
    }

    MaidActionWheelScreen(UUID maidUUID, int maidEntityId, String maidName, MaidActionService maidActionService) {
        super(Component.translatable("gui.mmdskin.maid_action_wheel"), STYLE);
        this.maidUUID = maidUUID;
        this.maidEntityId = maidEntityId;
        this.maidName = maidName;
        this.maidActionService = maidActionService;
        this.actionSlots = new ArrayList<>();
        initActionSlots();
    }

    private void initActionSlots() {
        List<ActionOption> actions = maidActionService.loadActions();
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
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (actionSlots.isEmpty()) {
            renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, List.of());
            renderEmptyState(guiGraphics, Component.translatable("gui.mmdskin.maid_action_wheel.no_actions"));
            renderCenterBubble(guiGraphics, maidName, style.lineColor());
        } else {
            renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, buildEntries());

            String centerText = selectedSlot >= 0
                    ? Component.translatable("gui.mmdskin.action_wheel.click_select").getString()
                    : maidName;
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
        maidActionService.selectAction(maidUUID, maidEntityId, slot.animId);
    }

    @SuppressWarnings("unused")
    private static class ActionSlot {
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
