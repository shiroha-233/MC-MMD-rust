package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.ui.selector.MaterialVisibilityScreen;
import com.shiroha.mmdskin.ui.selector.ModelSelectorScreen;
import com.shiroha.mmdskin.ui.selector.SceneSelectorScreen;
import com.shiroha.mmdskin.ui.stage.StagePlaybackUiAdapter;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 主配置轮盘界面。 */
public class ConfigWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = new WheelStyle(
            0.50f, 0.30f,
            0xFF60A0D0, 0xCC60A0D0, 0x60FFFFFF,
            0xE0182030, 0xFF60A0D0, 0xFF000000
    );
    
    private final List<ConfigSlot> configSlots;
    

    private final KeyMapping monitoredKey;
    

    private static Supplier<Screen> modSettingsScreenFactory;
    
    public ConfigWheelScreen(KeyMapping keyMapping) {
        super(Component.translatable("gui.mmdskin.config_wheel"), STYLE);
        this.monitoredKey = keyMapping;
        this.configSlots = new ArrayList<>();
        initConfigSlots();
    }
    
    
    public static void setModSettingsScreenFactory(Supplier<Screen> factory) {
        modSettingsScreenFactory = factory;
    }
    
    private void initConfigSlots() {
        configSlots.add(new ConfigSlot("model", 
            Component.translatable("gui.mmdskin.config.model_switch").getString(),
            "🎭", this::openModelSelector));
        configSlots.add(new ConfigSlot("action", 
            Component.translatable("gui.mmdskin.config.action_select").getString(),
            "🎬", this::openActionWheel));
        configSlots.add(new ConfigSlot("morph", 
            Component.translatable("gui.mmdskin.config.morph_select").getString(),
            "😊", this::openMorphWheel));
        configSlots.add(new ConfigSlot("material", 
            Component.translatable("gui.mmdskin.config.material_control").getString(),
            "👕", this::openMaterialVisibility));
        configSlots.add(new ConfigSlot("scene", 
            Component.translatable("gui.mmdskin.config.scene_mode").getString(),
            "🏠", this::openSceneSelector));
        configSlots.add(new ConfigSlot("stage", 
            Component.translatable("gui.mmdskin.config.stage_mode").getString(),
            "🎥", this::openStageSelect));
        configSlots.add(new ConfigSlot("settings", 
            Component.translatable("gui.mmdskin.config.mod_settings").getString(),
            "⚙", this::openModSettings));
    }

    @Override
    protected int getSlotCount() {
        return configSlots.size();
    }

    @Override
    protected void init() {
        super.init();
        initWheelLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateSelectedSlot(mouseX, mouseY);
        renderHighlight(guiGraphics);
        renderDividerLines(guiGraphics);
        renderOuterRing(guiGraphics);
        
        String centerText = selectedSlot >= 0 ? configSlots.get(selectedSlot).name : "MMD Skin";
        renderCenterCircle(guiGraphics, centerText, 0xFF60A0D0);
        renderSlotLabels(guiGraphics);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void tick() {
        super.tick();


        if (Minecraft.getInstance().screen != this) {
            return;
        }


        if (monitoredKey != null) {
            boolean isDown = false;


            if (monitoredKey.isDown()) {
                isDown = true;
            } else {
                long window = Minecraft.getInstance().getWindow().getWindow();
                InputConstants.Key key = KeyMappingUtil.getBoundKey(monitoredKey);
                if (key != null && key.getType() == InputConstants.Type.KEYSYM && key.getValue() != -1) {
                    isDown = GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
                }
            }

            if (!isDown) {

                if (selectedSlot >= 0 && selectedSlot < configSlots.size()) {
                    ConfigSlot slot = configSlots.get(selectedSlot);
                    this.onClose();
                    slot.action.run();
                } else {
                    this.onClose();
                }
            }
        }
    }

    private void renderSlotLabels(GuiGraphics guiGraphics) {
        double segmentAngle = 360.0 / configSlots.size();
        
        for (int i = 0; i < configSlots.size(); i++) {
            ConfigSlot slot = configSlots.get(i);
            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2 - 90);
            
            int textRadius = (innerRadius + outerRadius) / 2;
            int textX = centerX + (int) (Math.cos(angle) * textRadius);
            int textY = centerY + (int) (Math.sin(angle) * textRadius);
            
            int iconWidth = this.font.width(slot.icon);
            boolean isSelected = (i == selectedSlot);
            int iconColor = isSelected ? 0xFFFFFFFF : 0xFFCCDDEE;
            
            guiGraphics.drawString(this.font, slot.icon, textX - iconWidth / 2 + 1, textY - 11, style.textShadow(), false);
            guiGraphics.drawString(this.font, slot.icon, textX - iconWidth / 2, textY - 12, iconColor, false);
            
            int nameWidth = this.font.width(slot.name);
            guiGraphics.drawString(this.font, slot.name, textX - nameWidth / 2 + 1, textY + 3, style.textShadow(), false);
            guiGraphics.drawString(this.font, slot.name, textX - nameWidth / 2, textY + 2, iconColor, false);
        }
    }
    

    private void openModelSelector() {
        Minecraft.getInstance().setScreen(new ModelSelectorScreen());
    }
    
    private void openActionWheel() {
        Minecraft.getInstance().setScreen(new ActionWheelScreen());
    }
    
    private void openMorphWheel() {
        Minecraft.getInstance().setScreen(new MorphWheelScreen(monitoredKey));
    }
    
    private void openMaterialVisibility() {
        MaterialVisibilityScreen screen = MaterialVisibilityScreen.createForPlayer();
        if (screen != null) {
            Minecraft.getInstance().setScreen(screen);
        } else {
            Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("message.mmdskin.player.model_not_found"));
        }
    }
    
    private void openSceneSelector() {
        Minecraft.getInstance().setScreen(new SceneSelectorScreen());
    }
    
    private void openStageSelect() {
        StagePlaybackUiAdapter.INSTANCE.openStageSelection();
    }
    
    private void openModSettings() {
        if (modSettingsScreenFactory != null) {
            Screen settingsScreen = modSettingsScreenFactory.get();
            if (settingsScreen != null) {
                Minecraft.getInstance().setScreen(settingsScreen);
                return;
            }
        }
        {
            Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("message.mmdskin.mod_settings.not_initialized"));
        }
    }

    private static class ConfigSlot {
        @SuppressWarnings("unused")
        final String id;
        final String name;
        final String icon;
        final Runnable action;

        ConfigSlot(String id, String name, String icon, Runnable action) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.action = action;
        }
    }
}
