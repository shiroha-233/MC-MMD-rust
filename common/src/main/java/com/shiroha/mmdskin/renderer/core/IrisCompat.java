package com.shiroha.mmdskin.renderer.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Iris 光影模组运行时兼容检测
 * 
 * 通过反射检测 Iris API，避免硬依赖。
 * 用于判断当前是否有 Iris 光影包激活，以便渲染路径做相应适配。
 */
public class IrisCompat {
    private static final Logger logger = LogManager.getLogger();
    
    private static Boolean irisPresent = null;
    private static Method isShaderPackInUseMethod = null;
    private static Object irisApiInstance = null;
    
    // 阴影渲染状态检测（反射）
    private static boolean shadowStateDetected = false;
    private static Method areShadowsBeingRenderedMethod = null;
    
    /**
     * 检测 Iris 光影包是否正在使用中
     * 
     * @return true 表示 Iris 已加载且有光影包正在生效
     */
    public static boolean isIrisShaderActive() {
        if (irisPresent == null) {
            detectIris();
        }
        if (!irisPresent) return false;
        
        try {
            return (Boolean) isShaderPackInUseMethod.invoke(irisApiInstance);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 通过反射探测 Iris API 是否可用
     */
    private static void detectIris() {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstanceMethod = irisApiClass.getMethod("getInstance");
            irisApiInstance = getInstanceMethod.invoke(null);
            isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            irisPresent = true;
            logger.info("[IrisCompat] 检测到 Iris API，已启用兼容模式");
        } catch (ClassNotFoundException e) {
            irisPresent = false;
            logger.info("[IrisCompat] 未检测到 Iris，跳过兼容");
        } catch (Exception e) {
            irisPresent = false;
            logger.warn("[IrisCompat] Iris API 检测异常", e);
        }
    }
    
    /**
     * 检测当前是否处于 Iris 阴影渲染 pass
     * 
     * 阴影 pass 期间 OpenGL 状态特殊（阴影帧缓冲区已绑定），
     * 此时执行模型创建（含 glTexImage2D 等纹理操作）会导致 AMD 驱动崩溃。
     * 
     * @return true 表示当前正在阴影 pass 中
     */
    public static boolean isRenderingShadows() {
        if (!shadowStateDetected) {
            detectShadowState();
        }
        if (areShadowsBeingRenderedMethod == null) return false;
        
        try {
            return (Boolean) areShadowsBeingRenderedMethod.invoke(null);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 通过反射探测 Iris 阴影渲染状态字段
     */
    private static void detectShadowState() {
        shadowStateDetected = true;
        // Iris 1.6+ (net.irisshaders.iris)
        String[] classNames = {
            "net.irisshaders.iris.shadows.ShadowRenderingState",
            "net.coderbot.iris.shadows.ShadowRenderingState"
        };
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                areShadowsBeingRenderedMethod = clazz.getMethod("areShadowsCurrentlyBeingRendered");
                logger.info("[IrisCompat] 检测到阴影渲染状态: {}", className);
                return;
            } catch (Exception ignored) {}
        }
        logger.debug("[IrisCompat] 未找到阴影渲染状态类（非 Iris 环境或版本不兼容）");
    }
    
    /**
     * 重置检测状态（用于热重载场景）
     */
    public static void reset() {
        irisPresent = null;
        isShaderPackInUseMethod = null;
        irisApiInstance = null;
        shadowStateDetected = false;
        areShadowsBeingRenderedMethod = null;
    }
}
