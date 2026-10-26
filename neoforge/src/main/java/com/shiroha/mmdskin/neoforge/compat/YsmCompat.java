// 负责 NeoForge 侧 YSM 反射绑定与失败回退。
package com.shiroha.mmdskin.neoforge.compat;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class YsmCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Bindings BINDINGS = Bindings.load();

    private YsmCompat() {
    }

    public static boolean isYsmActive(LivingEntity entity) {
        if (!isYsmModelActive(entity)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean local = minecraft.player != null && minecraft.player.getUUID().equals(entity.getUUID());
        return local ? !isDisableSelfModel() : !isDisableOtherModel();
    }

    public static boolean isYsmModelActive(LivingEntity entity) {
        return BINDINGS.isModelActive(entity);
    }

    public static boolean isDisableSelfModel() {
        return BINDINGS.booleanValue(BINDINGS.disableSelfModel());
    }

    public static boolean isDisableOtherModel() {
        return BINDINGS.booleanValue(BINDINGS.disableOtherModel());
    }

    public static boolean isDisableSelfHands() {
        return BINDINGS.booleanValue(BINDINGS.disableSelfHands());
    }

    /**
     * YSM 的 NeoForge 侧绑定全部走反射：YSM 的混淆类名每个版本都会变，
     * 绑定失败时静默回退到原版 / MMD 路由（不崩游戏）。
     *
     * <p>26.3 适配：实体附件的读取走 {@code Entity.getData(AttachmentType)}
     * （NeoForge 26.3 把 {@code getData} 放在了 {@code AttachmentHolder} 基类上）。
     */
    private record Bindings(
            AttachmentType<?> attachmentType,
            Method isModelActive,
            Object disableSelfModel,
            Object disableOtherModel,
            Object disableSelfHands,
            Method booleanValueGet) {

        private static final Bindings UNAVAILABLE = new Bindings(null, null, null, null, null, null);

        private static Bindings load() {
            // ModList 在 ModList 初始化之前 get() 会返回 null，这里要防一手。
            ModList modList = ModList.get();
            if (modList == null || !modList.isLoaded("yes_steve_model")) {
                return UNAVAILABLE;
            }
            try {
                Class<?> data = Class.forName("com.elfmcys.yesstevemodel.oOooO0OoOoOO0OoOo0o00OoO");
                AttachmentType<?> attachment = (AttachmentType<?>) data
                        .getDeclaredField("ooO0000oO0o0o0o000Oooo0O").get(null);
                Method active = data.getMethod("OO0o0OOoOOO00OoOoOo0oOOO");
                Class<?> config = Class.forName("com.elfmcys.yesstevemodel.oO0oo0oooOo0O0ooooOOooOo");
                Object selfModel = config.getDeclaredField("O0OOoooOOOO0oo0o0OoO0oO0").get(null);
                Object otherModel = config.getDeclaredField("oOO0ooO00OOooOO0oOo0oO0O").get(null);
                Object selfHands = config.getDeclaredField("Oo0OoOO000OooOoooOoo0ooO").get(null);
                Method booleanGet = selfModel.getClass().getMethod("get");
                return new Bindings(attachment, active, selfModel, otherModel, selfHands, booleanGet);
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.warn("YSM NeoForge 兼容签名不可用，已回退原版/MMD 路由", exception);
                return UNAVAILABLE;
            }
        }

        private boolean isModelActive(LivingEntity entity) {
            if (attachmentType == null || isModelActive == null) {
                return false;
            }
            try {
                Object data = entity.getData(attachmentType);
                return data != null && Boolean.TRUE.equals(isModelActive.invoke(data));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.debug("读取 YSM NeoForge 模型状态失败", exception);
                return false;
            }
        }

        private boolean booleanValue(Object value) {
            if (value == null || booleanValueGet == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(booleanValueGet.invoke(value));
            } catch (ReflectiveOperationException exception) {
                LOGGER.debug("读取 YSM NeoForge 配置失败", exception);
                return false;
            }
        }
    }
}
