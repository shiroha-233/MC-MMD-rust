package com.shiroha.mmdskin.ui.wheel;

import com.shiroha.mmdskin.ui.config.MorphWheelConfigScreen;
import com.shiroha.mmdskin.ui.wheel.service.DefaultMorphWheelService;
import com.shiroha.mmdskin.ui.wheel.service.MorphOption;
import com.shiroha.mmdskin.ui.wheel.service.MorphWheelService;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 表情选择轮盘界面。 */
public class MorphWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = new WheelStyle(
            0.80f, 0.25f,
            0xFF60A0D0, 0xCC60A0D0, 0x60FFFFFF,
            0xE0182030, 0xFF60A0D0, 0x80000000
    );

    private final MorphWheelService morphWheelService;
    private final KeyMapping triggerKey;
    private final List<MorphSlot> morphSlots = new ArrayList<>();

    private static class MorphSlot {
        String displayName;
        String morphName;
        String filePath;
        String syncToken;
        boolean resetAction;

        MorphSlot(String displayName, String morphName, String filePath, String syncToken, boolean resetAction) {
            this.displayName = displayName;
            this.morphName = morphName;
            this.filePath = filePath;
            this.syncToken = syncToken;
            this.resetAction = resetAction;
        }
    }

    public MorphWheelScreen(KeyMapping keyMapping) {
        this(keyMapping, new DefaultMorphWheelService());
    }

    MorphWheelScreen(KeyMapping keyMapping, MorphWheelService morphWheelService) {
        super(Component.translatable("gui.mmdskin.morph_wheel"), STYLE);
        this.triggerKey = keyMapping;
        this.morphWheelService = morphWheelService;
    }

    @Override
    protected int getSlotCount() {
        return morphSlots.size();
    }

    @Override
    protected void init() {
        super.init();
        initWheelLayout();
        initMorphSlots();

        this.addRenderableWidget(createWheelIconButton(Component.literal("⚙"), btn -> {
            this.minecraft.setScreen(new MorphWheelConfigScreen(this));
        }));
    }

    private void initMorphSlots() {
        morphSlots.clear();
        for (MorphOption option : morphWheelService.loadMorphs()) {
            morphSlots.add(new MorphSlot(option.displayName(), option.morphName(), option.filePath(), option.syncToken(), option.resetAction()));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!morphSlots.isEmpty()) {
            renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, buildEntries());
        } else {
            renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, List.of());
            renderEmptyState(guiGraphics, Component.translatable("gui.mmdskin.morph_wheel.no_morphs"));
        }

        String centerText = Component.translatable("gui.mmdskin.morph_wheel.select").getString();
        if (selectedSlot >= 0 && selectedSlot < morphSlots.size()) {
            centerText = morphSlots.get(selectedSlot).displayName;
        }
        renderCenterBubble(guiGraphics, centerText, style.lineColor());

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private List<WheelEntry> buildEntries() {
        List<WheelEntry> entries = new ArrayList<>(morphSlots.size());
        for (MorphSlot slot : morphSlots) {
            entries.add(new WheelEntry(slot.displayName, null));
        }
        return entries;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && selectedSlot >= 0 && selectedSlot < morphSlots.size()) {
            executeMorph(morphSlots.get(selectedSlot));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (triggerKey != null) {
            com.mojang.blaze3d.platform.InputConstants.Key boundKey = KeyMappingUtil.getBoundKey(triggerKey);
            if (boundKey != null
                    && boundKey.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                    && boundKey.getValue() == keyCode) {
                if (selectedSlot >= 0 && selectedSlot < morphSlots.size()) {
                    executeMorph(morphSlots.get(selectedSlot));
                }
                this.onClose();
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void executeMorph(MorphSlot slot) {
        morphWheelService.selectMorph(new MorphOption(slot.displayName, slot.morphName, slot.filePath, slot.syncToken, slot.resetAction));
    }
}
