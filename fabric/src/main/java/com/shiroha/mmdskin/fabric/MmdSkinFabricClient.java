/** 文件职责：完成 Fabric 客户端配置、运行时与平台入口初始化。 */
package com.shiroha.mmdskin.fabric;

import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.fabric.config.MmdSkinConfig;
import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterClient;
import com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGL;
import net.fabricmc.api.ClientModInitializer;

public final class MmdSkinFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MmdSkinConfig.init();
        MmdSkinClient.initClient();
        MmdSkinRegisterClient.Register();
        MMDModelOpenGL.isMMDShaderEnabled = ConfigManager.isMMDShaderEnabled();
    }
}
