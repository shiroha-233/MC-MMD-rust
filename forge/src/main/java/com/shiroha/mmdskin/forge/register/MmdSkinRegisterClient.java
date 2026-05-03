package com.shiroha.mmdskin.forge.register;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.forge.config.ModConfigScreen;
import com.shiroha.mmdskin.render.entity.EntityRenderFactory;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import java.io.File;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 客户端注册入口
 * 保留平台入口、键位/渲染注册和事件订阅壳
 */
public class MmdSkinRegisterClient {
    static final Logger logger = LogManager.getLogger();

    private static final ForgeClientNetworkBindings NETWORK_BINDINGS = new ForgeClientNetworkBindings();

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

    private static final ForgeClientRuntimeHooks RUNTIME_HOOKS =
        new ForgeClientRuntimeHooks(keyConfigWheel, keyMaidConfigWheel, keyQuickModels);

    public static void Register() {
        KeyMappingUtil.setBoundKeyGetter(k -> k.getKey());
        MinecraftForge.EVENT_BUS.register(ForgeEventHandler.class);
        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        NETWORK_BINDINGS.register();
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
                            new EntityRenderFactory<>(mcEntityName));
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
            RUNTIME_HOOKS.onClientTick(event);
        }

        @SubscribeEvent
        public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
            RUNTIME_HOOKS.onPlayerLoggedIn(event);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            RUNTIME_HOOKS.onPlayerLoggedOut(event);
        }

        @SubscribeEvent
        public static void onPlayerDeath(LivingDeathEvent event) {
            RUNTIME_HOOKS.onPlayerDeath(event);
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            RUNTIME_HOOKS.onRenderGui(event);
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            RUNTIME_HOOKS.onPlayerRespawn(event);
        }
    }
}
