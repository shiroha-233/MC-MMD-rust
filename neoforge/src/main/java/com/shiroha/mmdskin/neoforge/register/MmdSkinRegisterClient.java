/** 文件职责：注册 NeoForge 客户端按键、事件钩子、网络发送器与实体渲染入口。 */
package com.shiroha.mmdskin.neoforge.register;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.neoforge.config.ModConfigScreen;
import com.shiroha.mmdskin.renderer.integration.entity.MmdSkinRenderFactory;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import java.io.File;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public final class MmdSkinRegisterClient {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final KeyMapping KEY_CONFIG_WHEEL = new KeyMapping(
        "key.mmdskin.config_wheel",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.mmdskin"
    );

    public static final KeyMapping KEY_MAID_CONFIG_WHEEL = new KeyMapping(
        "key.mmdskin.maid_config_wheel",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.mmdskin"
    );

    public static final KeyMapping[] KEY_QUICK_MODELS = new KeyMapping[4];

    private static final NeoForgeClientNetworkBindings NETWORK_BINDINGS = new NeoForgeClientNetworkBindings();
    private static final NeoForgeClientRuntimeHooks RUNTIME_HOOKS =
        new NeoForgeClientRuntimeHooks(KEY_CONFIG_WHEEL, KEY_MAID_CONFIG_WHEEL, KEY_QUICK_MODELS);

    static {
        for (int i = 0; i < KEY_QUICK_MODELS.length; i++) {
            KEY_QUICK_MODELS[i] = new KeyMapping(
                "key.mmdskin.quick_model_" + (i + 1),
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                "key.categories.mmdskin"
            );
        }
    }

    private MmdSkinRegisterClient() {
    }

    public static void Register() {
        KeyMappingUtil.setBoundKeyGetter(KeyMapping::getKey);
        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        NETWORK_BINDINGS.register();
        NeoForge.EVENT_BUS.register(RUNTIME_HOOKS);
    }

    @OnlyIn(Dist.CLIENT)
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_CONFIG_WHEEL);
        event.register(KEY_MAID_CONFIG_WHEEL);
        for (KeyMapping keyQuickModel : KEY_QUICK_MODELS) {
            event.register(keyQuickModel);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        Minecraft minecraft = Minecraft.getInstance();
        File[] modelDirs = new File(minecraft.gameDirectory, "3d-skin").listFiles();
        if (modelDirs == null) {
            return;
        }

        for (File modelDir : modelDirs) {
            String name = modelDir.getName();
            if (name.startsWith("EntityPlayer")
                || name.equals("DefaultAnim")
                || name.equals("CustomAnim")
                || name.equals("Shader")) {
                continue;
            }

            String entityTypeId = name.replace('.', ':');
            EntityType.byString(entityTypeId).ifPresentOrElse(
                entityType -> event.registerEntityRenderer(entityType, new MmdSkinRenderFactory<>(entityTypeId)),
                () -> LOGGER.warn("{} 实体不存在，跳过渲染注册", entityTypeId)
            );
        }
    }
}
