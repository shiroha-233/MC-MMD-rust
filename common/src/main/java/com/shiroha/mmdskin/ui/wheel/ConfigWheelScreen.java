package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.selector.MaterialVisibilityScreen;
import com.shiroha.mmdskin.ui.selector.ModelSelectorScreen;
import com.shiroha.mmdskin.ui.selector.SceneSelectorScreen;
import com.shiroha.mmdskin.ui.selector.VoicePackBindingScreen;
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
                "model", this::openModelSelector));
        configSlots.add(new ConfigSlot("action",
                Component.translatable("gui.mmdskin.config.action_select").getString(),
                "action", this::openActionWheel));
        configSlots.add(new ConfigSlot("morph",
                Component.translatable("gui.mmdskin.config.morph_select").getString(),
                "morph", this::openMorphWheel));
        configSlots.add(new ConfigSlot("material",
                Component.translatable("gui.mmdskin.config.material_control").getString(),
                "mat", this::openMaterialVisibility));
        configSlots.add(new ConfigSlot("voice",
                Component.translatable("gui.mmdskin.config.voice_pack").getString(),
                "voice", this::openVoiceBindings));
        configSlots.add(new ConfigSlot("scene",
                Component.translatable("gui.mmdskin.config.scene_mode").getString(),
                "scene", this::openSceneSelector));
        configSlots.add(new ConfigSlot("stage",
                Component.translatable("gui.mmdskin.config.stage_mode").getString(),
                "stage", this::openStageSelect));
        configSlots.add(new ConfigSlot("settings",
                Component.translatable("gui.mmdskin.config.mod_settings").getString(),
                "cfg", this::openModSettings));
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
        renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, buildEntries());

        String centerText = selectedSlot >= 0 ? configSlots.get(selectedSlot).name : "MMD Skin";
        renderCenterBubble(guiGraphics, centerText, style.lineColor());

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (Minecraft.getInstance().screen != this) {
            return;
        }

        if (monitoredKey != null) {
            boolean isDown;
            if (monitoredKey.isDown()) {
                isDown = true;
            } else {
                long window = Minecraft.getInstance().getWindow().getWindow();
                InputConstants.Key key = KeyMappingUtil.getBoundKey(monitoredKey);
                isDown = key != null
                        && key.getType() == InputConstants.Type.KEYSYM
                        && key.getValue() != -1
                        && GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
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

    private List<WheelEntry> buildEntries() {
        List<WheelEntry> entries = new ArrayList<>(configSlots.size());
        for (ConfigSlot slot : configSlots) {
            entries.add(new WheelEntry(slot.name, null));
        }
        return entries;
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

    private void openVoiceBindings() {
        Minecraft.getInstance().setScreen(VoicePackBindingScreen.createForPlayer(
                this, ModelSelectorConfig.getInstance().getSelectedModel()));
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
        Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("message.mmdskin.mod_settings.not_initialized"));
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
