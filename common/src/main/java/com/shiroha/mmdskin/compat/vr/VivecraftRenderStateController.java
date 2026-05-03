package com.shiroha.mmdskin.compat.vr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

/** 文件职责：集中控制 Vivecraft 渲染状态覆盖与恢复。 */
final class VivecraftRenderStateController {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Field vrSettingsInstanceField;
    private final Field showPlayerHandsField;
    private final Field shouldRenderSelfField;
    private final Field modelArmsModeField;
    private final Object modelArmsModeOff;

    private boolean renderStateCaptured;
    private boolean renderStateApplied;
    private boolean originalShowPlayerHands;
    private boolean originalShouldRenderSelf;
    private Object originalModelArmsMode;

    VivecraftRenderStateController(Field vrSettingsInstanceField,
                                   Field showPlayerHandsField,
                                   Field shouldRenderSelfField,
                                   Field modelArmsModeField,
                                   Object modelArmsModeOff) {
        this.vrSettingsInstanceField = vrSettingsInstanceField;
        this.showPlayerHandsField = showPlayerHandsField;
        this.shouldRenderSelfField = shouldRenderSelfField;
        this.modelArmsModeField = modelArmsModeField;
        this.modelArmsModeOff = modelArmsModeOff;
    }

    void apply(boolean active) {
        try {
            Object settings = vrSettingsInstanceField.get(null);
            if (settings == null) {
                return;
            }

            if (active) {
                capture(settings);
                showPlayerHandsField.setBoolean(settings, false);
                shouldRenderSelfField.setBoolean(settings, true);
                modelArmsModeField.set(settings, modelArmsModeOff);
                if (!renderStateApplied) {
                    LOGGER.info("Applied Vivecraft hand visibility override for MMD VR mode");
                    renderStateApplied = true;
                }
                return;
            }

            if (!renderStateCaptured) {
                return;
            }
            restore(settings);
        } catch (Throwable t) {
            LOGGER.debug("Failed to update Vivecraft render settings", t);
        }
    }

    private void capture(Object settings) throws IllegalAccessException {
        if (renderStateCaptured) {
            return;
        }
        originalShowPlayerHands = showPlayerHandsField.getBoolean(settings);
        originalShouldRenderSelf = shouldRenderSelfField.getBoolean(settings);
        originalModelArmsMode = modelArmsModeField.get(settings);
        renderStateCaptured = true;
    }

    private void restore(Object settings) throws IllegalAccessException {
        showPlayerHandsField.setBoolean(settings, originalShowPlayerHands);
        shouldRenderSelfField.setBoolean(settings, originalShouldRenderSelf);
        modelArmsModeField.set(settings, originalModelArmsMode);
        renderStateCaptured = false;
        renderStateApplied = false;
        LOGGER.info("Restored Vivecraft hand visibility settings");
    }
}
