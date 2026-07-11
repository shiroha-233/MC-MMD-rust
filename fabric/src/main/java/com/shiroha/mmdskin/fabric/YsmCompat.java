package com.shiroha.mmdskin.fabric;

import com.shiroha.mmdskin.compat.SparkleMorpherCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.reflect.Method;

/** Fabric 侧 YSM 兼容辅助类，用于检测模型状态与相关显示开关。 */
public class YsmCompat {
    private static final Logger logger = LogManager.getLogger();
    private static boolean ysmChecked = false;
    private static boolean ysmPresent = false;

    private static Method isModelActiveMethod = null;

    private static Object disableSelfModelValue = null;
    private static Object disableOtherModelValue = null;
    private static Object disableSelfHandsValue = null;

    private static Method booleanValueGetMethod = null;

    public static boolean isYsmActive(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(entity.getUUID());

        // 官方 YSM：模型激活且未按自/他开关禁用。
        if (isOfficialYsmModelActive(entity)) {
            boolean disabled = isLocalPlayer
                    ? getBooleanValue(disableSelfModelValue)
                    : getBooleanValue(disableOtherModelValue);
            if (!disabled) {
                return true;
            }
        }

        // Sparkle's Morpher：自带自/他开关语义，激活时让出渲染权。
        return SparkleMorpherCompat.isSparkleActive(entity);
    }

    public static boolean isYsmModelActive(LivingEntity entity) {
        return isOfficialYsmModelActive(entity) || SparkleMorpherCompat.isSparkleModelActive(entity);
    }

    private static boolean isOfficialYsmModelActive(LivingEntity entity) {
        if (!ysmChecked) {
            ysmPresent = FabricLoader.getInstance().isModLoaded("yes_steve_model");
            if (ysmPresent) {
                try {
                    Class<?> ysmConfigClass = Class.forName("com.elfmcys.yesstevemodel.OOO0OOOOo0O0Oo00oOOooo0O");
                    disableSelfModelValue = ysmConfigClass.getDeclaredField("Oo00OoO0o000oOOooOoOOoO0").get(null);
                    disableOtherModelValue = ysmConfigClass.getDeclaredField("oOO0000ooo0oOOo0OO0oo0Oo").get(null);
                    disableSelfHandsValue = ysmConfigClass.getDeclaredField("O0Oo0000OoOoOOOo0oo0o000").get(null);

                    Class<?> ysmMainClass = Class.forName("com.elfmcys.yesstevemodel.YesSteveModel");
                    isModelActiveMethod = ysmMainClass.getMethod("isAvailable");

                    if (disableSelfModelValue != null) {
                        booleanValueGetMethod = disableSelfModelValue.getClass().getMethod("get");
                    }
                } catch (Exception e) {
                    logger.error("YSM Fabric 兼容初始化失败: {}", e.getMessage());
                    ysmPresent = false;
                }
            }

            ysmChecked = true;
        }

        if (ysmPresent && isModelActiveMethod != null) {
            try {
                return (Boolean) isModelActiveMethod.invoke(null);
            } catch (Exception e) {

            }
        }

        return false;
    }

    public static boolean isDisableSelfModel() {
        return getBooleanValue(disableSelfModelValue) || SparkleMorpherCompat.isDisableSelfModel();
    }

    public static boolean isDisableOtherModel() {
        return getBooleanValue(disableOtherModelValue) || SparkleMorpherCompat.isDisableOtherModel();
    }

    public static boolean isDisableSelfHands() {
        return getBooleanValue(disableSelfHandsValue) || SparkleMorpherCompat.isDisableSelfHands();
    }

    private static boolean getBooleanValue(Object valueObj) {
        if (ysmPresent && valueObj != null && booleanValueGetMethod != null) {
            try {
                return (Boolean) booleanValueGetMethod.invoke(valueObj);
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }
}
