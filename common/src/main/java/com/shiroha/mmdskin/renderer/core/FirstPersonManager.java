package com.shiroha.mmdskin.renderer.core;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 第一人称模型管理器
 * 
 * 集中管理第一人称 MMD 模型显示的状态，包括：
 * - 判断是否应启用第一人称模型渲染
 * - 管理 Rust 侧的头部隐藏状态
 * - 每帧从 Rust 获取动画后的眼睛骨骼位置，驱动相机跟踪
 */
public final class FirstPersonManager {
    private static final Logger logger = LogManager.getLogger();
    
    /** 模型基础缩放因子（与 MMDModelOpenGL 中一致） */
    private static final float MODEL_SCALE = 0.09f;
    
    /** 缓存的模型缩放 */
    private static float cachedModelScale = 1.0f;
    
    /** 当前是否处于第一人称模型模式 */
    private static boolean activeFirstPerson = false;
    
    /** 当前跟踪的模型句柄（用于检测模型切换） */
    private static long trackedModelHandle = 0;

    /** 每帧更新的眼睛骨骼动画位置（模型局部空间） */
    private static final float[] eyeBonePos = new float[3];
    
    /** 眼睛骨骼是否有效（至少有一个分量非零） */
    private static boolean eyeBoneValid = false;

    private FirstPersonManager() {}

    /**
     * 判断当前是否应该使用第一人称模型渲染
     * 条件：配置启用 + 游戏处于第一人称视角
     */
    public static boolean shouldRenderFirstPerson() {
        if (!ConfigManager.isFirstPersonModelEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    /**
     * 阶段一：渲染前调用，管理 Rust 侧的头部隐藏状态
     *
     * @param nf NativeFunc 实例
     * @param modelHandle 模型句柄
     * @param modelScale 组合缩放系数（model.properties size × ModelConfigData.modelScale）
     * @param isLocalPlayer 是否为本地玩家
     */
    public static void preRender(NativeFunc nf, long modelHandle, float modelScale, boolean isLocalPlayer) {
        boolean shouldEnable = isLocalPlayer && shouldRenderFirstPerson();
        // 模型句柄变化时重置状态
        if (modelHandle != trackedModelHandle) {
            if (trackedModelHandle != 0 && activeFirstPerson) {
                nf.SetFirstPersonMode(trackedModelHandle, false);
            }
            trackedModelHandle = modelHandle;
            activeFirstPerson = false;
        }

        if (shouldEnable != activeFirstPerson) {
            nf.SetFirstPersonMode(modelHandle, shouldEnable);
            activeFirstPerson = shouldEnable;

            if (shouldEnable) {
                logger.info("第一人称模式启用: modelScale={}", modelScale);
            } else {
                logger.info("第一人称模式禁用");
            }
        }

        // 始终同步缩放值，确保运行时调整缩放后相机位置跟随
        if (shouldEnable) {
            cachedModelScale = modelScale;
        }
    }

    /**
     * 阶段二：渲染后调用，获取当前帧骨骼动画更新后的眼睛位置
     *
     * @param nf NativeFunc 实例
     * @param modelHandle 模型句柄
     */
    public static void postRender(NativeFunc nf, long modelHandle) {
        nf.GetEyeBonePosition(modelHandle, eyeBonePos);
        eyeBoneValid = (eyeBonePos[0] != 0.0f || eyeBonePos[1] != 0.0f || eyeBonePos[2] != 0.0f);
    }

    /**
     * 获取第一人称模式是否激活
     */
    public static boolean isActive() {
        return activeFirstPerson;
    }

    /**
     * 眼睛骨骼位置是否有效（已找到且位置非零）
     */
    public static boolean isEyeBoneValid() {
        return eyeBoneValid;
    }

    /**
     * 获取眼睛骨骼在世界空间中的偏移量 [x, y, z]（方块单位）
     * 模型局部空间 → 缩放 → 世界空间偏移
     *
     * @param out 输出数组 [x, y, z]，长度至少 3
     */
    public static void getEyeWorldOffset(float[] out) {
        float scale = MODEL_SCALE * cachedModelScale;
        out[0] = eyeBonePos[0] * scale;
        out[1] = eyeBonePos[1] * scale;
        out[2] = eyeBonePos[2] * scale;
    }

    /**
     * 重置状态（模型卸载时调用）
     */
    public static void reset() {
        activeFirstPerson = false;
        trackedModelHandle = 0;
        cachedModelScale = 1.0f;
        eyeBonePos[0] = 0.0f;
        eyeBonePos[1] = 0.0f;
        eyeBonePos[2] = 0.0f;
        eyeBoneValid = false;
    }
}
