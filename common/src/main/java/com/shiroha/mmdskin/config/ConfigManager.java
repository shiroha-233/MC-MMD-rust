package com.shiroha.mmdskin.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 统一配置管理器
 */

public class ConfigManager {
    private static final Logger logger = LogManager.getLogger();
    private static IConfigProvider provider;

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

    public static boolean isToonRenderingEnabled() {
        return provider != null ? provider.isToonRenderingEnabled() : true;
    }

    public static int getToonLevels() {
        return provider != null ? provider.getToonLevels() : 3;
    }

    public static boolean isToonOutlineEnabled() {
        return provider != null ? provider.isToonOutlineEnabled() : false;
    }

    public static float getToonOutlineWidth() {
        return provider != null ? provider.getToonOutlineWidth() : 0.003f;
    }

    public static float getToonRimPower() {
        return provider != null ? provider.getToonRimPower() : 5.0f;
    }

    public static float getToonRimIntensity() {
        return provider != null ? provider.getToonRimIntensity() : 0.1f;
    }

    public static float getToonShadowR() {
        return provider != null ? provider.getToonShadowR() : 0.8f;
    }

    public static float getToonShadowG() {
        return provider != null ? provider.getToonShadowG() : 0.8f;
    }

    public static float getToonShadowB() {
        return provider != null ? provider.getToonShadowB() : 0.8f;
    }

    public static float getToonSpecularPower() {
        return provider != null ? provider.getToonSpecularPower() : 30.0f;
    }

    public static float getToonSpecularIntensity() {
        return provider != null ? provider.getToonSpecularIntensity() : 0.08f;
    }

    public static float getToonOutlineR() {
        return provider != null ? provider.getToonOutlineR() : 0.0f;
    }

    public static float getToonOutlineG() {
        return provider != null ? provider.getToonOutlineG() : 0.0f;
    }

    public static float getToonOutlineB() {
        return provider != null ? provider.getToonOutlineB() : 0.0f;
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

    public interface IConfigProvider extends IRenderConfig, IToonConfig, IPhysicsConfig, IVRConfig {
    }
}
