package com.shiroha.mmdskin.neoforge.register;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.bonesync.BoneSyncManager;
import com.shiroha.mmdskin.bonesync.BoneSyncNetworkHandler;
import com.shiroha.mmdskin.neoforge.config.ModConfigScreen;
import com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.maid.MaidActionNetworkHandler;
import com.shiroha.mmdskin.maid.MaidModelNetworkHandler;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.renderer.render.MmdSkinRenderFactory;
import com.shiroha.mmdskin.renderer.render.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.MorphWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.camera.StageAudioPlayer;
import com.shiroha.mmdskin.ui.QuickModelSwitcher;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.ui.wheel.MaidConfigWheelScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge 客户端注册
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
    
    public static final KeyMapping[] keyQuickModels = new KeyMapping[4];
    static {
        for (int i = 0; i < 4; i++) {
            keyQuickModels[i] = new KeyMapping(
                "key.mmdskin.quick_model_" + (i + 1),
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                "key.categories.mmdskin"
            );
        }
    }
    
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
        KeyMappingUtil.setBoundKeyGetter(k -> k.getKey());
        NeoForge.EVENT_BUS.register(NeoForgeEventHandler.class);
        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        registerNetworkSenders();
    }
    
    /**
     * 注册网络发送器（与 Fabric 一致）
     */
    private static void registerNetworkSenders() {
        if (networkSendersRegistered) return;
        networkSendersRegistered = true;
        
        Minecraft MCinstance = Minecraft.getInstance();
        
        ActionWheelNetworkHandler.setNetworkSender(animId -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.CUSTOM_ANIM, player.getUUID(), animId));
            }
        });
        
        ActionWheelNetworkHandler.setAnimStopSender(() -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withArg(NetworkOpCode.RESET_PHYSICS, player.getUUID(), 0));
            }
        });
        
        MorphWheelNetworkHandler.setNetworkSender(morphName -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.MORPH_SYNC, player.getUUID(), morphName));
            }
        });
        
        com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler.setNetworkSender(modelName -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.MODEL_SELECT, player.getUUID(), modelName));
            }
        });
        
        PlayerModelSyncManager.setNetworkBroadcaster((playerUUID, modelName) -> {
            PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.MODEL_SELECT, playerUUID, modelName));
        });
        
        MaidModelNetworkHandler.setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.forMaid(NetworkOpCode.MAID_MODEL, player.getUUID(), entityId, modelName));
            }
        });
        
        MaidActionNetworkHandler.setNetworkSender((entityId, animId) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.forMaid(NetworkOpCode.MAID_ACTION, player.getUUID(), entityId, animId));
            }
        });
        
        StageNetworkHandler.setStageStartSender(stageData -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.STAGE_START, player.getUUID(), stageData));
            }
        });
        StageNetworkHandler.setStageEndSender(() -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.STAGE_END, player.getUUID(), ""));
            }
        });
        
        StageNetworkHandler.setStageMultiSender(data -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.STAGE_MULTI, player.getUUID(), data));
            }
        });
        
        BoneSyncNetworkHandler.setNetworkSender(boneData -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withBinary(NetworkOpCode.BONE_SYNC, player.getUUID(), boneData));
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
        for (KeyMapping keyQuickModel : keyQuickModels) {
            event.register(keyQuickModel);
        }
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
                    } else {
                        logger.warn("{} 实体不存在，跳过渲染注册", mcEntityName);
                    }
                }
            }
        }
    }
    
    /**
     * NeoForge 事件处理器（注册到 NeoForge 事件总线）
     */
    @EventBusSubscriber(value = Dist.CLIENT, modid = MmdSkin.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
    public static class NeoForgeEventHandler {
        
        /**
         * 客户端 Tick 事件 - 处理按键状态
         */
        @SubscribeEvent
        public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            MMDModelManager.tick();
            
            com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper.tickPending();

            StageAudioPlayer.tickRemoteAttenuation();
            
            BoneSyncManager.tickLocal();
            if (mc.screen == null || mc.screen instanceof ConfigWheelScreen) {
                boolean keyDown = keyConfigWheel.isDown();
                if (keyDown && !configWheelKeyWasDown) {
                    mc.setScreen(new ConfigWheelScreen(keyConfigWheel));
                }
                configWheelKeyWasDown = keyDown;
            } else {
                configWheelKeyWasDown = false;
            }
            
            if (mc.screen == null || mc.screen instanceof MaidConfigWheelScreen) {
                boolean keyDown = keyMaidConfigWheel.isDown();
                if (keyDown && !maidConfigWheelKeyWasDown) {
                    tryOpenMaidConfigWheel(mc);
                }
                maidConfigWheelKeyWasDown = keyDown;
            } else {
                maidConfigWheelKeyWasDown = false;
            }

            if (mc.screen == null) {
                for (int i = 0; i < keyQuickModels.length; i++) {
                    while (keyQuickModels[i].consumeClick()) {
                        QuickModelSwitcher.switchToSlot(i);
                    }
                }
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
                mc.setScreen(new MaidConfigWheelScreen(target.getUUID(), target.getId(), maidName, keyMaidConfigWheel));
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
                    PlayerModelSyncManager.broadcastLocalModelSelection(mc.player.getUUID(), selectedModel);
                }
                PacketDistributor.sendToServer(MmdSkinNetworkPack.withAnimId(NetworkOpCode.REQUEST_ALL_MODELS, mc.player.getUUID(), ""));
            }
        }
        
        /**
         * 玩家断开连接事件（清理远程玩家缓存 + 舞台模式）
         */
        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MMDCameraController.getInstance().exitStageMode();
            PlayerModelSyncManager.onDisconnect();
            MmdSkinRendererPlayerHelper.onDisconnect();
            BoneSyncManager.onDisconnect();
            com.shiroha.mmdskin.ui.stage.StageInviteManager.getInstance().onDisconnect();
        }

        /**
         * HUD 渲染事件 - 性能调试 HUD
         */
        @SubscribeEvent
        public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
            com.shiroha.mmdskin.renderer.core.PerformanceHud.render(event.getGuiGraphics());
        }

        /**
         * 玩家死亡事件 - 退出舞台模式
         */
        @SubscribeEvent
        public static void onPlayerDeath(LivingDeathEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && event.getEntity().getUUID().equals(mc.player.getUUID())) {
                MMDCameraController controller = MMDCameraController.getInstance();
                if (controller.isInStageMode()) {
                    logger.info("检测到玩家死亡，正在退出舞台模式以防止视角锁定");
                    controller.exitStageMode();
                }
            }
        }

        /**
         * 玩家复活事件 - 确保退出舞台模式
         */
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && event.getEntity().getUUID().equals(mc.player.getUUID())) {
                MMDCameraController controller = MMDCameraController.getInstance();
                if (controller.isInStageMode()) {
                    logger.info("检测到玩家复活，强制退出舞台模式");
                    controller.exitStageMode();
                }
            }
        }
    }
}
