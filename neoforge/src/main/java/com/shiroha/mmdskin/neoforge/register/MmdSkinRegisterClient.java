/** 文件职责：注册 NeoForge 客户端按键、事件钩子、网络发送器与实体渲染入口。 */
package com.shiroha.mmdskin.neoforge.register;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.client.entity.LegacyEntityMmdRenderer;
import com.shiroha.mmdskin.mixin.neoforge.KeyMappingAccessor;
import com.shiroha.mmdskin.neoforge.config.ModConfigScreen;
import com.shiroha.mmdskin.neoforge.render.NeoForgeRenderAdapter;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import java.io.File;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MmdSkinRegisterClient {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 26.3 的按键分类不再是一个翻译键字符串，而是一个 {@link KeyMapping.Category} 对象，
     * 它的显示名翻译键是 {@code key.category.<namespace>.<path>}。
     *
     * <p>NeoForge 26.3 把 {@code KeyMapping.Category.register(Identifier)} 标记成了 deprecated，
     * 要求改用 {@link RegisterKeyMappingsEvent#registerCategory(KeyMapping.Category)}，
     * 所以这里只 new、不 register。
     */
    static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath("mmdskin", "main"));

    static final KeyMapping KEY_CONFIG_WHEEL = new KeyMapping(
        "key.mmdskin.config_wheel",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_LALT,
        KEY_CATEGORY
    );

    static final KeyMapping[] KEY_QUICK_MODELS = new KeyMapping[4];

    private static final NeoForgeClientNetworkBindings NETWORK_BINDINGS = new NeoForgeClientNetworkBindings();
    private static final NeoForgeClientRuntimeHooks RUNTIME_HOOKS =
        new NeoForgeClientRuntimeHooks(KEY_CONFIG_WHEEL, KEY_QUICK_MODELS);
    private static final NeoForgeRenderAdapter RENDER_ADAPTER = new NeoForgeRenderAdapter();

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

        ConfigWheelScreen.setModSettingsScreenFactory(() -> ModConfigScreen.create(null));
        NETWORK_BINDINGS.register(minecraft);
        // 游戏总线事件：客户端 tick / 连接状态 / HUD / 世界渲染阶段。
        // （NeoForge 26.3 的 EventBusSubscriber 不再区分 MOD / GAME 总线，
        //   这里直接用实例注册最直观。）
        NeoForge.EVENT_BUS.register(RUNTIME_HOOKS);
        NeoForge.EVENT_BUS.register(RENDER_ADAPTER);
    }

    /** 按键注册走 NeoForge 的 mod 总线事件（1.21.x 是 RegisterKeyMappingsEvent，26.3 依然有）。 */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(KEY_CONFIG_WHEEL);
        for (KeyMapping keyQuickModel : KEY_QUICK_MODELS) {
            event.register(keyQuickModel);
        }
    }

    /**
     * 实体渲染器注册。
     *
     * <p>26.3 删除了 {@code EntityType.byString}，改为直接查内置实体注册表。
     * <p>
     * 注意：{@code BuiltInRegistries.ENTITY_TYPE} 是 DefaultedRegistry，未知 id 会被兜底成默认实体，
     * 所以必须先用 {@code containsKey} 判存在，否则会给不存在的模型注册出一个"猪"的渲染器。
     */
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
                || name.equals("StageAnim")
                || name.equals("Shader")) {
                continue;
            }

            String entityTypeId = name.replace('.', ':');
            EntityType<?> entityType = findEntityType(entityTypeId);
            if (entityType != null) {
                event.registerEntityRenderer(entityType,
                        context -> new LegacyEntityMmdRenderer<>(context, name));
            } else {
                LOGGER.warn("{} 实体不存在，跳过渲染注册", entityTypeId);
            }
        }
    }

    private static EntityType<?> findEntityType(String entityTypeId) {
        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getValue(id);
    }
}
