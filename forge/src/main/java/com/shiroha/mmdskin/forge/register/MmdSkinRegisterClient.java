package com.shiroha.mmdskin.forge.register;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.forge.config.ModConfigScreen;
import com.shiroha.mmdskin.forge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.maid.MaidActionNetworkHandler;
import com.shiroha.mmdskin.maid.MaidModelNetworkHandler;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.renderer.integration.entity.MmdSkinRenderFactory;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.MorphWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
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

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 客户端注册
 * 负责按键绑定、网络通信和实体渲染器注册
 */
public class MmdSkinRegisterClient {
    static final Logger logger = LogManager.getLogger();

    public static final KeyMapping keyConfigWheel = new KeyMapping(
        "key.mmdskin.config_wheel",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.mmdskin"
    );

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

    private static boolean configWheelKeyWasDown = false;
    private static boolean maidConfigWheelKeyWasDown = false;

    private static boolean networkSendersRegistered = false;

    public static void Register() {
        KeyMappingUtil.setBoundKeyGetter(k -> k.getKey());
        MinecraftForge.EVENT_BUS.register(ForgeEventHandler.class);
        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        registerNetworkSenders();
    }

    private static void registerNetworkSenders() {
        if (networkSendersRegistered) return;
        networkSendersRegistered = true;

        Minecraft MCinstance = Minecraft.getInstance();

        ActionWheelNetworkHandler.getInstance().setNetworkSender(animId -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.CUSTOM_ANIM, player.getUUID(), animId));
            }
        });

        ActionWheelNetworkHandler.getInstance().setAnimStopSender(() -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.RESET_PHYSICS, player.getUUID(), 0));
            }
        });

        MorphWheelNetworkHandler.getInstance().setNetworkSender(morphName -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MORPH_SYNC, player.getUUID(), morphName));
            }
        });

        com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler.getInstance().setNetworkSender(modelName -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MODEL_SELECT, player.getUUID(), modelName));
            }
        });

        PlayerModelSyncManager.setNetworkBroadcaster((playerUUID, modelName) -> {
            MmdSkinRegisterCommon.channel.sendToServer(
                new MmdSkinNetworkPack(NetworkOpCode.MODEL_SELECT, playerUUID, modelName));
        });

        MaidModelNetworkHandler.getInstance().setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MAID_MODEL, player.getUUID(), entityId, modelName));
            }
        });

        MaidActionNetworkHandler.getInstance().setNetworkSender((entityId, animId) -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MAID_ACTION, player.getUUID(), entityId, animId));
            }
        });

        StageNetworkHandler.setStageMultiSender(data -> {
            LocalPlayer player = MCinstance.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.STAGE_MULTI, player.getUUID(), data));
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(keyConfigWheel);
        event.register(keyMaidConfigWheel);
        for (KeyMapping keyQuickModel : keyQuickModels) {
            event.register(keyQuickModel);
        }
    }
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

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MmdSkin.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEventHandler {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            MMDModelManager.tick();

            StageAnimSyncHelper.tickPending();

            if (mc.screen == null || mc.screen instanceof ConfigWheelScreen) {
                boolean keyDown = keyConfigWheel.isDown();
                if (keyDown && !configWheelKeyWasDown) {
                    mc.setScreen(new ConfigWheelScreen(keyConfigWheel));
                }
                configWheelKeyWasDown = keyDown;
            } else {
                configWheelKeyWasDown = false;
            }

            if (mc.screen == null) {
                for (int i = 0; i < keyQuickModels.length; i++) {
                    while (keyQuickModels[i].consumeClick()) {
                        QuickModelSwitcher.switchToSlot(i);
                    }
                }
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
        }

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
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.REQUEST_ALL_MODELS, mc.player.getUUID(), ""));
            }
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MMDCameraController.getInstance().exitStageMode();
            PlayerModelSyncManager.onDisconnect();
            MmdSkinRendererPlayerHelper.onDisconnect();
            StageSessionService.getInstance().onDisconnect();
        }

        @SubscribeEvent
        public static void onPlayerDeath(LivingDeathEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && event.getEntity().getUUID().equals(mc.player.getUUID())) {
                MMDCameraController controller = MMDCameraController.getInstance();
                if (controller.isInStageMode()) {
                    controller.exitStageMode();
                }
            }
        }

        @SubscribeEvent
        public static void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
            PerformanceHud.render(event.getGuiGraphics());
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && event.getEntity().getUUID().equals(mc.player.getUUID())) {
                MMDCameraController controller = MMDCameraController.getInstance();
                if (controller.isInStageMode()) {
                    controller.exitStageMode();
                }
            }
        }
    }
}

