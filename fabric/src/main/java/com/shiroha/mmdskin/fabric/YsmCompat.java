package com.shiroha.mmdskin.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * YSM (Yes Steve Model) 兼容性辅助类 (Fabric)
 * 用于检测玩家是否正在使用 YSM 模型，以便 MMD 进行避让
 */
public class YsmCompat {
    private static boolean ysmChecked = false;
    private static boolean ysmPresent = false;
    
    private static Method isModelActiveMethod = null;
    private static Method getCapabilityMethod = null;
    
    // YSM 配置项字段 (BooleanValue 类型)
    private static Object disableSelfModelValue = null;
    private static Object disableOtherModelValue = null;
    private static Object disableSelfHandsValue = null;
    
    // BooleanValue.get() 方法
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

   /**
    * 检查实体是否正在使用 YSM 模型
    */
   public static boolean isYsmModelActive(LivingEntity entity) {
      if (!ysmChecked) {
         ysmPresent = FabricLoader.getInstance().isModLoaded("yes_steve_model");
         if (ysmPresent) {
            try {
               Class<?> entityWithCapsClass = Class.forName("com.elfmcys.yesstevemodel.O000oOOO0OoooO0OOO0Oo000");
               getCapabilityMethod = entityWithCapsClass.getMethod("oOO0000ooo0oOOo0OO0oo0Oo");
               
               Class<?> dataClass = Class.forName("com.elfmcys.yesstevemodel.ooOO0OO0o0ooO00oooO0O00O");
               isModelActiveMethod = dataClass.getMethod("oO00oooo0o0o000OO0OOo000");
               
               Class<?> ysmConfigClass = Class.forName("com.elfmcys.yesstevemodel.OOO0OOOOo0O0Oo00oOOooo0O");
               disableSelfModelValue = ysmConfigClass.getDeclaredField("Oo00OoO0o000oOOooOoOOoO0").get(null);
               disableOtherModelValue = ysmConfigClass.getDeclaredField("oOO0000ooo0oOOo0OO0oo0Oo").get(null);
               disableSelfHandsValue = ysmConfigClass.getDeclaredField("O0Oo0000OoOoOOOo0oo0o000").get(null);

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

      if (ysmPresent && getCapabilityMethod != null && isModelActiveMethod != null) {
         try {
            Object optional = getCapabilityMethod.invoke(entity);
            if (optional != null) {
               Method isPresentMethod = optional.getClass().getMethod("isPresent");
               if ((Boolean) isPresentMethod.invoke(optional)) {
                  Method getMethod = optional.getClass().getMethod("get");
                  Object data = getMethod.invoke(optional);
                  if (data != null) {
                     return (Boolean) isModelActiveMethod.invoke(data);
                  }
               }
            }
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
