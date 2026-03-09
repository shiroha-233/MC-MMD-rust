package com.shiroha.mmdskin.ui.wheel;

import com.shiroha.mmdskin.ui.config.ActionWheelConfigScreen;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import com.shiroha.mmdskin.ui.wheel.service.ActionWheelService;
import com.shiroha.mmdskin.ui.wheel.service.DefaultActionWheelService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

        this.addRenderableWidget(Button.builder(Component.literal("⚙"), btn -> {
            this.minecraft.setScreen(new ActionWheelConfigScreen(this));
        }).bounds(this.width - 28, this.height - 28, 22, 22).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (actionSlots.isEmpty()) {
            renderEmptyHint(guiGraphics);
        } else {
            updateSelectedSlot(mouseX, mouseY);
            renderHighlight(guiGraphics);
            renderDividerLines(guiGraphics);
            renderOuterRing(guiGraphics);

            String centerText = selectedSlot >= 0
                ? Component.translatable("gui.mmdskin.action_wheel.click_select").getString()
                : Component.translatable("gui.mmdskin.select_action").getString();
            renderCenterCircle(guiGraphics, centerText, 0xFF60A0D0);
            renderActionLabels(guiGraphics);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderEmptyHint(GuiGraphics guiGraphics) {
        int boxWidth = 220;
        int boxHeight = 40;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC0182030);
        drawRectOutline(guiGraphics, boxX, boxY, boxWidth, boxHeight, style.lineColor());

        Component hint = Component.translatable("gui.mmdskin.action_wheel.no_actions");
        guiGraphics.drawCenteredString(this.font, hint, centerX, centerY - 4, 0xFFFFFF);
    }

    private void renderActionLabels(GuiGraphics guiGraphics) {
        double segmentAngle = 360.0 / actionSlots.size();
        int maxTextWidth = (int) (outerRadius * 0.6);

        for (int i = 0; i < actionSlots.size(); i++) {
            ActionSlot slot = actionSlots.get(i);
            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2 - 90);

            int textRadius = (innerRadius + outerRadius) / 2 + 5;
            int textX = centerX + (int) (Math.cos(angle) * textRadius);
            int textY = centerY + (int) (Math.sin(angle) * textRadius);

            String displayName = slot.name;
            if (this.font.width(displayName) > maxTextWidth) {
                while (this.font.width(displayName + "..") > maxTextWidth && displayName.length() > 3) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "..";
            }

            Component text = Component.literal(displayName);
            int textWidth = this.font.width(text);

            boolean isSelected = (i == selectedSlot);
            int textColor = isSelected ? 0xFFFFFFFF : 0xFFCCDDEE;

            guiGraphics.drawString(this.font, text, textX - textWidth / 2 + 1, textY - 3, style.textShadow(), false);
            guiGraphics.drawString(this.font, text, textX - textWidth / 2 - 1, textY - 5, style.textShadow(), false);
            guiGraphics.drawString(this.font, text, textX - textWidth / 2, textY - 4, textColor, false);
        }
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
