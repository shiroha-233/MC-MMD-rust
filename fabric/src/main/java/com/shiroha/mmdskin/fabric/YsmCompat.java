package com.shiroha.mmdskin.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Method;

/**
 * YSM (Yes Steve Model) 兼容性辅助类 (Fabric)
 * 通过反射访问 YSM 内部 API，用于检测玩家是否正在使用 YSM 模型
 */
public class YsmCompat {
    private static boolean ysmChecked = false;
    private static boolean ysmPresent = false;

    private static Method isModelActiveMethod = null;
    
    // YSM 配置项字段
    private static Object disableSelfModelValue = null;
    private static Object disableOtherModelValue = null;
    private static Object disableSelfHandsValue = null;

    private static Method booleanValueGetMethod = null;

    /**
     * 综合判断：实体是否应显示 YSM 模型（而非原版/MMD）
     */
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

    /**
     * 检查实体是否配置了 YSM 模型（无论当前是否显示）
     */
    public static boolean isYsmModelActive(LivingEntity entity) {
        if (!ysmChecked) {
            ysmPresent = FabricLoader.getInstance().isModLoaded("yes_steve_model");
            if (ysmPresent) {
                try {
                    Class<?> ysmConfigClass = Class.forName("com.elfmcys.yesstevemodel.O00OO0oOOO0OoO0OOOo0OooO");
                    disableSelfModelValue = ysmConfigClass.getDeclaredField("oOooOoOO00OOooo0OooOO0o0").get(null);
                    disableOtherModelValue = ysmConfigClass.getDeclaredField("oo0o0OO0Oo0oOOo00o00Ooo0").get(null);
                    disableSelfHandsValue = ysmConfigClass.getDeclaredField("ooO00O0oOooO0OoOO000OoOo").get(null);

                    // 检查模型是否激活 (使用 YesSteveModel.isAvailable 作为通用判定)
                    Class<?> ysmMainClass = Class.forName("com.elfmcys.yesstevemodel.YesSteveModel");
                    isModelActiveMethod = ysmMainClass.getMethod("isAvailable");
                    
                    if (disableSelfModelValue != null) {
                        booleanValueGetMethod = disableSelfModelValue.getClass().getMethod("get");
                    }
                } catch (Exception e) {
                    System.err.println("[MMDSkin] Failed to initialize YSM Fabric compatibility: " + e.getMessage());
                    ysmPresent = false;
                }
            }

            ysmChecked = true;
        }

        if (ysmPresent && isModelActiveMethod != null) {
            try {
                return (Boolean) isModelActiveMethod.invoke(null);
            } catch (Exception e) {
                // 忽略调用异常
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
