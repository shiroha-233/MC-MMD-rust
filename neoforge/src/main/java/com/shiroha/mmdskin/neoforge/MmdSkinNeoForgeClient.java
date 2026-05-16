/** 文件职责：完成 NeoForge 客户端配置、运行时与平台事件入口初始化。 */
package com.shiroha.mmdskin.neoforge;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.neoforge.config.MmdSkinConfig;
import com.shiroha.mmdskin.neoforge.maid.MaidRenderEventHandler;
import com.shiroha.mmdskin.neoforge.maid.MaidSyncEventHandler;
import com.shiroha.mmdskin.neoforge.register.MmdSkinRegisterClient;
import com.shiroha.mmdskin.renderer.runtime.model.opengl.MMDModelOpenGL;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(value = Dist.CLIENT, modid = MmdSkin.MOD_ID) // TODO_1.21.11: API 变更 EventBusSubscriber 不再有 bus 选项
public final class MmdSkinNeoForgeClient {
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MmdSkinRegisterClient.onRegisterKeyMappings(event);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        MmdSkinRegisterClient.onRegisterEntityRenderers(event);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        MmdSkinConfig.init();
        MmdSkinClient.initClient();
        MmdSkinRegisterClient.Register();
        MMDModelOpenGL.isMMDShaderEnabled = ConfigManager.isMMDShaderEnabled();
        NeoForge.EVENT_BUS.register(new MaidRenderEventHandler());
        NeoForge.EVENT_BUS.register(MaidSyncEventHandler.class);
    }
}
