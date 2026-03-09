package com.shiroha.mmdskin.fabric;

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
        if (!isYsmModelActive(entity)) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(entity.getUUID());
        if (isLocalPlayer) {
            return !isDisableSelfModel();
        } else {
            return !isDisableOtherModel();
        }
    }

    public static boolean isYsmModelActive(LivingEntity entity) {
        if (!ysmChecked) {
            ysmPresent = FabricLoader.getInstance().isModLoaded("yes_steve_model");
            if (ysmPresent) {
                try {
                    Class<?> ysmConfigClass = Class.forName("com.elfmcys.yesstevemodel.O00OO0oOOO0OoO0OOOo0OooO");
                    disableSelfModelValue = ysmConfigClass.getDeclaredField("oOooOoOO00OOooo0OooOO0o0").get(null);
                    disableOtherModelValue = ysmConfigClass.getDeclaredField("oo0o0OO0Oo0oOOo00o00Ooo0").get(null);
                    disableSelfHandsValue = ysmConfigClass.getDeclaredField("ooO00O0oOooO0OoOO000OoOo").get(null);

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
        return getBooleanValue(disableSelfModelValue);
    }

    public static boolean isDisableOtherModel() {
        return getBooleanValue(disableOtherModelValue);
    }

    public static boolean isDisableSelfHands() {
        return getBooleanValue(disableSelfHandsValue);
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
