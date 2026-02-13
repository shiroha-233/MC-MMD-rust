package com.shiroha.mmdskin.fabric.register;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.fabric.config.ModConfigScreen;
import com.shiroha.mmdskin.mixin.fabric.KeyMappingAccessor;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.maid.MaidActionNetworkHandler;
import com.shiroha.mmdskin.maid.MaidModelNetworkHandler;
import com.shiroha.mmdskin.renderer.render.MmdSkinRenderFactory;
import com.shiroha.mmdskin.renderer.render.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.MorphWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.camera.StageAudioPlayer;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.ui.wheel.MaidConfigWheelScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;

import java.io.File;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric 客户端注册
 * 负责按键绑定、网络通信和实体渲染器注册
 */
@Environment(EnvType.CLIENT)
public class MmdSkinRegisterClient {
    static final Logger logger = LogManager.getLogger();
    
    // 主配置轮盘按键 (Alt，可自定义)
    static KeyMapping keyConfigWheel = new KeyMapping("key.mmdskin.config_wheel", 
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.mmdskin");
    
    // 女仆配置轮盘按键 (B，对着女仆时生效)
    static KeyMapping keyMaidConfigWheel = new KeyMapping("key.mmdskin.maid_config_wheel",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.mmdskin");
    
    // 追踪按键状态
    private static boolean configWheelKeyWasDown = false;
    private static boolean maidConfigWheelKeyWasDown = false;

    public static void Register() {
        Minecraft MCinstance = Minecraft.getInstance();
        
        // 注入按键获取逻辑
        KeyMappingUtil.setBoundKeyGetter(k -> {
            if (k instanceof KeyMappingAccessor accessor) {
                return accessor.mmd$getBoundKey();
            }
            return InputConstants.UNKNOWN;
        });

        // 注册按键
        KeyBindingHelper.registerKeyBinding(keyConfigWheel);
        if (MaidCompatMixinPlugin.isMaidModLoaded()) {
            KeyBindingHelper.registerKeyBinding(keyMaidConfigWheel);
        }
        
        // 设置模组设置界面工厂
        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        
        // 注册动作轮盘网络发送器
        ActionWheelNetworkHandler.setNetworkSender(animId -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                // 使用字符串传输动画ID，支持中文文件名
                logger.info("发送动作到服务器: " + animId);
                MmdSkinNetworkPack.sendToServer(1, player.getUUID(), animId);
            }
        });
        
        // 注册表情轮盘网络发送器
        MorphWheelNetworkHandler.setNetworkSender(morphName -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                logger.info("发送表情到服务器: " + morphName);
                // 使用 opCode 6 表示表情变更
                MmdSkinNetworkPack.sendToServer(6, player.getUUID(), morphName);
            }
        });
        
        // 注册模型选择网络发送器（旧接口，保留向后兼容）
        com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler.setNetworkSender(modelName -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                // 使用 opCode 3 表示模型变更（字符串参数）
                MmdSkinNetworkPack.sendToServer(3, player.getUUID(), modelName);
            }
        });
        
        // 注册模型同步管理器的网络广播器（新接口，用于联机同步）
        PlayerModelSyncManager.setNetworkBroadcaster((playerUUID, modelName) -> {
            // 使用 opCode 3 发送模型选择到服务器
            MmdSkinNetworkPack.sendToServer(3, playerUUID, modelName);
        });
        
        // 注册女仆模型选择网络发送器
        MaidModelNetworkHandler.setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(4, player.getUUID(), entityId, modelName);
            }
        });
        
        // 注册女仆动作网络发送器
        MaidActionNetworkHandler.setNetworkSender((entityId, animId) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                logger.info("发送女仆动作到服务器: 实体={}, 动画={}", entityId, animId);
                MmdSkinNetworkPack.sendToServer(5, player.getUUID(), entityId, animId);
            }
        });
        
        // 注册舞台模式网络发送器
        StageNetworkHandler.setStageStartSender(stageData -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(7, player.getUUID(), stageData);
            }
        });
        StageNetworkHandler.setStageEndSender(() -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(8, player.getUUID(), "");
            }
        });
        
        // 主配置轮盘按键事件（按住打开，松开选择）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (MCinstance.player == null) return;

            // 远程舞台音频距离衰减（每秒更新一次）
            StageAudioPlayer.tickRemoteAttenuation();
            
            // 舞台模式死亡/复活检测
            if (MCinstance.player != null) {
                if (!MCinstance.player.isAlive()) {
                    MMDCameraController controller = MMDCameraController.getInstance();
                    if (controller.isInStageMode()) {
                        logger.info("检测到玩家死亡，正在退出舞台模式以防止视角锁定");
                        controller.exitStageMode();
                    }
                }
            }

            // 主配置轮盘按键处理
            if (MCinstance.screen == null || MCinstance.screen instanceof ConfigWheelScreen) {
                boolean keyDown = keyConfigWheel.isDown();
                if (keyDown && !configWheelKeyWasDown) {
                    MCinstance.setScreen(new ConfigWheelScreen(keyConfigWheel));
                }
                configWheelKeyWasDown = keyDown;
            } else {
                configWheelKeyWasDown = false;
            }
            
            // 女仆配置轮盘按键处理
            if (MaidCompatMixinPlugin.isMaidModLoaded()) {
                if (MCinstance.screen == null || MCinstance.screen instanceof MaidConfigWheelScreen) {
                    boolean keyDown = keyMaidConfigWheel.isDown();
                    if (keyDown && !maidConfigWheelKeyWasDown) {
                        tryOpenMaidConfigWheel(MCinstance);
                    }
                    maidConfigWheelKeyWasDown = keyDown;
                } else {
                    maidConfigWheelKeyWasDown = false;
                }
            }
        });

        // 注册实体渲染器
        File[] modelDirs = new File(MCinstance.gameDirectory, "3d-skin").listFiles();
        if (modelDirs != null) {
            for (File i : modelDirs) {
                if (!i.getName().startsWith("EntityPlayer") && !i.getName().equals("DefaultAnim") && !i.getName().equals("Shader")) {
                    String mcEntityName = i.getName().replace('.', ':');
                    if (EntityType.byString(mcEntityName).isPresent())
                        EntityRendererRegistry.register(EntityType.byString(mcEntityName).get(), new MmdSkinRenderFactory<>(mcEntityName));
                    else
                        logger.warn(mcEntityName + " 实体不存在，跳过渲染注册");
                }
            }
        }

        // 注册网络接收器
        ClientPlayNetworking.registerGlobalReceiver(MmdSkinRegisterCommon.SKIN_S2C, (client, handler, buf, responseSender) -> {
            // 复制缓冲区数据，因为需要在主线程处理
            FriendlyByteBuf copiedBuf = new FriendlyByteBuf(buf.copy());
            client.execute(() -> {
                MmdSkinNetworkPack.DoInClient(copiedBuf);
                copiedBuf.release();
            });
        });
        
        // 注册玩家加入服务器事件（广播自己的模型选择）
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                LocalPlayer player = client.player;
                if (player != null) {
                    // 延迟一点广播，确保网络连接稳定
                    String selectedModel = com.shiroha.mmdskin.ui.config.ModelSelectorConfig.getInstance()
                        .getPlayerModel(player.getName().getString());
                    if (selectedModel != null && !selectedModel.isEmpty() && 
                        !selectedModel.equals(com.shiroha.mmdskin.config.UIConstants.DEFAULT_MODEL_NAME)) {
                        logger.info("玩家加入服务器，广播模型选择: {}", selectedModel);
                        PlayerModelSyncManager.broadcastLocalModelSelection(player.getUUID(), selectedModel);
                    }
                    // 请求所有玩家的模型信息
                    MmdSkinNetworkPack.sendToServer(10, player.getUUID(), "");
                }
            });
        });
        
        // 注册玩家断开连接事件（清理远程玩家缓存 + 舞台模式）
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MMDCameraController.getInstance().exitStageMode();
            PlayerModelSyncManager.onDisconnect();
            MmdSkinRendererPlayerHelper.onDisconnect();
        });
        
        logger.info("MMD Skin 客户端注册完成");
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
            logger.info("打开女仆配置轮盘: {} (ID: {})", maidName, target.getId());
        }
    }
}
