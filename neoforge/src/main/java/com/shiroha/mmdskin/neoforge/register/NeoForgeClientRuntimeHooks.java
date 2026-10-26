/** 文件职责：承接 NeoForge 客户端事件并执行运行时钩子。 */
package com.shiroha.mmdskin.neoforge.register;

import com.shiroha.mmdskin.bonesync.BoneSyncManager;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.QuickModelSwitcher;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class NeoForgeClientRuntimeHooks {
    private final KeyMapping keyConfigWheel;
    private final KeyMapping[] keyQuickModels;

    private boolean configWheelKeyWasDown;

    NeoForgeClientRuntimeHooks(KeyMapping keyConfigWheel, KeyMapping[] keyQuickModels) {
        this.keyConfigWheel = keyConfigWheel;
        this.keyQuickModels = keyQuickModels;
    }

    void register(Minecraft minecraft) {
        // NeoForge 26.3：这些是游戏总线事件，用 NeoForge.EVENT_BUS.register(实例) 注册。
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        MmdClientRenderRuntime.current().tick();
        StageAnimSyncHelper.tickPending();
        BoneSyncManager.tickLocal();
        // 模型分发的自动下载扫描（只下半径内玩家正在用的、服务器授权过的模型）
        ModelDistributionClient.tickAutoDownload(minecraft);

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

    @SubscribeEvent
    public void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(minecraft.player.getName().getString());
        if (selectedModel != null
            && !selectedModel.isEmpty()
            && !selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            PlayerModelSyncManager.broadcastLocalModelSelection(minecraft.player.getUUID(), selectedModel);
        }
        MmdSkinNetworkPack.sendToServer(NetworkOpCode.REQUEST_ALL_MODELS, minecraft.player.getUUID(), "");
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MMDCameraController.getInstance().exitStageMode();
        MmdClientRenderRuntime.current().firstPerson().reset();
        PlayerModelSyncManager.onDisconnect();
        MmdSkinRendererPlayerHelper.onDisconnect();
        BoneSyncManager.onDisconnect();
        StageSessionService.getInstance().onDisconnect();
        // 断线时清掉上一台服务器的分发清单，别把别的服的模型留在列表里
        ModelDistributionClient.onDisconnect();
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        exitStageModeIfLocalPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        exitStageModeIfLocalPlayer(event.getEntity().getUUID());
    }

    /**
     * 26.3 的 HUD 绘制：NeoForge 的 {@code RenderGuiEvent.Post} 在原版 HUD 画完之后触发，
     * 对应 Fabric 端的 {@code HudElementRegistry.addLast}。
     * 事件带的是 26.3 的 {@code GuiGraphicsExtractor}，与 common 的 PerformanceHud 匹配。
     */
    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        PerformanceHud.render(event.getGuiGraphics());
    }

    private void exitStageModeIfLocalPlayer(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !playerId.equals(minecraft.player.getUUID())) {
            return;
        }
        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isInStageMode()) {
            controller.exitStageMode();
        }
    }
}
