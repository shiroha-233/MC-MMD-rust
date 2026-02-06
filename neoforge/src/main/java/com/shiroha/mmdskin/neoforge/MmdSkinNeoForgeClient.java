package com.shiroha.mmdskin.neoforge;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.neoforge.config.MmdSkinConfig;
import com.shiroha.mmdskin.neoforge.maid.MaidRenderEventHandler;
import com.shiroha.mmdskin.neoforge.register.MmdSkinRegisterClient;
import com.shiroha.mmdskin.renderer.model.MMDModelOpenGL;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge 客户端初始化
 * 
 * 重构说明：
 * - 初始化统一配置管理器
 * - 使用 ConfigManager 访问配置
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = MmdSkin.MOD_ID)
public class MmdSkinNeoForgeClient {
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        MmdSkinClient.logger.info("MMD Skin NeoForge 客户端初始化开始...");
        
        // 初始化配置系统
        MmdSkinConfig.init();
        
        // 初始化客户端
        MmdSkinClient.initClient();
        
        // 注册客户端内容
        MmdSkinRegisterClient.Register();
        
        // 配置 MMD Shader
        MMDModelOpenGL.isMMDShaderEnabled = com.shiroha.mmdskin.config.ConfigManager.isMMDShaderEnabled();
        
        // 注册女仆渲染事件处理器（TouhouLittleMaid 联动）
        NeoForge.EVENT_BUS.register(new MaidRenderEventHandler());
        
        MmdSkinClient.logger.info("MMD Skin NeoForge 客户端初始化成功");
    }
}
