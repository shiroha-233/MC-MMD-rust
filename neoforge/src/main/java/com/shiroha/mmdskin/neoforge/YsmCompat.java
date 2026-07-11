package com.shiroha.mmdskin.neoforge;

import com.shiroha.mmdskin.compat.SparkleMorpherCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * YSM (Yes Steve Model) 兼容逻辑 - NeoForge 版
 * 通过反射访问 YSM 的数据和配置，用于在第一人称模式下协调渲染
 */
public class YsmCompat {
   private static final Logger logger = LogManager.getLogger();
   private static boolean ysmChecked = false;
   private static boolean ysmPresent = false;
   private static AttachmentType<?> ysmAttachmentType = null;
   private static Method isModelActiveMethod = null;
   private static Object disableSelfModelValue = null;
   private static Object disableOtherModelValue = null;
   private static Object disableSelfHandsValue = null;
   private static Method booleanValueGetMethod = null;

   /**
    * 判断实体是否应当显示 YSM 模型（而非原版/MMD）
    */
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
         ysmPresent = ModList.get().isLoaded("yes_steve_model");
         if (ysmPresent) {
            try {
               // 反射获取 YSM 核心数据类和字段 (针对 1.21.1 混淆名)
               Class<?> ysmDataClass = Class.forName("com.elfmcys.yesstevemodel.oOooO0OoOoOO0OoOo0o00OoO");
               Field typeField = ysmDataClass.getDeclaredField("ooO0000oO0o0o0o000Oooo0O");
               ysmAttachmentType = (AttachmentType<?>)typeField.get(null);
               isModelActiveMethod = ysmDataClass.getMethod("OO0o0OOoOOO00OoOoOo0oOOO");
               
               // 反射获取 YSM 配置类和配置项
               Class<?> ysmConfigClass = Class.forName("com.elfmcys.yesstevemodel.oO0oo0oooOo0O0ooooOOooOo");
               disableSelfModelValue = ysmConfigClass.getDeclaredField("O0OOoooOOOO0oo0o0OoO0oO0").get(null);
               disableOtherModelValue = ysmConfigClass.getDeclaredField("oOO0ooO00OOooOO0oOo0oO0O").get(null);
               disableSelfHandsValue = ysmConfigClass.getDeclaredField("Oo0OoOO000OooOoooOoo0ooO").get(null);

               if (disableSelfModelValue != null) {
                  booleanValueGetMethod = disableSelfModelValue.getClass().getMethod("get");
               }
            } catch (Exception e) {
               logger.error("YSM NeoForge 兼容初始化失败: {}", e.getMessage());
               ysmPresent = false;
            }
         }

         ysmChecked = true;
      }

      if (ysmPresent && ysmAttachmentType != null && isModelActiveMethod != null) {
         try {
            Object data = entity.getData(ysmAttachmentType);
            if (data != null) {
               return (Boolean) isModelActiveMethod.invoke(data);
            }
         } catch (Exception e) {
            // 忽略调用异常
         }
      }

      return false;
   }

   /** 获取 YSM 是否开启了“阻止自身模型渲染” */
   public static boolean isDisableSelfModel() {
      return getBooleanValue(disableSelfModelValue) || SparkleMorpherCompat.isDisableSelfModel();
   }

   /** 获取 YSM 是否开启了“阻止其他玩家模型渲染” */
   public static boolean isDisableOtherModel() {
      return getBooleanValue(disableOtherModelValue) || SparkleMorpherCompat.isDisableOtherModel();
   }

   /** 获取 YSM 是否开启了“阻止自身手臂渲染” */
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
