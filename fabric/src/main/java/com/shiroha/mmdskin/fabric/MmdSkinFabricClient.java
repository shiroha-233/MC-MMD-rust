package com.shiroha.mmdskin.fabric;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.fabric.config.MmdSkinConfig;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterClient;
import com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGL;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric 客户端初始化
 */
public class MmdSkinFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MmdSkinConfig.init();
        MmdSkinClient.initClient();
        MmdSkinRegisterClient.Register();
        MMDModelOpenGL.isMMDShaderEnabled = com.shiroha.mmdskin.config.ConfigManager.isMMDShaderEnabled();
    }
}
