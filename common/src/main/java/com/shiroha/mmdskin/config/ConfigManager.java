/* 文件职责：向运行时提供线程安全的全局配置读取入口。 */
package com.shiroha.mmdskin.config;

public final class ConfigManager {
    private static volatile IConfigProvider provider;

    private ConfigManager() {
    }

    public static void init(IConfigProvider configProvider) {
        provider = configProvider;
    }

    public static boolean isLightingEnabled() {
        return provider != null ? provider.isLightingEnabled() : true;
    }

    public static boolean isGpuSkinningEnabled() {
        return provider != null ? provider.isGpuSkinningEnabled() : true;
    }

    public static int getModelPoolMaxCount() {
        return provider != null ? provider.getModelPoolMaxCount() : 20;
    }

    /**
     * 是否自动下载附近玩家正在使用的模型。
     *
     * <p>默认关闭：只下"半径内玩家正在用、且服务器授权分发"的模型，
     * 不会把服务器上的模型批量拉下来。
     */
    public static boolean isAutoDownloadNearbyModels() {
        return provider != null && provider.isAutoDownloadNearbyModels();
    }

    /** 自动下载半径（格）。 */
    public static int getAutoDownloadRadius() {
        return provider != null ? provider.getAutoDownloadRadius() : 48;
    }

    public static boolean isPerformanceProfilingEnabled() {
        return provider != null ? provider.isPerformanceProfilingEnabled() : false;
    }

    public static int getPerformanceLogIntervalSeconds() {
        return provider != null ? provider.getPerformanceLogIntervalSeconds() : 5;
    }

    public static int getMaxVisibleModelsPerFrame() {
        return provider != null ? provider.getMaxVisibleModelsPerFrame() : 20;
    }

    /**
     * 加载前距离闸门（格）；0 = 不限制。
     *
     * <p>用来在 acquire 之前挡掉远离相机的实体：不触发 PMX 解析、独立 Bullet 物理世界
     * 与 GPU buffer 的创建，模型一多时的卡顿主要就来自这里。
     */
    public static float getModelLoadMaxDistance() {
        return provider != null ? provider.getModelLoadMaxDistance() : 64.0f;
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

    public static boolean isRagdollEnabled() {
        return provider != null ? provider.isRagdollEnabled() : true;
    }

    public static String getCorpseKeepMode() {
        return provider != null ? provider.getCorpseKeepMode() : "NEARBY";
    }

    public static double getCorpseKeepRadius() {
        return provider != null ? provider.getCorpseKeepRadius() : 48.0;
    }

    public static long getCorpseKeepSeconds() {
        return provider != null ? provider.getCorpseKeepSeconds() : 60;
    }

    public static int getMaxCorpses() {
        return provider != null ? provider.getMaxCorpses() : 2;
    }

    public static float getRagdollJointStrength() {
        return provider != null ? provider.getRagdollJointStrength() : 1.35f;
    }

    public static boolean isHideDeathPoof() {
        return provider != null ? provider.isHideDeathPoof() : true;
    }

    public static boolean isHijackDeathScreen() {
        return provider != null ? provider.isHijackDeathScreen() : true;
    }

    public static float getRagdollDeathAnimSeconds() {
        return provider != null ? provider.getRagdollDeathAnimSeconds() : 0.0f;
    }

    public static int getRagdollPhysicsKeepSeconds() {
        return provider != null ? provider.getRagdollPhysicsKeepSeconds() : 60;
    }

    public interface IConfigProvider extends IRenderConfig, IToonConfig, IPhysicsConfig, IVRConfig, IRagdollConfig {
        String getMobModelReplacement(String entityTypeId);
    }
}
