package com.shiroha.mmdskin.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 统一配置管理器
 */

public class ConfigManager {
    private static final Logger logger = LogManager.getLogger();
    private static volatile IConfigProvider provider;

    public static void init(IConfigProvider configProvider) {
        provider = configProvider;
    }

    public static boolean isOpenGLLightingEnabled() {
        return provider != null ? provider.isOpenGLLightingEnabled() : true;
    }

    public static int getModelPoolMaxCount() {
        return provider != null ? provider.getModelPoolMaxCount() : 20;
    }

    public static boolean isMMDShaderEnabled() {
        return provider != null ? provider.isMMDShaderEnabled() : false;
    }

    public static boolean isGpuSkinningEnabled() {
        return provider != null ? provider.isGpuSkinningEnabled() : false;
    }

    public static boolean isGpuMorphEnabled() {
        return provider != null ? provider.isGpuMorphEnabled() : false;
    }

    public static int getMaxBones() {
        return provider != null ? provider.getMaxBones() : 2048;
    }

    public static boolean isPerformanceProfilingEnabled() {
        return provider != null ? provider.isPerformanceProfilingEnabled() : false;
    }

    public static int getPerformanceLogIntervalSeconds() {
        return provider != null ? provider.getPerformanceLogIntervalSeconds() : 5;
    }

    public static int getMaxVisibleModelsPerFrame() {
        return provider != null ? provider.getMaxVisibleModelsPerFrame() : 10;
    }

    public static float getAnimationLodMediumDistance() {
        return provider != null ? provider.getAnimationLodMediumDistance() : 24.0f;
    }

    public static float getAnimationLodFarDistance() {
        return provider != null ? provider.getAnimationLodFarDistance() : 48.0f;
    }

    public static int getAnimationLodMediumUpdateInterval() {
        return provider != null ? provider.getAnimationLodMediumUpdateInterval() : 2;
    }

    public static int getAnimationLodFarUpdateInterval() {
        return provider != null ? provider.getAnimationLodFarUpdateInterval() : 4;
    }

    public static boolean isToonRenderingEnabled() {
        return provider != null ? provider.isToonRenderingEnabled() : false;
    }

    public static int getToonLevels() {
        return provider != null ? provider.getToonLevels() : 4;
    }

    public static boolean isToonOutlineEnabled() {
        return provider != null ? provider.isToonOutlineEnabled() : true;
    }

    public static float getToonOutlineWidth() {
        return provider != null ? provider.getToonOutlineWidth() : 0.0022f;
    }

    public static float getToonRimPower() {
        return provider != null ? provider.getToonRimPower() : 5.6f;
    }

    public static float getToonRimIntensity() {
        return provider != null ? provider.getToonRimIntensity() : 0.02f;
    }

    public static float getToonShadowR() {
        return provider != null ? provider.getToonShadowR() : 0.78f;
    }

    public static float getToonShadowG() {
        return provider != null ? provider.getToonShadowG() : 0.84f;
    }

    public static float getToonShadowB() {
        return provider != null ? provider.getToonShadowB() : 0.94f;
    }

    public static float getToonSpecularPower() {
        return provider != null ? provider.getToonSpecularPower() : 96.0f;
    }

    public static float getToonSpecularIntensity() {
        return provider != null ? provider.getToonSpecularIntensity() : 0.015f;
    }

    public static float getToonOutlineR() {
        return provider != null ? provider.getToonOutlineR() : 0.06f;
    }

    public static float getToonOutlineG() {
        return provider != null ? provider.getToonOutlineG() : 0.08f;
    }

    public static float getToonOutlineB() {
        return provider != null ? provider.getToonOutlineB() : 0.12f;
    }

    public static boolean isPhysicsEnabled() {
        return provider != null ? provider.isPhysicsEnabled() : true;
    }

    public static float getPhysicsGravityY() {
        return provider != null ? provider.getPhysicsGravityY() : -98.0f;
    }

    public static float getPhysicsFps() {
        return provider != null ? provider.getPhysicsFps() : 60.0f;
    }

    public static int getPhysicsMaxSubstepCount() {
        return provider != null ? provider.getPhysicsMaxSubstepCount() : 5;
    }

    public static float getPhysicsInertiaStrength() {
        return provider != null ? provider.getPhysicsInertiaStrength() : 0.5f;
    }

    public static float getPhysicsMaxLinearVelocity() {
        return provider != null ? provider.getPhysicsMaxLinearVelocity() : 20.0f;
    }

    public static float getPhysicsMaxAngularVelocity() {
        return provider != null ? provider.getPhysicsMaxAngularVelocity() : 20.0f;
    }

    public static boolean isPhysicsJointsEnabled() {
        return provider != null ? provider.isPhysicsJointsEnabled() : true;
    }

    public static boolean isPhysicsKinematicFilter() {
        return provider != null ? provider.isPhysicsKinematicFilter() : true;
    }

    public static boolean isPhysicsDebugLog() {
        return provider != null ? provider.isPhysicsDebugLog() : false;
    }

    public static int getMaxPhysicsModelsPerFrame() {
        return provider != null ? provider.getMaxPhysicsModelsPerFrame() : 10;
    }

    public static float getPhysicsLodMaxDistance() {
        return provider != null ? provider.getPhysicsLodMaxDistance() : 24.0f;
    }

    public static boolean isFirstPersonModelEnabled() {
        return provider != null ? provider.isFirstPersonModelEnabled() : false;
    }

    public static float getFirstPersonCameraForwardOffset() {
        return provider != null ? provider.getFirstPersonCameraForwardOffset() : 0.0f;
    }

    public static float getFirstPersonCameraVerticalOffset() {
        return provider != null ? provider.getFirstPersonCameraVerticalOffset() : 0.0f;
    }

    public static int getTextureCacheBudgetMB() {
        return provider != null ? provider.getTextureCacheBudgetMB() : 256;
    }

    public static boolean isDebugHudEnabled() {
        return provider != null ? provider.isDebugHudEnabled() : false;
    }

    public static boolean isVREnabled() {
        return provider != null ? provider.isVREnabled() : false;
    }

    public static float getVRArmIKStrength() {
        return provider != null ? provider.getVRArmIKStrength() : 1.0f;
    }

    public static String getMobModelReplacement(String entityTypeId) {
        return provider != null ? provider.getMobModelReplacement(entityTypeId) : "";
    }

    public interface IConfigProvider extends IRenderConfig, IToonConfig, IPhysicsConfig, IVRConfig {
        String getMobModelReplacement(String entityTypeId);
    }
}
