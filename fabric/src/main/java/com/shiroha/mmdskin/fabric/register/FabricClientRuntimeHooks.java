/* 文件职责：注册 Fabric 客户端生命周期、HUD 与按键运行时钩子。 */
package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.bonesync.BoneSyncManager;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.fabric.compat.YsmCompat;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.QuickModelSwitcher;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;

final class FabricClientRuntimeHooks {
    /**
     * 26.3 的 {@code Lightmap} 不再暴露 {@code FULL_BRIGHT}，这里直接用 MC 的全亮 packed light 常量
     * （block = 0xF000，sky = 0xF000）。
     */
    private static final int FULL_BRIGHT_PACKED_LIGHT = 0xF000F0;
    private static final Identifier PERFORMANCE_HUD_ID =
            Identifier.fromNamespaceAndPath("mmdskin", "performance_hud");
    private static final Identifier PIPELINE_RELOAD_ID =
            Identifier.fromNamespaceAndPath("mmdskin", "render_pipelines");

    private final KeyMapping keyConfigWheel;
    private final KeyMapping[] keyQuickModels;

    private boolean configWheelKeyWasDown;

    FabricClientRuntimeHooks(KeyMapping keyConfigWheel, KeyMapping[] keyQuickModels) {
        this.keyConfigWheel = keyConfigWheel;
        this.keyQuickModels = keyQuickModels;
    }

    void register(Minecraft minecraft) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick(minecraft));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> onJoin(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        // 26.3 的 HUD 拆成两阶段：注册期登记元素，渲染期元素自己抽取绘制状态。
        // addLast 落在原版字幕层（最后一个根层）末尾，等价于 1.21.5 的 HudRenderCallback：
        // 都在原版 HUD 全部画完之后绘制。
        HudElementRegistry.addLast(PERFORMANCE_HUD_ID,
                (graphics, deltaTracker) -> PerformanceHud.render(graphics));
        registerWorldRendering();
        registerPipelineReload();
    }

    /**
     * 26.3 的世界渲染被拆成"提取"与"提交+渲染"两段，1.21.5 的 WorldRenderEvents 语义要按阶段重新落位：
     * <ul>
     *   <li>{@code WorldRenderEvents.START} → {@code LevelExtractionEvents.END_EXTRACTION}：
     *       1.21.5 的 START 在 {@code LevelRenderer.renderLevel} 开头，早于实体绘制；26.3 的实体绘制请求
     *       是在 {@code LevelRenderer.submitFeatures} 的"提交阶段"产生的，而 {@code beginFrame} 会清空帧队列，
     *       所以它必须落在提交阶段之前，只有 {@code END_EXTRACTION}（{@code LevelExtractor.extract} 结束）满足。</li>
     *   <li>{@code WorldRenderEvents.AFTER_ENTITIES} 的"贡献"部分 → {@code LevelRenderEvents.COLLECT_SUBMITS}：
     *       该事件在 {@code LevelRenderer.submitFeatures} 结束时触发，此时所有实体都已提交完毕，
     *       而 {@code SubmitNodeCollector} 还没有被 {@code prepareFrame} 消费，手持物仍可提交。</li>
     *   <li>{@code WorldRenderEvents.AFTER_ENTITIES} 的"flush"部分 → {@code LevelRenderEvents.END_MAIN}：
     *       该事件在主 Render Pass 关闭之后触发，地形与实体已经画进主目标（深度缓冲有效），
     *       同时没有别的 Render Pass 处于打开状态，MMD 才能自己开一个 Pass 拿到正确的遮挡关系。</li>
     * </ul>
     */
    private static void registerWorldRendering() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> MmdClientRenderRuntime.current().beginFrame(
                context.deltaTracker().getGameTimeDeltaTicks()));
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
            LocalPlayer player = Minecraft.getInstance().player;
            // 26.3 的 LevelRenderState.worldPartialTicks 就是 1.21.5 的
            // tickCounter().getGameTimeDeltaPartialTick(false)。
            float partialTick = context.levelState().worldPartialTicks;
            runtime.firstPerson().localModels().contribute(
                    Minecraft.getInstance(), context.gameRenderer().mainCamera(), partialTick,
                    runtime.frameId(), runtime.frameDeltaSeconds(),
                    player != null && YsmCompat.isYsmActive(player),
                    context.poseStack(), context.submitNodeCollector());
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            if (IrisCompatibility.isShadowPass()) {
                MmdClientRenderRuntime.current().frameQueue().clear();
                return;
            }
            MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
            Vec3 camera = context.gameRenderer().mainCamera().position();
            runtime.flushWorldFrame(
                    camera.x, camera.y, camera.z, FULL_BRIGHT_PACKED_LIGHT);
        });
    }

    private static void registerPipelineReload() {
        // 26.3 的资源重载接口换成了 ResourceLoader + SimpleReloadListener。
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                PIPELINE_RELOAD_ID,
                new SimpleReloadListener<ResourceManager>() {
                    @Override
                    protected ResourceManager prepare(PreparableReloadListener.SharedState state) {
                        return state.resourceManager();
                    }

                    @Override
                    protected void apply(ResourceManager resourceManager, PreparableReloadListener.SharedState state) {
                        MmdRenderPipelines.deactivate();
                        // 26.3 的 ShaderManager 不再公开 getShader，改用管线层提供的 ResourceManager 版着色器源。
                        try (var shaderSource = MmdRenderPipelines.shaderSource(resourceManager)) {
                            MmdRenderPipelines.validateAndActivate(
                                    RenderSystem.getDevice(), shaderSource);
                        }
                    }
                });
    }

    private void onClientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        MmdClientRenderRuntime.current().tick();
        StageAnimSyncHelper.tickPending();
        BoneSyncManager.tickLocal();
        // 模型分发的自动下载扫描（只下半径内玩家正在用的、服务器授权过的模型）
        ModelDistributionClient.tickAutoDownload(minecraft);

        if (!player.isAlive()) {
            MMDCameraController controller = MMDCameraController.getInstance();
            if (controller.isInStageMode()) {
                controller.exitStageMode();
            }
        }

        // 26.3 把当前界面挪到了 Minecraft.gui 上（Minecraft.screen 字段已删除）。
        if (minecraft.gui.screen() == null || minecraft.gui.screen() instanceof ConfigWheelScreen) {
            boolean keyDown = keyConfigWheel.isDown();
            if (keyDown && !configWheelKeyWasDown) {
                minecraft.gui.setScreen(new ConfigWheelScreen(keyConfigWheel));
            }
            configWheelKeyWasDown = keyDown;
        } else {
            configWheelKeyWasDown = false;
        }

        if (minecraft.gui.screen() == null) {
            for (int i = 0; i < keyQuickModels.length; i++) {
                while (keyQuickModels[i].consumeClick()) {
                    QuickModelSwitcher.switchToSlot(i);
                }
            }
        }
    }

    private void onJoin(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(player.getName().getString());
        if (selectedModel != null
            && !selectedModel.isEmpty()
            && !selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            PlayerModelSyncManager.broadcastLocalModelSelection(player.getUUID(), selectedModel);
        }
        MmdSkinNetworkPack.sendToServer(NetworkOpCode.REQUEST_ALL_MODELS, player.getUUID(), "");
    }

    private void onDisconnect() {
        MMDCameraController.getInstance().exitStageMode();
        MmdClientRenderRuntime.current().firstPerson().reset();
        PlayerModelSyncManager.onDisconnect();
        MmdSkinRendererPlayerHelper.onDisconnect();
        BoneSyncManager.onDisconnect();
        StageSessionService.getInstance().onDisconnect();
        // 断线时清掉上一台服务器的分发清单，别把别的服的模型留在列表里
        ModelDistributionClient.onDisconnect();
    }
}
