package com.shiroha.mmdskin.renderer.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Iris 光影模组运行时兼容检测。
 */
public class IrisCompat {
    private static final Logger logger = LogManager.getLogger();

    private static volatile Boolean irisPresent = null;
    private static Method isShaderPackInUseMethod = null;
    private static Object irisApiInstance = null;

    private static volatile boolean shadowStateDetected = false;
    private static Method areShadowsBeingRenderedMethod = null;

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

    private static void detectIris() {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstanceMethod = irisApiClass.getMethod("getInstance");
            irisApiInstance = getInstanceMethod.invoke(null);
            isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            irisPresent = true;
        } catch (ClassNotFoundException e) {
            irisPresent = false;
        } catch (Exception e) {
            irisPresent = false;
            logger.warn("[IrisCompat] Iris API 检测异常", e);
        }
    }

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

    private static void detectShadowState() {
        shadowStateDetected = true;

        String[] classNames = {
            "net.irisshaders.iris.shadows.ShadowRenderingState",
            "net.coderbot.iris.shadows.ShadowRenderingState"
        };
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                areShadowsBeingRenderedMethod = clazz.getMethod("areShadowsCurrentlyBeingRendered");
                return;
            } catch (Exception ignored) {}
        }
    }

    public static void reset() {
        irisPresent = null;
        isShaderPackInUseMethod = null;
        irisApiInstance = null;
        shadowStateDetected = false;
        areShadowsBeingRenderedMethod = null;
    }
}
