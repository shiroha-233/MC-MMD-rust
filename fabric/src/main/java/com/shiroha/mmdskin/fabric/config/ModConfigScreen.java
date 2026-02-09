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
        
        // 性能设置分类
        ConfigCategory performanceCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.performance"));
        
        performanceCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.model_pool_max"),
                data.modelPoolMaxCount, 10, 500)
            .setDefaultValue(100)
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
        
        // ==================== 物理引擎设置分类 ====================
        ConfigCategory physicsCategory = builder.getOrCreateCategory(
            Component.translatable("gui.mmdskin.mod_settings.category.physics"));
        
        // 重力
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_gravity"),
                (int)(data.physicsGravityY * -10), 1, 200)
            .setDefaultValue(38)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_gravity.tooltip"))
            .setSaveConsumer(value -> data.physicsGravityY = value / -10.0f)
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
            .setDefaultValue(4)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_substeps.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxSubstepCount = value)
            .build());
        
        // 求解器迭代
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_solver_iterations"),
                data.physicsSolverIterations, 1, 16)
            .setDefaultValue(4)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_solver_iterations.tooltip"))
            .setSaveConsumer(value -> data.physicsSolverIterations = value)
            .build());
        
        // 线性阻尼
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_linear_damping"),
                (int)(data.physicsLinearDampingScale * 100), 0, 200)
            .setDefaultValue(30)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_linear_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsLinearDampingScale = value / 100.0f)
            .build());
        
        // 角速度阻尼
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_angular_damping"),
                (int)(data.physicsAngularDampingScale * 100), 0, 200)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_angular_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsAngularDampingScale = value / 100.0f)
            .build());
        
        // 质量缩放
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_mass_scale"),
                (int)(data.physicsMassScale * 10), 1, 100)
            .setDefaultValue(20)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_mass_scale.tooltip"))
            .setSaveConsumer(value -> data.physicsMassScale = value / 10.0f)
            .build());
        
        // 线性弹簧刚度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_linear_stiffness"),
                (int)(data.physicsLinearSpringStiffnessScale * 1000), 1, 100)
            .setDefaultValue(10)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_linear_stiffness.tooltip"))
            .setSaveConsumer(value -> data.physicsLinearSpringStiffnessScale = value / 1000.0f)
            .build());
        
        // 角度弹簧刚度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_angular_stiffness"),
                (int)(data.physicsAngularSpringStiffnessScale * 1000), 1, 100)
            .setDefaultValue(10)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_angular_stiffness.tooltip"))
            .setSaveConsumer(value -> data.physicsAngularSpringStiffnessScale = value / 1000.0f)
            .build());
        
        // 线性弹簧阻尼
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_linear_spring_damping"),
                (int)(data.physicsLinearSpringDampingFactor * 10), 1, 200)
            .setDefaultValue(80)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_linear_spring_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsLinearSpringDampingFactor = value / 10.0f)
            .build());
        
        // 角度弹簧阻尼
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_angular_spring_damping"),
                (int)(data.physicsAngularSpringDampingFactor * 10), 1, 200)
            .setDefaultValue(80)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_angular_spring_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsAngularSpringDampingFactor = value / 10.0f)
            .build());
        
        // 惯性强度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_inertia"),
                (int)(data.physicsInertiaStrength * 100), 0, 300)
            .setDefaultValue(100)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_inertia.tooltip"))
            .setSaveConsumer(value -> data.physicsInertiaStrength = value / 100.0f)
            .build());
        
        // 最大线速度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity"),
                (int)(data.physicsMaxLinearVelocity * 10), 1, 500)
            .setDefaultValue(10)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_linear_velocity.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxLinearVelocity = value / 10.0f)
            .build());
        
        // 最大角速度
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity"),
                (int)(data.physicsMaxAngularVelocity * 10), 1, 500)
            .setDefaultValue(10)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_max_angular_velocity.tooltip"))
            .setSaveConsumer(value -> data.physicsMaxAngularVelocity = value / 10.0f)
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
        
        // ====== 胸部物理专用参数 ======
        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_enabled"),
                data.physicsBustEnabled)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_enabled.tooltip"))
            .setSaveConsumer(value -> data.physicsBustEnabled = value)
            .build());
        
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_linear_damping"),
                (int)(data.physicsBustLinearDampingScale * 100), 0, 500)
            .setDefaultValue(150)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_linear_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsBustLinearDampingScale = value / 100.0f)
            .build());
        
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_angular_damping"),
                (int)(data.physicsBustAngularDampingScale * 100), 0, 500)
            .setDefaultValue(150)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_angular_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsBustAngularDampingScale = value / 100.0f)
            .build());
        
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_mass_scale"),
                (int)(data.physicsBustMassScale * 10), 1, 100)
            .setDefaultValue(10)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_mass_scale.tooltip"))
            .setSaveConsumer(value -> data.physicsBustMassScale = value / 10.0f)
            .build());
        
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_linear_stiffness"),
                (int)(data.physicsBustLinearSpringStiffnessScale * 10), 1, 500)
            .setDefaultValue(100)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_linear_stiffness.tooltip"))
            .setSaveConsumer(value -> data.physicsBustLinearSpringStiffnessScale = value / 10.0f)
            .build());
        
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_angular_stiffness"),
                (int)(data.physicsBustAngularSpringStiffnessScale * 10), 1, 500)
            .setDefaultValue(100)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_angular_stiffness.tooltip"))
            .setSaveConsumer(value -> data.physicsBustAngularSpringStiffnessScale = value / 10.0f)
            .build());
        
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_linear_spring_damping"),
                (int)(data.physicsBustLinearSpringDampingFactor * 10), 1, 200)
            .setDefaultValue(30)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_linear_spring_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsBustLinearSpringDampingFactor = value / 10.0f)
            .build());
        
        physicsCategory.addEntry(entryBuilder
            .startIntSlider(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_angular_spring_damping"),
                (int)(data.physicsBustAngularSpringDampingFactor * 10), 1, 200)
            .setDefaultValue(30)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_angular_spring_damping.tooltip"))
            .setSaveConsumer(value -> data.physicsBustAngularSpringDampingFactor = value / 10.0f)
            .build());
        
        // 胸部防凹陷
        physicsCategory.addEntry(entryBuilder
            .startBooleanToggle(
                Component.translatable("gui.mmdskin.mod_settings.physics_bust_clamp_inward"),
                data.physicsBustClampInward)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("gui.mmdskin.mod_settings.physics_bust_clamp_inward.tooltip"))
            .setSaveConsumer(value -> data.physicsBustClampInward = value)
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
        
        builder.setSavingRunnable(() -> {
            MmdSkinConfig.save();
            // 同步渲染模式设置到工厂
            com.shiroha.mmdskin.renderer.core.RenderModeManager.setUseGpuSkinning(data.gpuSkinningEnabled);
            // 同步物理配置到 Rust 引擎
            try {
                com.shiroha.mmdskin.NativeFunc.GetInst().SetPhysicsConfig(
                    data.physicsGravityY,
                    data.physicsFps,
                    data.physicsMaxSubstepCount,
                    data.physicsSolverIterations,
                    data.physicsPgsIterations,
                    data.physicsMaxCorrectiveVelocity,
                    data.physicsLinearDampingScale,
                    data.physicsAngularDampingScale,
                    data.physicsMassScale,
                    data.physicsLinearSpringStiffnessScale,
                    data.physicsAngularSpringStiffnessScale,
                    data.physicsLinearSpringDampingFactor,
                    data.physicsAngularSpringDampingFactor,
                    data.physicsInertiaStrength,
                    data.physicsMaxLinearVelocity,
                    data.physicsMaxAngularVelocity,
                    data.physicsBustEnabled,
                    data.physicsBustLinearDampingScale,
                    data.physicsBustAngularDampingScale,
                    data.physicsBustMassScale,
                    data.physicsBustLinearSpringStiffnessScale,
                    data.physicsBustAngularSpringStiffnessScale,
                    data.physicsBustLinearSpringDampingFactor,
                    data.physicsBustAngularSpringDampingFactor,
                    data.physicsBustClampInward,
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
