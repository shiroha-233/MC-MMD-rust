/* 文件职责：构建 NeoForge 平台的 Cloth Config 配置界面并把界面输入保存回运行时配置。 */
package com.shiroha.mmdskin.neoforge.config;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.config.ConfigData;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.runtime.mode.RenderModeManager;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ModConfigScreen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int COLOR_INFO = 0xFF8A8A8A;

    private ModConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigData data = MmdSkinConfig.getData();
        ConfigSnapshot snapshot = ConfigSnapshot.capture(data);

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("gui.mmdskin.mod_settings.title"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        buildRenderCategory(builder, entryBuilder, data);
        buildPerformanceCategory(builder, entryBuilder, data);
        buildToonCategory(builder, entryBuilder, data);
        buildPhysicsCategory(builder, entryBuilder, data);
        buildDebugCategory(builder, entryBuilder, data);
        buildVrCategory(builder, entryBuilder, data);
        buildMobReplacementCategory(builder, entryBuilder, data);

        builder.setSavingRunnable(() -> saveConfig(data, snapshot));
        return builder.build();
    }

    private static void buildRenderCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ConfigData data) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("gui.mmdskin.mod_settings.category.render"));

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.opengl_lighting"), data.openGLEnableLighting)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.opengl_lighting.tooltip"))
                .setSaveConsumer(value -> data.openGLEnableLighting = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.mmd_shader"), data.mmdShaderEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.mmd_shader.tooltip"))
                .setSaveConsumer(value -> data.mmdShaderEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.first_person_model"), data.firstPersonModelEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.first_person_model.tooltip"))
                .setSaveConsumer(value -> data.firstPersonModelEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(
                        Component.translatable("gui.mmdskin.mod_settings.first_person_camera_forward_offset"),
                        Math.round(data.firstPersonCameraForwardOffset * 1000.0F),
                        -100, 500)
                .setDefaultValue(0)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.first_person_camera_forward_offset.tooltip"))
                .setTextGetter(value -> Component.literal(String.format(Locale.ROOT, "%.3f", value.intValue() / 1000.0F)))
                .setSaveConsumer(value -> data.firstPersonCameraForwardOffset = value.intValue() / 1000.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(
                        Component.translatable("gui.mmdskin.mod_settings.first_person_camera_vertical_offset"),
                        Math.round(data.firstPersonCameraVerticalOffset * 1000.0F),
                        -500, 500)
                .setDefaultValue(0)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.first_person_camera_vertical_offset.tooltip"))
                .setTextGetter(value -> Component.literal(String.format(Locale.ROOT, "%.3f", value.intValue() / 1000.0F)))
                .setSaveConsumer(value -> data.firstPersonCameraVerticalOffset = value.intValue() / 1000.0F)
                .build());
    }

    private static void buildPerformanceCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ConfigData data) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("gui.mmdskin.mod_settings.category.performance"));

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.model_pool_max"), data.modelPoolMaxCount, 5, 100)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.model_pool_max.tooltip"))
                .setSaveConsumer(value -> data.modelPoolMaxCount = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.gpu_skinning"), data.gpuSkinningEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.gpu_skinning.tooltip"))
                .setSaveConsumer(value -> data.gpuSkinningEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.gpu_morph"), data.gpuMorphEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.gpu_morph.tooltip"))
                .setSaveConsumer(value -> data.gpuMorphEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.max_bones"), data.maxBones, 512, 4096)
                .setDefaultValue(2048)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.max_bones.tooltip"))
                .setSaveConsumer(value -> data.maxBones = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.texture_cache_budget"), data.textureCacheBudgetMB, 64, 1024)
                .setDefaultValue(256)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.texture_cache_budget.tooltip"))
                .setTextGetter(value -> Component.literal(value + " MB"))
                .setSaveConsumer(value -> data.textureCacheBudgetMB = value)
                .build());

        List<AbstractConfigListEntry> runtimeEntries = new ArrayList<>();
        runtimeEntries.add(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.performance_profiling"), data.performanceProfilingEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.performance_profiling.tooltip"))
                .setSaveConsumer(value -> data.performanceProfilingEnabled = value)
                .build());
        runtimeEntries.add(entryBuilder
                .startIntField(Component.translatable("gui.mmdskin.mod_settings.performance_log_interval"), data.performanceLogIntervalSeconds)
                .setDefaultValue(5)
                .setMin(1)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.performance_log_interval.tooltip"))
                .setSaveConsumer(value -> data.performanceLogIntervalSeconds = value)
                .build());
        runtimeEntries.add(entryBuilder
                .startIntField(Component.translatable("gui.mmdskin.mod_settings.max_visible_models"), data.maxVisibleModelsPerFrame)
                .setDefaultValue(10)
                .setMin(1)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.max_visible_models.tooltip"))
                .setSaveConsumer(value -> data.maxVisibleModelsPerFrame = value)
                .build());
        runtimeEntries.add(entryBuilder
                .startFloatField(Component.translatable("gui.mmdskin.mod_settings.animation_lod_medium_distance"), data.animationLodMediumDistance)
                .setDefaultValue(24.0F)
                .setMin(0.0F)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.animation_lod_medium_distance.tooltip"))
                .setSaveConsumer(value -> data.animationLodMediumDistance = value)
                .build());
        runtimeEntries.add(entryBuilder
                .startFloatField(Component.translatable("gui.mmdskin.mod_settings.animation_lod_far_distance"), data.animationLodFarDistance)
                .setDefaultValue(48.0F)
                .setMin(0.0F)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.animation_lod_far_distance.tooltip"))
                .setSaveConsumer(value -> data.animationLodFarDistance = value)
                .build());
        runtimeEntries.add(entryBuilder
                .startIntField(Component.translatable("gui.mmdskin.mod_settings.animation_lod_medium_interval"), data.animationLodMediumUpdateInterval)
                .setDefaultValue(2)
                .setMin(1)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.animation_lod_medium_interval.tooltip"))
                .setSaveConsumer(value -> data.animationLodMediumUpdateInterval = value)
                .build());
        runtimeEntries.add(entryBuilder
                .startIntField(Component.translatable("gui.mmdskin.mod_settings.animation_lod_far_interval"), data.animationLodFarUpdateInterval)
                .setDefaultValue(4)
                .setMin(1)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.animation_lod_far_interval.tooltip"))
                .setSaveConsumer(value -> data.animationLodFarUpdateInterval = value)
                .build());
        runtimeEntries.add(entryBuilder
                .startTextDescription(Component.translatable("gui.mmdskin.mod_settings.performance_runtime_note"))
                .setColor(COLOR_INFO)
                .build());

        category.addEntry(entryBuilder
                .startSubCategory(Component.translatable("gui.mmdskin.mod_settings.performance_runtime_group"), runtimeEntries)
                .setExpanded(true)
                .build());
    }

    private static void buildToonCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ConfigData data) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("gui.mmdskin.mod_settings.category.toon"));

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.toon_enabled"), data.toonRenderingEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_enabled.tooltip"))
                .setSaveConsumer(value -> data.toonRenderingEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_levels"), data.toonLevels, 2, 5)
                .setDefaultValue(4)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_levels.tooltip"))
                .setSaveConsumer(value -> data.toonLevels = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_rim_power"), Math.round(data.toonRimPower * 10.0F), 1, 100)
                .setDefaultValue(56)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_rim_power.tooltip"))
                .setTextGetter(value -> Component.literal(String.format(Locale.ROOT, "%.1f", value.intValue() / 10.0F)))
                .setSaveConsumer(value -> data.toonRimPower = value.intValue() / 10.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_rim_intensity"), Math.round(data.toonRimIntensity * 100.0F), 0, 100)
                .setDefaultValue(2)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_rim_intensity.tooltip"))
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonRimIntensity = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_shadow_r"), Math.round(data.toonShadowR * 100.0F), 0, 100)
                .setDefaultValue(78)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_shadow.tooltip"))
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonShadowR = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_shadow_g"), Math.round(data.toonShadowG * 100.0F), 0, 100)
                .setDefaultValue(84)
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonShadowG = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_shadow_b"), Math.round(data.toonShadowB * 100.0F), 0, 100)
                .setDefaultValue(94)
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonShadowB = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_specular_power"), Math.round(data.toonSpecularPower), 1, 128)
                .setDefaultValue(96)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_specular_power.tooltip"))
                .setSaveConsumer(value -> data.toonSpecularPower = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_specular_intensity"), Math.round(data.toonSpecularIntensity * 100.0F), 0, 100)
                .setDefaultValue(2)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_specular_intensity.tooltip"))
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonSpecularIntensity = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.toon_outline"), data.toonOutlineEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline.tooltip"))
                .setSaveConsumer(value -> data.toonOutlineEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_outline_width"), Math.round(data.toonOutlineWidth * 1000.0F), 1, 100)
                .setDefaultValue(2)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline_width.tooltip"))
                .setTextGetter(value -> Component.literal(String.format(Locale.ROOT, "%.3f", value.intValue() / 1000.0F)))
                .setSaveConsumer(value -> data.toonOutlineWidth = value.intValue() / 1000.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_outline_r"), Math.round(data.toonOutlineR * 100.0F), 0, 100)
                .setDefaultValue(6)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline_color.tooltip"))
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonOutlineR = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_outline_g"), Math.round(data.toonOutlineG * 100.0F), 0, 100)
                .setDefaultValue(8)
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonOutlineG = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.toon_outline_b"), Math.round(data.toonOutlineB * 100.0F), 0, 100)
                .setDefaultValue(12)
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.toonOutlineB = value.intValue() / 100.0F)
                .build());
    }

    private static void buildPhysicsCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ConfigData data) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("gui.mmdskin.mod_settings.category.physics"));

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.physics_enabled"), data.physicsEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_enabled.tooltip"))
                .setSaveConsumer(value -> data.physicsEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.physics_gravity"), Math.round(data.physicsGravityY * -1.0F), 10, 200)
                .setDefaultValue(98)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_gravity.tooltip"))
                .setSaveConsumer(value -> data.physicsGravityY = value.intValue() * -1.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.physics_fps"), Math.round(data.physicsFps), 30, 120)
                .setDefaultValue(60)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_fps.tooltip"))
                .setSaveConsumer(value -> data.physicsFps = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.physics_substeps"), data.physicsMaxSubstepCount, 1, 10)
                .setDefaultValue(5)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_substeps.tooltip"))
                .setSaveConsumer(value -> data.physicsMaxSubstepCount = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.physics_inertia"), Math.round(data.physicsInertiaStrength * 100.0F), 0, 300)
                .setDefaultValue(50)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_inertia.tooltip"))
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.physicsInertiaStrength = value.intValue() / 100.0F)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity"), Math.round(data.physicsMaxLinearVelocity), 0, 100)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity.tooltip"))
                .setSaveConsumer(value -> data.physicsMaxLinearVelocity = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity"), Math.round(data.physicsMaxAngularVelocity), 0, 100)
                .setDefaultValue(20)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity.tooltip"))
                .setSaveConsumer(value -> data.physicsMaxAngularVelocity = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.physics_joints_enabled"), data.physicsJointsEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_joints_enabled.tooltip"))
                .setSaveConsumer(value -> data.physicsJointsEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.physics_kinematic_filter"), data.physicsKinematicFilter)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_kinematic_filter.tooltip"))
                .setSaveConsumer(value -> data.physicsKinematicFilter = value)
                .build());

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.physics_debug_log"), data.physicsDebugLog)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_debug_log.tooltip"))
                .setSaveConsumer(value -> data.physicsDebugLog = value)
                .build());

        List<AbstractConfigListEntry> lodEntries = new ArrayList<>();
        lodEntries.add(entryBuilder
                .startIntField(Component.translatable("gui.mmdskin.mod_settings.max_physics_models"), data.maxPhysicsModelsPerFrame)
                .setDefaultValue(10)
                .setMin(1)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.max_physics_models.tooltip"))
                .setSaveConsumer(value -> data.maxPhysicsModelsPerFrame = value)
                .build());
        lodEntries.add(entryBuilder
                .startFloatField(Component.translatable("gui.mmdskin.mod_settings.physics_lod_distance"), data.physicsLodMaxDistance)
                .setDefaultValue(24.0F)
                .setMin(0.0F)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_lod_distance.tooltip"))
                .setSaveConsumer(value -> data.physicsLodMaxDistance = value)
                .build());

        category.addEntry(entryBuilder
                .startSubCategory(Component.translatable("gui.mmdskin.mod_settings.physics_budget_group"), lodEntries)
                .setExpanded(true)
                .build());
    }

    private static void buildDebugCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ConfigData data) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("gui.mmdskin.mod_settings.category.debug"));

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.debug_hud"), data.debugHudEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.debug_hud.tooltip"))
                .setSaveConsumer(value -> data.debugHudEnabled = value)
                .build());
    }

    private static void buildVrCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ConfigData data) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("gui.mmdskin.mod_settings.category.vr"));

        category.addEntry(entryBuilder
                .startBooleanToggle(Component.translatable("gui.mmdskin.mod_settings.vr_enabled"), data.vrEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.vr_enabled.tooltip"))
                .setSaveConsumer(value -> data.vrEnabled = value)
                .build());

        category.addEntry(entryBuilder
                .startIntSlider(Component.translatable("gui.mmdskin.mod_settings.vr_arm_ik_strength"), Math.round(data.vrArmIKStrength * 100.0F), 0, 100)
                .setDefaultValue(100)
                .setTooltip(Component.translatable("gui.mmdskin.mod_settings.vr_arm_ik_strength.tooltip"))
                .setTextGetter(value -> Component.literal(value + "%"))
                .setSaveConsumer(value -> data.vrArmIKStrength = value.intValue() / 100.0F)
                .build());
    }

    private static void buildMobReplacementCategory(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, ConfigData data) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("gui.mmdskin.mod_settings.category.mob_replacement"));
        category.addEntry(entryBuilder
                .startTextDescription(Component.translatable("gui.mmdskin.mod_settings.mob_replacement.description"))
                .build());

        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            if (!isSupportedMobReplacement(entityType)) {
                continue;
            }
            ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (entityTypeId == null) {
                continue;
            }
            category.addEntry(new MobReplacementListEntry(
                    entityType,
                    entityType.getDescription(),
                    getMobReplacementValue(data, entityTypeId.toString()),
                    value -> saveMobReplacementSelection(data, entityTypeId.toString(), value)
            ));
        }
    }

    static void saveConfig(ConfigData data, ConfigSnapshot snapshot) {
        cleanupInvalidMobReplacements(data);
        MmdSkinConfig.save();

        RenderModeManager.setUseGpuSkinning(data.gpuSkinningEnabled);

        if (snapshot.requiresModelReload(data)) {
            MMDModelManager.forceReloadAllModels();
        }

        applyPhysicsConfig(data);
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

    private static boolean isSupportedMobReplacement(EntityType<?> entityType) {
        if (entityType == EntityType.PLAYER) {
            return false;
        }
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return entityTypeId != null
                && "minecraft".equals(entityTypeId.getNamespace())
                && entityType.getCategory() != MobCategory.MISC;
    }

    private static void applyPhysicsConfig(ConfigData data) {
        try {
            NativeFunc.GetInst().SetPhysicsConfig(
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
        } catch (UnsatisfiedLinkError error) {
            LOGGER.warn("Physics JNI is unavailable; skip runtime physics config sync.", error);
        }
    }

    private record ConfigSnapshot(
            boolean gpuSkinningEnabled,
            boolean gpuMorphEnabled,
            boolean mmdShaderEnabled,
            int maxBones,
            int textureCacheBudgetMB
    ) {
        static ConfigSnapshot capture(ConfigData data) {
            return new ConfigSnapshot(
                    data.gpuSkinningEnabled,
                    data.gpuMorphEnabled,
                    data.mmdShaderEnabled,
                    data.maxBones,
                    data.textureCacheBudgetMB
            );
        }

        boolean requiresModelReload(ConfigData data) {
            return gpuSkinningEnabled != data.gpuSkinningEnabled
                    || gpuMorphEnabled != data.gpuMorphEnabled
                    || mmdShaderEnabled != data.mmdShaderEnabled
                    || maxBones != data.maxBones
                    || textureCacheBudgetMB != data.textureCacheBudgetMB;
        }
    }
}
