package com.shiroha.mmdskin.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class YsmCompat {
    private static boolean ysmChecked = false;
    private static boolean ysmPresent = false;

    private static Method ysmIsAvailableMethod = null;
    private static final Map<Class<?>, Method> isYsmModelMethodCache = new ConcurrentHashMap<>();

    private static Capability<?> ysmPlayerModelCapability = null;
    private static Capability<?> ysmEntityModelCapability = null;
    private static Capability<?> ysmClientLazyCapability = null;

    private static volatile Method ysmModelIdGetter = null;
    private static volatile Method ysmModelDisabledGetter = null;
    private static volatile Method ysmClientModelActiveGetter = null;
    private static volatile Method ysmClientModelObjectGetter = null;
    private static volatile Method ysmEntityCanRenderFlag1Getter = null;
    private static volatile Method ysmEntityCanRenderFlag2Getter = null;

    private static Object disableSelfModelValue = null;
    private static Object disableOtherModelValue = null;
    private static Object disableSelfHandsValue = null;
    private static Method booleanValueGetMethod = null;

    public static boolean isYsmActive(LivingEntity entity) {
        if (!isYsmModelActive(entity)) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean isLocal = mc.player != null && mc.player.getUUID().equals(entity.getUUID());
        return isLocal ? !isDisableSelfModel() : !isDisableOtherModel();
    }

    public static boolean isYsmModelActive(LivingEntity entity) {
        ensureInit();
        if (!ysmPresent || !isYsmAvailable()) return false;

        if (ysmClientLazyCapability != null) {
            Object data = getCapData(entity, ysmClientLazyCapability);
            if (data != null) {
                if (invokeGetter(data, "Oooo0O0oo000OoOoO0ooooOo", () -> ysmClientModelObjectGetter, v -> ysmClientModelObjectGetter = v) != null) return true;
                if (Boolean.TRUE.equals(invokeGetter(data, "o00oo0OO0OOOOO0OOo0oOOoO", () -> ysmClientModelActiveGetter, v -> ysmClientModelActiveGetter = v))) return true;
            }
        }

        if (ysmEntityModelCapability != null) {
            Object data = getCapData(entity, ysmEntityModelCapability);
            if (data != null) {
                Object f1 = invokeGetter(data, "oOoo0oO000Oo0OOoO0O00O0o", () -> ysmEntityCanRenderFlag1Getter, v -> ysmEntityCanRenderFlag1Getter = v);
                Object f2 = invokeGetter(data, "Oo000oOoO000O0OOO0O0oOOo", () -> ysmEntityCanRenderFlag2Getter, v -> ysmEntityCanRenderFlag2Getter = v);
                if (Boolean.TRUE.equals(f1) && Boolean.TRUE.equals(f2)) return true;
            }
        }

        try {
            Method m = isYsmModelMethodCache.computeIfAbsent(entity.getClass(), cls -> {
                try { return cls.getMethod("isYsmModel"); } catch (NoSuchMethodException e) { return null; }
            });
            if (m != null && Boolean.TRUE.equals(m.invoke(entity))) return true;
        } catch (Exception e) {}

        if (ysmPlayerModelCapability != null) {
            Object data = getCapData(entity, ysmPlayerModelCapability);
            if (data != null) {
                Object modelId = invokeGetter(data, "Oooo0O0oo000OoOoO0ooooOo", () -> ysmModelIdGetter, v -> ysmModelIdGetter = v);
                Object disabled = invokeGetter(data, "ooOo0O0oooO00OoO0ooOO0O0", () -> ysmModelDisabledGetter, v -> ysmModelDisabledGetter = v);
                if (modelId instanceof String && !((String) modelId).isEmpty() && !Boolean.TRUE.equals(disabled)) return true;
            }
        }

        return false;
    }

    private static void ensureInit() {
        if (ysmChecked) return;
        ysmChecked = true;
        ysmPresent = ModList.get().isLoaded("yes_steve_model");
        if (!ysmPresent) return;

        try {
            Class<?> ysmMainClass = Class.forName("com.elfmcys.yesstevemodel.YesSteveModel");
            ysmIsAvailableMethod = ysmMainClass.getMethod("isAvailable");

            Class<?> ysmConfigClass = Class.forName("com.elfmcys.yesstevemodel.o00oO00OOO00OOOOo00Oo00O");
            disableSelfModelValue = getStaticFieldValue(ysmConfigClass, "Ooo0oooO0oOOo0o0o0oO0O0o");
            disableOtherModelValue = getStaticFieldValue(ysmConfigClass, "oo00OO0o0oo00oO0O00ooooo");
            disableSelfHandsValue = getStaticFieldValue(ysmConfigClass, "o0OoO0O0OoO0oOoOO0oOooO0");

            if (disableSelfModelValue != null) booleanValueGetMethod = disableSelfModelValue.getClass().getMethod("get");

            ysmPlayerModelCapability = resolveCap("com.elfmcys.yesstevemodel.o00oo0OO0OOOOO0OOo0oOOoO");
            ysmClientLazyCapability = resolveCap("com.elfmcys.yesstevemodel.OoOOOoOOo0O00OO0OOOo0OOo");
            ysmEntityModelCapability = resolveCap("com.elfmcys.yesstevemodel.oO0oOoOo0O0OooOOo0O00oOo");
        } catch (Exception e) {
            ysmPresent = false;
        }
    }

    private static Capability<?> resolveCap(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object cap = getStaticFieldValue(clazz, "Ooo0oOo0oo0ooO0OO0oo000o");
            return cap instanceof Capability ? (Capability<?>) cap : null;
        } catch (Throwable e) { return null; }
    }

    private static Object getStaticFieldValue(Class<?> clazz, String fieldName) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(null);
    }

    private static boolean isYsmAvailable() {
        ensureInit();
        if (!ysmPresent || ysmIsAvailableMethod == null) return false;
        try { return (Boolean) ysmIsAvailableMethod.invoke(null); } catch (Exception e) { return false; }
    }

    private static Object getCapData(LivingEntity entity, Capability<?> cap) {
        try {
            LazyOptional<?> opt = entity.getCapability(cap, null);
            return opt.isPresent() ? opt.orElse(null) : null;
        } catch (Throwable e) { return null; }
    }

    private static Object invokeGetter(Object target, String name, java.util.function.Supplier<Method> getter, java.util.function.Consumer<Method> setter) {
        try {
            Method m = getter.get();
            if (m == null) {
                m = target.getClass().getMethod(name);
                setter.accept(m);
            }
            return m.invoke(target);
        } catch (Throwable e) { return null; }
    }

    public static boolean isDisableSelfModel() { return getBooleanValue(disableSelfModelValue); }
    public static boolean isDisableOtherModel() { return getBooleanValue(disableOtherModelValue); }
    public static boolean isDisableSelfHands() { return getBooleanValue(disableSelfHandsValue); }

    private static boolean getBooleanValue(Object valueObj) {
        ensureInit();
        if (ysmPresent && valueObj != null && booleanValueGetMethod != null) {
            try { return (Boolean) booleanValueGetMethod.invoke(valueObj); } catch (Exception e) {}
        }
        return false;
    }
}
