/** 文件职责：注册 Fabric 客户端按键、网络接收器、运行时钩子与实体渲染入口。 */
package com.shiroha.mmdskin.fabric.register;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.fabric.config.ModConfigScreen;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.client.entity.LegacyEntityMmdRenderer;
import com.shiroha.mmdskin.mixin.fabric.KeyMappingAccessor;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import java.io.File;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Environment(EnvType.CLIENT)
public final class MmdSkinRegisterClient {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 26.3 的按键分类不再是一个翻译键字符串，而是一个 {@link KeyMapping.Category} 对象，
     * 它的显示名翻译键是 {@code key.category.<namespace>.<path>}。
     */
    static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("mmdskin", "main"));

    static final KeyMapping KEY_CONFIG_WHEEL = new KeyMapping(
        "key.mmdskin.config_wheel",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_LALT,
        KEY_CATEGORY
    );

    static final KeyMapping[] KEY_QUICK_MODELS = new KeyMapping[4];

    private static final FabricClientNetworkBindings NETWORK_BINDINGS = new FabricClientNetworkBindings();
    private static final FabricClientRuntimeHooks RUNTIME_HOOKS =
        new FabricClientRuntimeHooks(KEY_CONFIG_WHEEL, KEY_QUICK_MODELS);

    static {
        for (int i = 0; i < KEY_QUICK_MODELS.length; i++) {
            KEY_QUICK_MODELS[i] = new KeyMapping(
                "key.mmdskin.quick_model_" + (i + 1),
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                KEY_CATEGORY
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

        KeyMappingHelper.registerKeyMapping(KEY_CONFIG_WHEEL);
        for (KeyMapping keyQuickModel : KEY_QUICK_MODELS) {
            KeyMappingHelper.registerKeyMapping(keyQuickModel);
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
                || name.equals("StageAnim")
                || name.equals("Shader")) {
                continue;
            }

            String entityTypeId = name.replace('.', ':');
            EntityType<?> entityType = findEntityType(entityTypeId);
            if (entityType != null) {
                EntityRendererRegistry.register(entityType,
                        context -> new LegacyEntityMmdRenderer<>(context, name));
            } else {
                LOGGER.warn("{} 实体不存在，跳过渲染注册", entityTypeId);
            }
        }
    }

    /**
     * 26.3 删除了 {@code EntityType.byString}，改为直接查内置实体注册表。
     * <p>
     * 注意：{@code BuiltInRegistries.ENTITY_TYPE} 是 DefaultedRegistry，未知 id 会被兜底成默认实体，
     * 所以必须先用 {@code containsKey} 判存在，否则会给不存在的模型注册出一个"猪"的渲染器。
     */
    private static EntityType<?> findEntityType(String entityTypeId) {
        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getValue(id);
    }

    private static void registerPayloadReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
            com.shiroha.mmdskin.fabric.network.MmdSkinPayload.TYPE,
            (payload, context) -> context.client().execute(() -> MmdSkinNetworkPack.handlePayload(payload))
        );
    }
}
