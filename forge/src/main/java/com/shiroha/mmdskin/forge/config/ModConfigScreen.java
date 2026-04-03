package com.shiroha.mmdskin.forge.config;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.config.ConfigData;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.integration.entity.MobReplacementTargets;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Forge 模组设置界面。 */
public class ModConfigScreen {

    public static Screen create(Screen parent) {
        return createSettingsScreen(parent);
    }

    static Screen createSettingsScreen(Screen parent) {
        ConfigData data = MmdSkinConfig.getData();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("gui.mmdskin.mod_settings.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory renderCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.render"));

        renderCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.opengl_lighting"),
                data.openGLEnableLighting)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.opengl_lighting.tooltip"))
            .setSaveConsumer(value -> data.openGLEnableLighting = value)
            .build());

        renderCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.mmd_shader"),
                data.mmdShaderEnabled)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.mmd_shader.tooltip"))
            .setSaveConsumer(value -> data.mmdShaderEnabled = value)
            .build());

        renderCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.first_person_model"),
                data.firstPersonModelEnabled)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.first_person_model.tooltip"))
            .setSaveConsumer(value -> data.firstPersonModelEnabled = value)
            .build());

        renderCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.first_person_camera_forward_offset"),
                Math.round(data.firstPersonCameraForwardOffset * 1000.0F),
                -100, 500)
            .setDefaultValue(0)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.first_person_camera_forward_offset.tooltip"))
            .setTextGetter(value -> Component.literal(String.format("%.3f", value.intValue() / 1000.0F)))
            .setSaveConsumer(value -> data.firstPersonCameraForwardOffset = value.intValue() / 1000.0F)
            .build());

        renderCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.first_person_camera_vertical_offset"),
                Math.round(data.firstPersonCameraVerticalOffset * 1000.0F),
                -500, 500)
            .setDefaultValue(0)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.first_person_camera_vertical_offset.tooltip"))
            .setTextGetter(value -> Component.literal(String.format("%.3f", value.intValue() / 1000.0F)))
            .setSaveConsumer(value -> data.firstPersonCameraVerticalOffset = value.intValue() / 1000.0F)
            .build());

        ConfigCategory performanceCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.performance"));

        performanceCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.model_pool_max"),
                data.modelPoolMaxCount, 5, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.model_pool_max.tooltip"))
            .setSaveConsumer(value -> data.modelPoolMaxCount = value)
            .build());

        performanceCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.gpu_skinning"),
                data.gpuSkinningEnabled)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.gpu_skinning.tooltip"))
            .setSaveConsumer(value -> data.gpuSkinningEnabled = value)
            .build());

        performanceCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.gpu_morph"),
                data.gpuMorphEnabled)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.gpu_morph.tooltip"))
            .setSaveConsumer(value -> data.gpuMorphEnabled = value)
            .build());

        performanceCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.max_bones"),
                data.maxBones, 512, 4096)
            .setDefaultValue(2048)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.max_bones.tooltip"))
            .setSaveConsumer(value -> data.maxBones = value)
            .build());

        performanceCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.texture_cache_budget"),
                data.textureCacheBudgetMB, 64, 1024)
            .setDefaultValue(256)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.texture_cache_budget.tooltip"))
            .setTextGetter(value -> Component.literal(value + " MB"))
            .setSaveConsumer(value -> data.textureCacheBudgetMB = value)
            .build());

        ConfigCategory toonCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.toon"));

        toonCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.toon_enabled"),
                data.toonRenderingEnabled)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_enabled.tooltip"))
            .setSaveConsumer(value -> data.toonRenderingEnabled = value)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_levels"),
                data.toonLevels, 2, 5)
            .setDefaultValue(3)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_levels.tooltip"))
            .setSaveConsumer(value -> data.toonLevels = value)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_rim_power"),
                (int) (data.toonRimPower * 10), 10, 100)
            .setDefaultValue(30)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_rim_power.tooltip"))
            .setSaveConsumer(value -> data.toonRimPower = value / 10.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_rim_intensity"),
                (int) (data.toonRimIntensity * 100), 0, 100)
            .setDefaultValue(30)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_rim_intensity.tooltip"))
            .setSaveConsumer(value -> data.toonRimIntensity = value / 100.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_shadow_r"),
                (int) (data.toonShadowR * 100), 0, 100)
            .setDefaultValue(60)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_shadow.tooltip"))
            .setSaveConsumer(value -> data.toonShadowR = value / 100.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_shadow_g"),
                (int) (data.toonShadowG * 100), 0, 100)
            .setDefaultValue(50)
            .setSaveConsumer(value -> data.toonShadowG = value / 100.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_shadow_b"),
                (int) (data.toonShadowB * 100), 0, 100)
            .setDefaultValue(70)
            .setSaveConsumer(value -> data.toonShadowB = value / 100.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_specular_power"),
                (int) data.toonSpecularPower, 1, 128)
            .setDefaultValue(32)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_specular_power.tooltip"))
            .setSaveConsumer(value -> data.toonSpecularPower = value)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_specular_intensity"),
                (int) (data.toonSpecularIntensity * 100), 0, 100)
            .setDefaultValue(50)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_specular_intensity.tooltip"))
            .setSaveConsumer(value -> data.toonSpecularIntensity = value / 100.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline"),
                data.toonOutlineEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline.tooltip"))
            .setSaveConsumer(value -> data.toonOutlineEnabled = value)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline_width"),
                (int) (data.toonOutlineWidth * 1000), 1, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline_width.tooltip"))
            .setSaveConsumer(value -> data.toonOutlineWidth = value / 1000.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline_r"),
                (int) (data.toonOutlineR * 100), 0, 100)
            .setDefaultValue(10)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline_color.tooltip"))
            .setSaveConsumer(value -> data.toonOutlineR = value / 100.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline_g"),
                (int) (data.toonOutlineG * 100), 0, 100)
            .setDefaultValue(10)
            .setSaveConsumer(value -> data.toonOutlineG = value / 100.0f)
            .build());

        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline_b"),
                (int) (data.toonOutlineB * 100), 0, 100)
            .setDefaultValue(10)
            .setSaveConsumer(value -> data.toonOutlineB = value / 100.0f)
            .build());

        ConfigCategory physicsCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.physics"));

        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_enabled"),
                data.physicsEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_enabled.tooltip"))
            .setSaveConsumer(value -> data.physicsEnabled = value)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_gravity"),
                (int) (data.physicsGravityY * -1), 10, 200)
            .setDefaultValue(98)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_gravity.tooltip"))
            .setSaveConsumer(value -> data.physicsGravityY = value * -1.0f)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_fps"),
                (int) data.physicsFps, 30, 120)
            .setDefaultValue(60)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_fps.tooltip"))
            .setSaveConsumer(value -> data.physicsFps = value)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_substeps"),
                data.physicsMaxSubstepCount, 1, 10)
            .setDefaultValue(5)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_substeps.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxSubstepCount = value)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_inertia"),
                (int) (data.physicsInertiaStrength * 100), 0, 300)
            .setDefaultValue(50)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_inertia.tooltip"))
            .setSaveConsumer(value -> data.physicsInertiaStrength = value / 100.0f)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity"),
                (int) data.physicsMaxLinearVelocity, 0, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxLinearVelocity = value)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity"),
                (int) data.physicsMaxAngularVelocity, 0, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxAngularVelocity = value)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_joints_enabled"),
                data.physicsJointsEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_joints_enabled.tooltip"))
            .setSaveConsumer(value -> data.physicsJointsEnabled = value)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_kinematic_filter"),
                data.physicsKinematicFilter)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_kinematic_filter.tooltip"))
            .setSaveConsumer(value -> data.physicsKinematicFilter = value)
            .build());

        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_debug_log"),
                data.physicsDebugLog)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_debug_log.tooltip"))
            .setSaveConsumer(value -> data.physicsDebugLog = value)
            .build());

        ConfigCategory debugCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.debug"));

        debugCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.debug_hud"),
                data.debugHudEnabled)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.debug_hud.tooltip"))
            .setSaveConsumer(value -> data.debugHudEnabled = value)
            .build());

        ConfigCategory mobReplacementCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.mob_replacement"));

        mobReplacementCategory.addEntry(entryBuilder
            .startTextDescription(Component.translatable("gui.mmdskin.mod_settings.mob_replacement.description"))
            .build());

        for (MobReplacementTargets.Target target : MobReplacementTargets.all()) {
            String entityTypeId = target.entityTypeId().toString();
            mobReplacementCategory.addEntry(new MobReplacementListEntry(
                target,
                getMobReplacementValue(data, entityTypeId),
                value -> saveMobReplacementSelection(data, entityTypeId, value)
            ));
        }

        builder.setSavingRunnable(() -> saveConfig(data));

        return builder.build();
    }

    static void saveConfig(ConfigData data) {
        cleanupInvalidMobReplacements(data);
        MmdSkinConfig.save();

        com.shiroha.mmdskin.renderer.runtime.mode.RenderModeManager.setUseGpuSkinning(data.gpuSkinningEnabled);
        com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager.forceReloadAllModels();

        try {
            com.shiroha.mmdskin.NativeFunc.GetInst().SetPhysicsConfig(
                data.physicsEnabled,
                data.physicsGravityY,
                data.physicsFps,
                data.physicsMaxSubstepCount,
                data.physicsInertiaStrength,
                data.physicsMaxLinearVelocity,
                data.physicsMaxAngularVelocity,
                data.physicsJointsEnabled,
                data.physicsKinematicFilter,
                data.physicsDebugLog
            );
        } catch (UnsatisfiedLinkError e) {
            org.apache.logging.log4j.LogManager.getLogger().warn("物理配置 JNI 方法未找到，请重新编译 Rust 库");
        }
    }

    static String getMobReplacementValue(ConfigData data, String entityTypeId) {
        String currentValue = data.mobModelReplacements.getOrDefault(entityTypeId, UIConstants.DEFAULT_MODEL_NAME);
        if (currentValue == null || currentValue.isBlank()) {
            return UIConstants.DEFAULT_MODEL_NAME;
        }
        return currentValue;
    }

    static List<String> createModelSelections() {
        List<String> selections = new ArrayList<>();
        selections.add(UIConstants.DEFAULT_MODEL_NAME);
        for (ModelInfo modelInfo : ModelInfo.scanModels()) {
            String folderName = modelInfo.getFolderName();
            if (!folderName.isBlank() && !selections.contains(folderName)) {
                selections.add(folderName);
            }
        }
        return selections;
    }

    static Component toModelSelectionComponent(String modelName) {
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return Component.translatable("gui.mmdskin.mod_settings.mob_replacement.vanilla");
        }
        return Component.literal(modelName);
    }

    static void saveMobReplacementSelection(ConfigData data, String entityTypeId, String value) {
        if (value == null || value.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(value)) {
            data.mobModelReplacements.remove(entityTypeId);
            return;
        }
        data.mobModelReplacements.put(entityTypeId, value);
    }

    static void cleanupInvalidMobReplacements(ConfigData data) {
        Iterator<String> iterator = data.mobModelReplacements.values().iterator();
        while (iterator.hasNext()) {
            String modelName = iterator.next();
            if (modelName == null || modelName.isBlank() || ModelInfo.findByFolderName(modelName) == null) {
                iterator.remove();
            }
        }
    }
}
