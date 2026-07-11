package com.shiroha.mmdskin.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Sparkle's Morpher 兼容辅助类。
 *
 * <p>Sparkle's Morpher 是 OpenYSM 衍生的玩家模型加载器（mod id: {@code sparkle_morpher}，
 * 包名 {@code com.micaftic.morpher}）。它与原版 YSM 共享同一渲染目标（玩家），但走独立
 * 的网络通道与能力体系，因此 MC-M-MD 在 SparkleMorpher 模型激活时必须让出
 * {@code PlayerRenderer.render} 的渲染权，避免二者在同一帧互相 {@code ci.cancel()}。</p>
 *
 * <p>本类全部通过反射访问 SparkleMorpher 的公开 API：{@code YesSteveModel.isAvailable()}、
 * {@code PlayerCapability.get(Entity)} -> {@code Optional} -> {@code cap.isModelActive()}，
 * 以及 {@code GeneralConfig} 上的 DISABLE_SELF_MODEL / DISABLE_OTHER_MODEL / DISABLE_SELF_HANDS
 * 开关。运行时 SparkleMorpher 缺失则静默返回 {@code false}，与 {@code YsmCompat} 对官方 YSM
 * 的处理保持一致。</p>
 */
public final class SparkleMorpherCompat {
    private static final Logger logger = LogManager.getLogger();

    private static volatile int initState = 0; // 0=未初始化, 1=存在, -1=缺失
    private static volatile boolean present = false;

    private static volatile Method isAvailableMethod;     // YesSteveModel.isAvailable() -> boolean
    private static volatile Method capabilityGetMethod;    // PlayerCapability.get(Entity) -> Optional
    private static volatile Method optionalOrElseMethod;  // Optional.orElse(Object) -> Object
    private static volatile Method isModelActiveMethod;    // cap.isModelActive() -> boolean

    private static volatile Object disableSelfModelValue;
    private static volatile Object disableOtherModelValue;
    private static volatile Object disableSelfHandsValue;
    private static volatile Method booleanValueGetMethod;

    private SparkleMorpherCompat() {
    }

    private static void ensureInit() {
        if (initState != 0) {
            return;
        }
        synchronized (SparkleMorpherCompat.class) {
            if (initState != 0) {
                return;
            }
            try {
                Class<?> mainClass = Class.forName("com.micaftic.morpher.YesSteveModel");
                isAvailableMethod = mainClass.getMethod("isAvailable");

                Class<?> capClass = Class.forName("com.micaftic.morpher.capability.PlayerCapability");
                // 优先 get(Entity)，回退 get(Player)；二者均为 @ExpectPlatform 静态方法。
                try {
                    capabilityGetMethod = capClass.getMethod("get", Entity.class);
                } catch (NoSuchMethodException e) {
                    capabilityGetMethod = capClass.getMethod("get", Player.class);
                }

                Class<?> optionalClass = Class.forName("java.util.Optional");
                optionalOrElseMethod = optionalClass.getMethod("orElse", Object.class);

                // isModelActive 定义在祖先类 LivingAnimatable，getMethod 会沿继承链查找。
                isModelActiveMethod = capClass.getMethod("isModelActive");

                Class<?> configClass = Class.forName("com.micaftic.morpher.config.GeneralConfig");
                disableSelfModelValue = configClass.getField("DISABLE_SELF_MODEL").get(null);
                disableOtherModelValue = configClass.getField("DISABLE_OTHER_MODEL").get(null);
                disableSelfHandsValue = configClass.getField("DISABLE_SELF_HANDS").get(null);

                if (disableSelfModelValue != null) {
                    booleanValueGetMethod = disableSelfModelValue.getClass().getMethod("get");
                }

                present = true;
                initState = 1;
            } catch (Throwable t) {
                logger.debug("SparkleMorpher compat not initialized: {}", t.getMessage());
                present = false;
                initState = -1;
            }
        }
    }

    /** SparkleMorpher 是否已安装且原生加速可用。 */
    public static boolean isSparkleAvailable() {
        ensureInit();
        if (!present || isAvailableMethod == null) {
            return false;
        }
        try {
            return (boolean) isAvailableMethod.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 该实体上是否挂载了激活的 SparkleMorpher 模型（不考虑自/他显示开关）。 */
    public static boolean isSparkleModelActive(LivingEntity entity) {
        ensureInit();
        if (!present || !isSparkleAvailable()
                || capabilityGetMethod == null || isModelActiveMethod == null || optionalOrElseMethod == null) {
            return false;
        }
        try {
            Object optional = capabilityGetMethod.invoke(null, entity);
            if (optional == null) {
                return false;
            }
            Object cap = optionalOrElseMethod.invoke(optional, new Object[]{ null });
            if (cap == null) {
                return false;
            }
            return (boolean) isModelActiveMethod.invoke(cap);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 该实体上 SparkleMorpher 模型是否应当接管渲染（考虑自/他显示开关），
     * 与官方 YSM 的 {@code isYsmActive} 语义对齐。MC-M-MD 据此让出渲染权。
     */
    public static boolean isSparkleActive(LivingEntity entity) {
        if (!isSparkleModelActive(entity)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(entity.getUUID());
        return isLocalPlayer ? !isDisableSelfModel() : !isDisableOtherModel();
    }

    /** SparkleMorpher 是否开启了“阻止自身模型渲染”。 */
    public static boolean isDisableSelfModel() {
        ensureInit();
        return getBooleanValue(disableSelfModelValue);
    }

    /** SparkleMorpher 是否开启了“阻止其他玩家模型渲染”。 */
    public static boolean isDisableOtherModel() {
        ensureInit();
        return getBooleanValue(disableOtherModelValue);
    }

    /** SparkleMorpher 是否开启了“阻止自身手臂渲染”。 */
    public static boolean isDisableSelfHands() {
        ensureInit();
        return getBooleanValue(disableSelfHandsValue);
    }

    private static boolean getBooleanValue(Object valueObj) {
        if (present && valueObj != null && booleanValueGetMethod != null) {
            try {
                return (boolean) booleanValueGetMethod.invoke(valueObj);
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }
}
