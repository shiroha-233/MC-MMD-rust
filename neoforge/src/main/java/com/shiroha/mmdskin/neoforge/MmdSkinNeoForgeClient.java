/** 文件职责：完成 NeoForge 客户端配置、运行时与平台事件入口初始化。 */
package com.shiroha.mmdskin.neoforge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.neoforge.config.MmdSkinConfig;
import com.shiroha.mmdskin.neoforge.register.MmdSkinRegisterClient;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * NeoForge 客户端入口。
 *
 * <p>26.3 适配：{@code @EventBusSubscriber} 不再有 {@code bus} 属性，
 * 事件自己实现 {@code IModBusEvent} 就走 mod 总线、否则走游戏总线。
 *
 * <p>1.21.x 的 {@code RegisterRenderStateModifiersEvent} 在 26.3 已被删除
 * （NeoForge 不再提供 render state modifier 机制），
 * 所以 MMD 快照改由 mixin 直接挂在原版 {@code LivingEntityRenderState} 上。
 *
 * <p><b>MMD 渲染管线必须在资源重载时自己编译</b>，不能走
 * {@code RegisterRenderPipelinesEvent}：
 * 那个事件触发时 Minecraft 还没编译任何管线，
 * {@code activateRegisteredPipelines()} 拿到的缓存是空的，
 * 于是 {@code ready=false}，渲染路径（MmdRenderRouter / MmdGpuDrawDevice）
 * 全部直接 return —— 表现为「模组加载了但 MMD 模型一个都不出现」。
 * 现在改成和 Fabric 端同一条路：资源重载回调里用
 * {@code validateAndActivate} 自己编译并持有管线。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = MmdSkin.MOD_ID)
public final class MmdSkinNeoForgeClient {
    private static final Identifier PIPELINE_RELOAD_ID =
            Identifier.fromNamespaceAndPath("mmdskin", "render_pipelines");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MmdSkinRegisterClient.onRegisterKeyMappings(event);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        MmdSkinRegisterClient.onRegisterEntityRenderers(event);
    }

    /**
     * 资源重载时重新编译 MMD 渲染管线。
     *
     * <p>NeoForge 端对应 Fabric 的
     * {@code ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener}：
     * 两边都是「资源重载回调里自己编译管线」，只是注册入口不同。
     */
    @SubscribeEvent
    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(PIPELINE_RELOAD_ID, new SimplePreparableReloadListener<ResourceManager>() {
            @Override
            protected ResourceManager prepare(ResourceManager resourceManager,
                                            net.minecraft.util.profiling.ProfilerFiller profiler) {
                return resourceManager;
            }

            @Override
            protected void apply(ResourceManager resourceManager,
                                ResourceManager ignored,
                                net.minecraft.util.profiling.ProfilerFiller profiler) {
                MmdRenderPipelines.deactivate();
                // 26.3 的 ShaderManager 不再公开 getShader，改用管线层提供的
                // ResourceManager 版着色器源（与 Fabric 端完全一致）。
                try (var shaderSource = MmdRenderPipelines.shaderSource(resourceManager)) {
                    MmdRenderPipelines.validateAndActivate(
                            RenderSystem.getDevice(), shaderSource);
                }
            }
        });
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        MmdSkinConfig.init();
        MmdSkinClient.initClient();
        MmdSkinRegisterClient.Register();
    }
}
