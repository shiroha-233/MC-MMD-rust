/* 文件职责：注册 Fabric 客户端生命周期、HUD 与按键运行时钩子。 */
package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.bonesync.BoneSyncManager;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.client.gpu.MmdRenderPipelines;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.config.UIConstants;
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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Optional;
import java.util.OptionalDouble;

final class FabricClientRuntimeHooks {
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
        registerWorldFrameFlush();
        registerPipelineReload();
    }

    /**
     * 26.2 世界帧 flush 时机：必须在 frame graph 执行完之后绘制，否则会被 main pass 整帧覆盖。
     * 官方例程与社区一致采用 AFTER_TRANSLUCENT_TERRAIN（地形深度已就绪）。
     */
    private static void registerWorldFrameFlush() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            MmdClientRenderRuntime runtime = MmdClientRenderRuntime.currentIfInstalled().orElse(null);
            if (runtime == null) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (IrisCompatibility.isShadowPass()) {
                runtime.frameQueue().clear();
                return;
            }
            RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
            LocalPlayer player = minecraft.player;
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "mmdskin world frame",
                            mainTarget.getColorTextureView(),
                            Optional.empty(),
                            mainTarget.getDepthTextureView(),
                            OptionalDouble.empty())) {
                float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                var cameraPos = context.levelState().cameraRenderState.pos;
                // 26.2 的 LevelRenderEvents 回调 poseStack 与实体提交均为相机相对坐标，
                // ModelView（CameraMatrices）只含视图旋转，顶点携带 -pos，无需额外换算。
                runtime.firstPerson().localModels().contribute(
                        minecraft, minecraft.gameRenderer.mainCamera(), partialTick,
                        runtime.frameId(), runtime.frameDeltaSeconds(),
                        player != null && YsmCompat.isYsmActive(player),
                        context.poseStack(), context.submitNodeCollector());
                runtime.flushWorldFrame(cameraPos.x, cameraPos.y, cameraPos.z, 0xF000F0);
            }
        });
    }

    private static void registerPipelineReload() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    private static final Identifier ID = Identifier.fromNamespaceAndPath(
                            "mmdskin", "render_pipelines");

                    @Override
                    public Identifier getFabricId() {
                        return ID;
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        MmdRenderPipelines.deactivate();
                        Minecraft minecraft = Minecraft.getInstance();
                        MmdRenderPipelines.validateAndActivate(
                                RenderSystem.getDevice(), minecraft.getShaderManager()::getShader);
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

        if (!player.isAlive()) {
            MMDCameraController controller = MMDCameraController.getInstance();
            if (controller.isInStageMode()) {
                controller.exitStageMode();
            }
        }

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
    }
}
