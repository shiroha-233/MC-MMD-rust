package com.shiroha.mmdskin.ui.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.maid.MaidActionWheelScreen;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.maid.MaidModelSelectorScreen;
import com.shiroha.mmdskin.ui.selector.MaterialVisibilityScreen;
import com.shiroha.mmdskin.ui.selector.VoicePackBindingScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 女仆配置轮盘界面。 */
public class MaidConfigWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = new WheelStyle(
            0.45f, 0.35f,
            0xFFD060A0, 0xCCD060A0, 0x60FFFFFF,
            0xE0301828, 0xFFD060A0, 0xFF000000
    );

    private final List<ConfigSlot> configSlots;
    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;
    private final KeyMapping monitoredKey;

    public MaidConfigWheelScreen(UUID maidUUID, int maidEntityId, String maidName, KeyMapping keyMapping) {
        super(Component.translatable("gui.mmdskin.maid_config_wheel"), STYLE);
        this.maidUUID = maidUUID;
        this.maidEntityId = maidEntityId;
        this.maidName = maidName;
        this.monitoredKey = keyMapping;
        this.configSlots = new ArrayList<>();
        initConfigSlots();
    }

    private void initConfigSlots() {
        configSlots.add(new ConfigSlot("model",
                Component.translatable("gui.mmdskin.maid.model_switch").getString(),
                "model", this::openMaidModelSelector));
        configSlots.add(new ConfigSlot("action",
                Component.translatable("gui.mmdskin.maid.action_select").getString(),
                "action", this::openMaidActionWheel));
        configSlots.add(new ConfigSlot("material",
                Component.translatable("gui.mmdskin.maid.material_control").getString(),
                "mat", this::openMaidMaterialVisibility));
        configSlots.add(new ConfigSlot("voice",
                Component.translatable("gui.mmdskin.maid.voice_pack").getString(),
                "voice", this::openMaidVoiceBindings));
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

        String centerText = selectedSlot >= 0 ? configSlots.get(selectedSlot).name : maidName;
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

    private void openMaidModelSelector() {
        Minecraft.getInstance().setScreen(new MaidModelSelectorScreen(maidUUID, maidEntityId, maidName));
    }

    private void openMaidActionWheel() {
        Minecraft.getInstance().setScreen(new MaidActionWheelScreen(maidUUID, maidEntityId, maidName));
    }

    private void openMaidMaterialVisibility() {
        MaterialVisibilityScreen screen = MaterialVisibilityScreen.createForMaid(maidUUID, maidName);
        if (screen != null) {
            Minecraft.getInstance().setScreen(screen);
        } else {
            Minecraft.getInstance().gui.getChat().addMessage(
                    Component.translatable("message.mmdskin.maid.model_not_found"));
        }
    }

    private void openMaidVoiceBindings() {
        if (MaidMMDModelManager.hasMMDModel(maidUUID)) {
            Minecraft.getInstance().setScreen(VoicePackBindingScreen.createForMaid(this, maidUUID));
        }
    }

    @SuppressWarnings("unused")
    private static class ConfigSlot {
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
