package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.ui.selector.MaterialVisibilityScreen;
import com.shiroha.mmdskin.ui.selector.ModelSelectorScreen;
import com.shiroha.mmdskin.ui.selector.SceneSelectorScreen;
import com.shiroha.mmdskin.ui.stage.StageSelectScreen;
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

/**
 * 主配置轮盘界面
 * 按住 Alt 打开，松开关闭
 * 提供模型切换/动作选择/材质控制/模组设置六个入口
 */
public class ConfigWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = new WheelStyle(
            0.50f, 0.30f,
            0xFF60A0D0, 0xCC60A0D0, 0x60FFFFFF,
            0xE0182030, 0xFF60A0D0, 0xFF000000
    );
    
    private final List<ConfigSlot> configSlots;
    
    // 监控的按键（用于检测松开）
    private final KeyMapping monitoredKey;
    
    // 模组设置界面打开回调（由平台实现）
    private static Supplier<Screen> modSettingsScreenFactory;
    
    public ConfigWheelScreen(KeyMapping keyMapping) {
        super(Component.translatable("gui.mmdskin.config_wheel"), STYLE);
        this.monitoredKey = keyMapping;
        this.configSlots = new ArrayList<>();
        initConfigSlots();
    }
    
    /**
     * 设置模组设置界面工厂（由 Fabric/Forge 平台调用）
     */
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

        // 只有在当前界面确实是 ConfigWheelScreen 时才检测按键
        // 避免在打开子界面（如 ModelSelectorScreen）时因 monitoredKey.isDown() 为 false 而误关闭
        if (Minecraft.getInstance().screen != this) {
            return;
        }

        // 检测按键是否松开
        if (monitoredKey != null) {
            boolean isDown = false;

            // 兜底逻辑：如果 KeyMapping.isDown() 为 false，尝试通过直接检测物理按键状态
            // 这解决了从游戏切换到 Screen 时，Minecraft 内部 KeyMapping 状态更新延迟导致的闪烁问题
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
                // 按键松开，执行选中的操作并关闭
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
    
    // 配置入口操作
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
        Minecraft.getInstance().setScreen(new StageSelectScreen());
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
        @SuppressWarnings("unused") // 预留用于配置持久化
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
