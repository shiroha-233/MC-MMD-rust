/** 文件职责：注册 Fabric 客户端按键、网络接收器、运行时钩子与实体渲染入口。 */
package com.shiroha.mmdskin.fabric.register;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.fabric.config.ModConfigScreen;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.mixin.fabric.KeyMappingAccessor;
import com.shiroha.mmdskin.renderer.integration.entity.MmdSkinRenderFactory;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import java.io.File;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class MmdSkinRegisterClient {
    private static final Logger LOGGER = LogManager.getLogger();

    static final KeyMapping KEY_CONFIG_WHEEL = new KeyMapping(
        "key.mmdskin.config_wheel",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.mmdskin"
    );

    static final KeyMapping KEY_MAID_CONFIG_WHEEL = new KeyMapping(
        "key.mmdskin.maid_config_wheel",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.mmdskin"
    );

    static final KeyMapping[] KEY_QUICK_MODELS = new KeyMapping[4];

    private static final FabricClientNetworkBindings NETWORK_BINDINGS = new FabricClientNetworkBindings();
    private static final FabricClientRuntimeHooks RUNTIME_HOOKS =
        new FabricClientRuntimeHooks(KEY_CONFIG_WHEEL, KEY_MAID_CONFIG_WHEEL, KEY_QUICK_MODELS);

    static {
        for (int i = 0; i < KEY_QUICK_MODELS.length; i++) {
            KEY_QUICK_MODELS[i] = new KeyMapping(
                "key.mmdskin.quick_model_" + (i + 1),
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                "key.categories.mmdskin"
            );
        }
    }

    private MmdSkinRegisterClient() {
    }

    public static void Register() {
        Minecraft minecraft = Minecraft.getInstance();

        KeyMappingUtil.setBoundKeyGetter(keyMapping -> {
            if (keyMapping instanceof KeyMappingAccessor accessor) {
                return accessor.mmd$getBoundKey();
            }
            return InputConstants.UNKNOWN;
        });

        KeyBindingHelper.registerKeyBinding(KEY_CONFIG_WHEEL);
        if (MaidCompatMixinPlugin.isMaidModLoaded()) {
            KeyBindingHelper.registerKeyBinding(KEY_MAID_CONFIG_WHEEL);
        }
        for (KeyMapping keyQuickModel : KEY_QUICK_MODELS) {
            KeyBindingHelper.registerKeyBinding(keyQuickModel);
        }

        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        NETWORK_BINDINGS.register(minecraft);
        RUNTIME_HOOKS.register(minecraft);
        registerEntityRenderers(minecraft);
        registerPayloadReceiver();
    }

    private static void registerEntityRenderers(Minecraft minecraft) {
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
                entityType -> EntityRendererRegistry.register(entityType, new MmdSkinRenderFactory<>(entityTypeId)),
                () -> LOGGER.warn("{} 实体不存在，跳过渲染注册", entityTypeId)
            );
        }
    }

    private static void registerPayloadReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
            com.shiroha.mmdskin.fabric.network.MmdSkinPayload.TYPE,
            (payload, context) -> context.client().execute(() -> MmdSkinNetworkPack.handlePayload(payload))
        );
    }
}
