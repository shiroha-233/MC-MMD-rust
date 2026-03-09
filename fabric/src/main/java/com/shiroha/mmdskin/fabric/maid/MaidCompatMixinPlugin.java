package com.shiroha.mmdskin.fabric.maid;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 女仆模组兼容工具类
 *
 * 提供 TouhouLittleMaid (Orihime) 模组加载状态检测。
 * 用于在按键注册、网络发送器等处判断是否启用女仆相关功能。
 */
public class MaidCompatMixinPlugin {

    private static final boolean MAID_MOD_LOADED = FabricLoader.getInstance().isModLoaded("touhou_little_maid");

    public static boolean isMaidModLoaded() {
        return MAID_MOD_LOADED;
    }
}

