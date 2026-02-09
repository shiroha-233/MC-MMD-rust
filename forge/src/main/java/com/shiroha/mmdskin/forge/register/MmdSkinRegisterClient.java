package com.shiroha.mmdskin.forge.register;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.forge.config.ModConfigScreen;
import com.shiroha.mmdskin.forge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.maid.MaidActionNetworkHandler;
import com.shiroha.mmdskin.maid.MaidModelNetworkHandler;
import com.shiroha.mmdskin.renderer.render.MmdSkinRenderFactory;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.ui.wheel.MaidConfigWheelScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.fml.common.Mod;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 客户端注册
 * 负责按键绑定、网络通信和实体渲染器注册
 * 
 * 使用两个事件总线：
 * - MOD 事件总线：按键注册、实体渲染器注册
 * - Forge 事件总线：按键输入处理、客户端 Tick
 */
public class MmdSkinRegisterClient {
    static final Logger logger = LogManager.getLogger();
    
    // 主配置轮盘按键 (Alt，可自定义)
    public static final KeyMapping keyConfigWheel = new KeyMapping(
        "key.mmdskin.config_wheel", 
        KeyConflictContext.IN_GAME, 
        InputConstants.Type.KEYSYM, 
        GLFW.GLFW_KEY_LEFT_ALT, 
        "key.categories.mmdskin"
    );
    
    // 女仆配置轮盘按键 (B，对着女仆时生效)
    public static final KeyMapping keyMaidConfigWheel = new KeyMapping(
        "key.mmdskin.maid_config_wheel", 
        KeyConflictContext.IN_GAME, 
        InputConstants.Type.KEYSYM, 
        GLFW.GLFW_KEY_B, 
        "key.categories.mmdskin"
    );
    
    // 追踪按键状态
    private static boolean configWheelKeyWasDown = false;
    private static boolean maidConfigWheelKeyWasDown = false;
    
    // 是否已注册网络发送器
    private static boolean networkSendersRegistered = false;

    /**
     * 主注册方法 - 在客户端初始化时调用
     * 注册事件监听器到两个事件总线
     */
    public static void Register() {
        // 注册到 Forge 事件总线（按键输入、客户端 Tick）
        // 注意：按键注册和实体渲染器注册已由 MmdSkinForgeClient 中的 @SubscribeEvent 处理
        //（RegisterKeyMappingsEvent 和 EntityRenderersEvent 在 FMLClientSetupEvent 之前触发）
        MinecraftForge.EVENT_BUS.register(ForgeEventHandler.class);
        
        // 设置模组设置界面工厂
        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        
        // 注册网络发送器
        registerNetworkSenders();
        
        logger.info("MMD Skin Forge 客户端注册完成");
    }
    
    /**
     * 注册网络发送器（与 Fabric 一致）
     */
    private static void registerNetworkSenders() {
        if (networkSendersRegistered) return;
        networkSendersRegistered = true;
        
        Minecraft MCinstance = Minecraft.getInstance();
        
        // 注册动作轮盘网络发送器
        ActionWheelNetworkHandler.setNetworkSender(animId -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                logger.info("发送动作到服务器: " + animId);
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(1, player.getUUID(), animId));
            }
        });
        
        // 注册模型选择网络发送器（旧接口，保留向后兼容）
        com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler.setNetworkSender(modelName -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                // 使用 opCode 3 表示模型变更（字符串参数）
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(3, player.getUUID(), modelName));
            }
        });
        
        // 注册模型同步管理器的网络广播器（新接口，用于联机同步）
        PlayerModelSyncManager.setNetworkBroadcaster((playerUUID, modelName) -> {
            // 使用 opCode 3 发送模型选择到服务器
            MmdSkinRegisterCommon.channel.sendToServer(
                new MmdSkinNetworkPack(3, playerUUID, modelName));
        });
        
        // 注册女仆模型选择网络发送器
        MaidModelNetworkHandler.setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(4, player.getUUID(), entityId, modelName));
            }
        });
        
        // 注册女仆动作网络发送器
        MaidActionNetworkHandler.setNetworkSender((entityId, animId) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                logger.info("发送女仆动作到服务器: 实体={}, 动画={}", entityId, animId);
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(5, player.getUUID(), entityId, animId));
            }
        });
    }
    
    /**
     * MOD 事件：注册按键映射
     */
    @OnlyIn(Dist.CLIENT)
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(keyConfigWheel);
        event.register(keyMaidConfigWheel);
        logger.info("按键映射注册完成");
    }
    
    /**
     * MOD 事件：注册实体渲染器
     */
    @OnlyIn(Dist.CLIENT)
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        Minecraft MCinstance = Minecraft.getInstance();
        File[] modelDirs = new File(MCinstance.gameDirectory, "3d-skin").listFiles();
        
        if (modelDirs != null) {
            for (File i : modelDirs) {
                String name = i.getName();
                if (!name.startsWith("EntityPlayer") && 
                    !name.equals("DefaultAnim") && 
                    !name.equals("CustomAnim") &&
                    !name.equals("Shader")) {
                    
                    String mcEntityName = name.replace('.', ':');
                    if (EntityType.byString(mcEntityName).isPresent()) {
                        event.registerEntityRenderer(
                            EntityType.byString(mcEntityName).get(), 
                            new MmdSkinRenderFactory<>(mcEntityName));
                        logger.info("{} 实体渲染器注册成功", mcEntityName);
                    } else {
                        logger.warn("{} 实体不存在，跳过渲染注册", mcEntityName);
                    }
                }
            }
        }
    }
    
    /**
     * Forge 事件处理器（注册到 Forge 事件总线）
     */
    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MmdSkin.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEventHandler {
        
        /**
         * 客户端 Tick 事件 - 处理按键状态（与 Fabric 的 ClientTickEvents 对应）
         */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            // 主配置轮盘按键处理
            if (mc.screen == null || mc.screen instanceof ConfigWheelScreen) {
                boolean keyDown = keyConfigWheel.isDown();
                if (keyDown && !configWheelKeyWasDown) {
                    int keyCode = keyConfigWheel.getKey().getValue();
                    mc.setScreen(new ConfigWheelScreen(keyCode));
                }
                configWheelKeyWasDown = keyDown;
            } else {
                configWheelKeyWasDown = false;
            }
            
            // 女仆配置轮盘按键处理
            if (mc.screen == null || mc.screen instanceof MaidConfigWheelScreen) {
                boolean keyDown = keyMaidConfigWheel.isDown();
                if (keyDown && !maidConfigWheelKeyWasDown) {
                    tryOpenMaidConfigWheel(mc);
                }
                maidConfigWheelKeyWasDown = keyDown;
            } else {
                maidConfigWheelKeyWasDown = false;
            }
        }
        
        /**
         * 尝试打开女仆配置轮盘
         */
        private static void tryOpenMaidConfigWheel(Minecraft mc) {
            HitResult hitResult = mc.hitResult;
            if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
                return;
            }
            
            EntityHitResult entityHit = (EntityHitResult) hitResult;
            Entity target = entityHit.getEntity();
            
            String className = target.getClass().getName();
            if (className.contains("EntityMaid") || className.contains("touhoulittlemaid")) {
                String maidName = target.getName().getString();
                int keyCode = keyMaidConfigWheel.getKey().getValue();
                mc.setScreen(new MaidConfigWheelScreen(target.getUUID(), target.getId(), maidName, keyCode));
                logger.info("打开女仆配置轮盘: {} (ID: {})", maidName, target.getId());
            }
        }
        
        /**
         * 玩家加入服务器事件（广播自己的模型选择）
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String selectedModel = com.shiroha.mmdskin.ui.config.ModelSelectorConfig.getInstance()
                    .getPlayerModel(mc.player.getName().getString());
                if (selectedModel != null && !selectedModel.isEmpty() && 
                    !selectedModel.equals(com.shiroha.mmdskin.config.UIConstants.DEFAULT_MODEL_NAME)) {
                    logger.info("玩家加入服务器，广播模型选择: {}", selectedModel);
                    PlayerModelSyncManager.broadcastLocalModelSelection(mc.player.getUUID(), selectedModel);
                }
            }
        }
        
        /**
         * 玩家断开连接事件（清理远程玩家缓存）
         */
        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MMDCameraController.getInstance().exitStageMode();
            PlayerModelSyncManager.onDisconnect();
        }
    }
}
