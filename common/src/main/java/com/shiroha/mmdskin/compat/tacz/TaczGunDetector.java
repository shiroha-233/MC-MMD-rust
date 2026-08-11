package com.shiroha.mmdskin.compat.tacz;

import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 文件职责：在不建立 TaCZ 编译期依赖的前提下识别其枪械物品。 */
public final class TaczGunDetector {
    private static final String TACZ_GUN_INTERFACE = "com.tacz.guns.api.item.IGun";
    private static final Map<Class<?>, Boolean> GUN_CLASS_CACHE = new ConcurrentHashMap<>();

    private TaczGunDetector() {
    }

    public static boolean isGun(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        return GUN_CLASS_CACHE.computeIfAbsent(
                itemStack.getItem().getClass(),
                itemClass -> implementsInterfaceNamed(itemClass, TACZ_GUN_INTERFACE));
    }

    static boolean implementsInterfaceNamed(Class<?> type, String interfaceName) {
        if (type == null || interfaceName == null || interfaceName.isBlank()) {
            return false;
        }
        for (Class<?> implementedInterface : type.getInterfaces()) {
            if (interfaceName.equals(implementedInterface.getName())
                    || implementsInterfaceNamed(implementedInterface, interfaceName)) {
                return true;
            }
        }
        return implementsInterfaceNamed(type.getSuperclass(), interfaceName);
    }
}
