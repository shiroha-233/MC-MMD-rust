package com.shiroha.mmdskin.ui.wheel;

import com.shiroha.mmdskin.ui.config.MorphWheelConfigScreen;
import com.shiroha.mmdskin.ui.wheel.service.DefaultMorphWheelService;
import com.shiroha.mmdskin.ui.wheel.service.MorphOption;
import com.shiroha.mmdskin.ui.wheel.service.MorphWheelService;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 表情选择轮盘界面
 * 与动作轮盘保持一致的UI风格
 */
public class MorphWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = new WheelStyle(
            0.80f, 0.25f,
            0xFF60A0D0, 0xCC60A0D0, 0x60FFFFFF,
            0xE0182030, 0xFF60A0D0, 0x80000000
    );
    
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final MorphWheelService morphWheelService;
    private final KeyMapping triggerKey;

    private List<MorphSlot> morphSlots = new ArrayList<>();

    private static class MorphSlot {
        String displayName;
        String morphName;
        String filePath;
        boolean resetAction;

        MorphSlot(String displayName, String morphName, String filePath, boolean resetAction) {
            this.displayName = displayName;
            this.morphName = morphName;
            this.filePath = filePath;
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
        
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙"), btn -> {
                this.minecraft.setScreen(new MorphWheelConfigScreen(this));
            }).bounds(this.width - 28, this.height - 28, 22, 22).build());
    }
    
    private void initMorphSlots() {
        morphSlots.clear();
        for (MorphOption option : morphWheelService.loadMorphs()) {
            morphSlots.add(new MorphSlot(option.displayName(), option.morphName(), option.filePath(), option.resetAction()));
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        guiGraphics.fill(0, 0, width, height, 0x80000000);
        
        updateSelectedSlot(mouseX, mouseY);
        
        if (!morphSlots.isEmpty()) {
            renderHighlight(guiGraphics);
            renderDividerLines(guiGraphics);
            renderOuterRing(guiGraphics);
            renderMorphLabels(guiGraphics);
        } else {
            String hint = Component.translatable("gui.mmdskin.morph_wheel.no_morphs").getString();
            int hintWidth = font.width(hint);
            guiGraphics.drawString(font, hint, centerX - hintWidth / 2, centerY - 4, TEXT_COLOR);
        }
        

        String centerText = Component.translatable("gui.mmdskin.morph_wheel.select").getString();
        if (selectedSlot >= 0 && selectedSlot < morphSlots.size()) {
            centerText = morphSlots.get(selectedSlot).displayName;
        }
        renderCenterCircle(guiGraphics, centerText, 0xFF60A0D0);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderMorphLabels(GuiGraphics guiGraphics) {
        int segments = morphSlots.size();
        double segmentAngle = 360.0 / segments;
        
        for (int i = 0; i < segments; i++) {
            MorphSlot slot = morphSlots.get(i);
            double midAngle = Math.toRadians(-90 + (i + 0.5) * segmentAngle);
            int labelRadius = (innerRadius + outerRadius) / 2;
            
            int labelX = centerX + (int) (Math.cos(midAngle) * labelRadius);
            int labelY = centerY + (int) (Math.sin(midAngle) * labelRadius);
            
            String label = slot.displayName;
            if (label.length() > 8) {
                label = label.substring(0, 7) + "..";
            }
            
            int textWidth = font.width(label);
            int textColor = (i == selectedSlot) ? 0xFFFFFF00 : TEXT_COLOR;
            
            guiGraphics.drawString(font, label, labelX - textWidth / 2 + 1, labelY - 4 + 1, style.textShadow(), false);
            guiGraphics.drawString(font, label, labelX - textWidth / 2, labelY - 4, textColor, false);
        }
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
            if (boundKey != null && boundKey.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && boundKey.getValue() == keyCode) {

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
        morphWheelService.selectMorph(new MorphOption(slot.displayName, slot.morphName, slot.filePath, slot.resetAction));
    }
}
