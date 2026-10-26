package com.shiroha.mmdskin.fabric;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.shiroha.mmdskin.config.GameDirectoryProvider;
import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterCommon;

import net.fabricmc.api.ModInitializer;

public class MmdSkinFabric implements ModInitializer {
    public static final Logger logger = LogManager.getLogger();
    @Override
    public void onInitialize() {
        // 游戏目录注入：房主（客户端兼服务端）与服主（专用服务器）都从这里拿，
        // 专用服务器上 Minecraft.getInstance() 是 null，不能用客户端那套取法。
        GameDirectoryProvider.setProvider(
                () -> net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile());
        MmdSkinRegisterCommon.Register();
    }
}
