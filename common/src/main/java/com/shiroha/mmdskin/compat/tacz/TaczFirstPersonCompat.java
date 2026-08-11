package com.shiroha.mmdskin.compat.tacz;

import net.minecraft.client.player.LocalPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** 文件职责：在没有 TaCZ 编译期依赖时读取本地玩家的原生 ADS 状态。 */
public final class TaczFirstPersonCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String OPERATOR_CLASS = "com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator";
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile Access cachedAccess;
    private static volatile boolean resolutionAttempted;

    private TaczFirstPersonCompat() {
    }

    /**
     * TaCZ 自己已经按枪械 aimTime 插值，这里只读取并清洗结果，不再添加第二套固定过渡时间。
     */
    public static AimState getAimState(LocalPlayer player, float partialTick) {
        if (player == null) {
            return AimState.INACTIVE;
        }

        Access access = resolveAccess(player.getClass().getClassLoader(), player.getClass());
        if (access == null) {
            return AimState.INACTIVE;
        }

        try {
            return access.read(player, sanitizePartialTick(partialTick));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logFailureOnce("读取 TaCZ ADS 状态失败，已回退为未瞄准", error);
            return AimState.INACTIVE;
        }
    }

    private static Access resolveAccess(ClassLoader classLoader, Class<?> playerClass) {
        if (resolutionAttempted) {
            return cachedAccess;
        }
        synchronized (TaczFirstPersonCompat.class) {
            if (resolutionAttempted) {
                return cachedAccess;
            }
            try {
                Class<?> operatorType = Class.forName(OPERATOR_CLASS, false, classLoader);
                cachedAccess = Access.create(operatorType, playerClass);
            } catch (ClassNotFoundException ignored) {
                // 未安装 TaCZ 是正常情况，不输出警告。
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                logFailureOnce("TaCZ ADS API 不兼容，已禁用 ADS 动作同步", error);
            } finally {
                resolutionAttempted = true;
            }
            return cachedAccess;
        }
    }

    static float sanitizeProgress(float progress) {
        if (!Float.isFinite(progress)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, progress));
    }

    private static float sanitizePartialTick(float partialTick) {
        return sanitizeProgress(partialTick);
    }

    private static void logFailureOnce(String message, Throwable error) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(message, error);
        }
    }

    /** aiming 表示按键目标；progress 表示仍需保留的实际过渡量。 */
    public record AimState(boolean aiming, float progress) {
        public static final AimState INACTIVE = new AimState(false, 0.0f);
    }

    private record Access(Method fromLocalPlayer, Method isAim, Method getProgress) {
        static Access create(Class<?> operatorType, Class<?> playerClass) throws ReflectiveOperationException {
            Method factory = null;
            for (Method method : operatorType.getMethods()) {
                if (method.getName().equals("fromLocalPlayer")
                        && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(playerClass)) {
                    factory = method;
                    break;
                }
            }
            if (factory == null) {
                throw new NoSuchMethodException("IClientPlayerGunOperator.fromLocalPlayer");
            }
            return new Access(
                    factory,
                    operatorType.getMethod("isAim"),
                    operatorType.getMethod("getClientAimingProgress", float.class));
        }

        AimState read(Object player, float partialTick) throws ReflectiveOperationException {
            Object operator = fromLocalPlayer.invoke(null, player);
            if (operator == null) {
                return AimState.INACTIVE;
            }
            boolean aiming = Boolean.TRUE.equals(isAim.invoke(operator));
            Object rawProgress = getProgress.invoke(operator, partialTick);
            float progress = rawProgress instanceof Number number ? number.floatValue() : 0.0f;
            return new AimState(aiming, sanitizeProgress(progress));
        }
    }
}
