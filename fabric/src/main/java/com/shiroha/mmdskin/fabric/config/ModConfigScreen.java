package com.shiroha.mmdskin.fabric.config;

import com.shiroha.mmdskin.config.ConfigData;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fabric 模组设置界面
 * 使用 Cloth Config API 构建
 */
public class ModConfigScreen {
    
    /**
     * 创建模组设置界面
     */
    public static Screen create(Screen parent) {
        ConfigData data = MmdSkinConfig.getData();
        
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("gui.mmdskin.mod_settings.title"));
        
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        
        // 渲染设置分类
        ConfigCategory renderCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.render"));

        // 全局功能开关
        renderCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.mod_enabled"),
                data.modEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.mod_enabled.tooltip"))
            .setSaveConsumer(value -> data.modEnabled = value)
            .build());

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
        
        // 性能设置分类
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
        
        // Toon 渲染设置分类（3渲2）
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
        
        // 边缘光设置
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_rim_power"),
                (int)(data.toonRimPower * 10), 10, 100)
            .setDefaultValue(30)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_rim_power.tooltip"))
            .setSaveConsumer(value -> data.toonRimPower = value / 10.0f)
            .build());
        
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_rim_intensity"),
                (int)(data.toonRimIntensity * 100), 0, 100)
            .setDefaultValue(30)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_rim_intensity.tooltip"))
            .setSaveConsumer(value -> data.toonRimIntensity = value / 100.0f)
            .build());
        
        // 阴影色设置
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_shadow_r"),
                (int)(data.toonShadowR * 100), 0, 100)
            .setDefaultValue(60)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_shadow.tooltip"))
            .setSaveConsumer(value -> data.toonShadowR = value / 100.0f)
            .build());
        
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_shadow_g"),
                (int)(data.toonShadowG * 100), 0, 100)
            .setDefaultValue(50)
            .setSaveConsumer(value -> data.toonShadowG = value / 100.0f)
            .build());
        
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_shadow_b"),
                (int)(data.toonShadowB * 100), 0, 100)
            .setDefaultValue(70)
            .setSaveConsumer(value -> data.toonShadowB = value / 100.0f)
            .build());
        
        // 高光设置
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_specular_power"),
                (int)data.toonSpecularPower, 1, 128)
            .setDefaultValue(32)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_specular_power.tooltip"))
            .setSaveConsumer(value -> data.toonSpecularPower = value)
            .build());
        
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_specular_intensity"),
                (int)(data.toonSpecularIntensity * 100), 0, 100)
            .setDefaultValue(50)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_specular_intensity.tooltip"))
            .setSaveConsumer(value -> data.toonSpecularIntensity = value / 100.0f)
            .build());
        
        // 描边设置
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
                (int)(data.toonOutlineWidth * 1000), 1, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline_width.tooltip"))
            .setSaveConsumer(value -> data.toonOutlineWidth = value / 1000.0f)
            .build());
        
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline_r"),
                (int)(data.toonOutlineR * 100), 0, 100)
            .setDefaultValue(10)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.toon_outline_color.tooltip"))
            .setSaveConsumer(value -> data.toonOutlineR = value / 100.0f)
            .build());
        
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline_g"),
                (int)(data.toonOutlineG * 100), 0, 100)
            .setDefaultValue(10)
            .setSaveConsumer(value -> data.toonOutlineG = value / 100.0f)
            .build());
        
        toonCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.toon_outline_b"),
                (int)(data.toonOutlineB * 100), 0, 100)
            .setDefaultValue(10)
            .setSaveConsumer(value -> data.toonOutlineB = value / 100.0f)
            .build());
        
        // ==================== 物理引擎设置分类（Bullet3） ====================
        ConfigCategory physicsCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.physics"));
        
        // 启用物理模拟
        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_enabled"),
                data.physicsEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_enabled.tooltip"))
            .setSaveConsumer(value -> data.physicsEnabled = value)
            .build());
        
        // 重力（MMD 标准 -98.0，滑块显示正值）
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_gravity"),
                (int)(data.physicsGravityY * -1), 10, 200)
            .setDefaultValue(98)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_gravity.tooltip"))
            .setSaveConsumer(value -> data.physicsGravityY = value * -1.0f)
            .build());
        
        // 物理 FPS
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_fps"),
                (int)data.physicsFps, 30, 120)
            .setDefaultValue(60)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_fps.tooltip"))
            .setSaveConsumer(value -> data.physicsFps = value)
            .build());
        
        // 子步数
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_substeps"),
                data.physicsMaxSubstepCount, 1, 10)
            .setDefaultValue(5)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_substeps.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxSubstepCount = value)
            .build());
        
        // 惯性强度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_inertia"),
                (int)(data.physicsInertiaStrength * 100), 0, 300)
            .setDefaultValue(50)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_inertia.tooltip"))
            .setSaveConsumer(value -> data.physicsInertiaStrength = value / 100.0f)
            .build());
        
        // 最大线速度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity"),
                (int)data.physicsMaxLinearVelocity, 0, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxLinearVelocity = value)
            .build());
        
        // 最大角速度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity"),
                (int)data.physicsMaxAngularVelocity, 0, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxAngularVelocity = value)
            .build());
        
        // 启用关节
        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_joints_enabled"),
                data.physicsJointsEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_joints_enabled.tooltip"))
            .setSaveConsumer(value -> data.physicsJointsEnabled = value)
            .build());
        
        // 调试日志
        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_debug_log"),
                data.physicsDebugLog)
            .setDefaultValue(false)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_debug_log.tooltip"))
            .setSaveConsumer(value -> data.physicsDebugLog = value)
            .build());
        
        // ==================== 调试设置分类 ====================
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
        
        builder.setSavingRunnable(() -> {
            MmdSkinConfig.save();
            // 同步渲染模式设置到工厂
            com.shiroha.mmdskin.renderer.core.RenderModeManager.setUseGpuSkinning(data.gpuSkinningEnabled);
            // 重载所有模型以应用新的渲染模式（CPU/GPU 蒙皮热切换）
            com.shiroha.mmdskin.renderer.model.MMDModelManager.forceReloadAllModels();
            // 同步物理配置到 Rust 引擎
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
                    data.physicsDebugLog
                );
            } catch (UnsatisfiedLinkError e) {
                org.apache.logging.log4j.LogManager.getLogger().warn("物理配置 JNI 方法未找到，请重新编译 Rust 库");
            }
        });
        
        return builder.build();
    }
}
