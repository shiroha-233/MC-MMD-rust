package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.bonesync.BoneSyncManager;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.shiroha.mmdskin.fabric.maid.MaidCompatMixinPlugin;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.QuickModelSwitcher;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import com.shiroha.mmdskin.ui.wheel.MaidConfigWheelScreen;
import com.shiroha.mmdskin.voice.runtime.PlayerVoiceSceneObserver;
import com.shiroha.mmdskin.voice.runtime.VoicePlaybackManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 文件职责：注册 Fabric 客户端运行时生命周期与界面钩子。 */
@Environment(EnvType.CLIENT)
final class FabricClientRuntimeHooks {
    private final KeyMapping keyConfigWheel;
    private final KeyMapping keyMaidConfigWheel;
    private final KeyMapping[] keyQuickModels;

    private boolean configWheelKeyWasDown;
    private boolean maidConfigWheelKeyWasDown;

    FabricClientRuntimeHooks(KeyMapping keyConfigWheel, KeyMapping keyMaidConfigWheel, KeyMapping[] keyQuickModels) {
        this.keyConfigWheel = keyConfigWheel;
        this.keyMaidConfigWheel = keyMaidConfigWheel;
        this.keyQuickModels = keyQuickModels;
    }

    void register(Minecraft minecraft) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick(minecraft));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> onJoin(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> PerformanceHud.render(graphics));
    }

    private void onClientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        ClientRenderRuntime.get().modelRepository().tick();
        VoicePlaybackManager.getInstance().tick();
        PlayerVoiceSceneObserver.getInstance().tick(minecraft);
        StageAnimSyncHelper.tickPending();
        BoneSyncManager.tickLocal();

        if (!player.isAlive()) {
            MMDCameraController controller = MMDCameraController.getInstance();
            if (controller.isInStageMode()) {
                controller.exitStageMode();
            }
        }

        if (minecraft.screen == null || minecraft.screen instanceof ConfigWheelScreen) {
            boolean keyDown = keyConfigWheel.isDown();
            if (keyDown && !configWheelKeyWasDown) {
                minecraft.setScreen(new ConfigWheelScreen(keyConfigWheel));
            }
            configWheelKeyWasDown = keyDown;
        } else {
            configWheelKeyWasDown = false;
        }

        if (minecraft.screen == null) {
            for (int i = 0; i < keyQuickModels.length; i++) {
                while (keyQuickModels[i].consumeClick()) {
                    QuickModelSwitcher.switchToSlot(i);
                }
            }
        }

        if (MaidCompatMixinPlugin.isMaidModLoaded()) {
            if (minecraft.screen == null || minecraft.screen instanceof MaidConfigWheelScreen) {
                boolean keyDown = keyMaidConfigWheel.isDown();
                if (keyDown && !maidConfigWheelKeyWasDown) {
                    tryOpenMaidConfigWheel(minecraft);
                }
                maidConfigWheelKeyWasDown = keyDown;
            } else {
                maidConfigWheelKeyWasDown = false;
            }
        }
    }

    private void onJoin(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(player.getName().getString());
        if (selectedModel != null && !selectedModel.isEmpty() && !selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            PlayerModelSyncManager.broadcastLocalModelSelection(player.getUUID(), selectedModel);
        }
        MmdSkinNetworkPack.sendToServer(NetworkOpCode.REQUEST_ALL_MODELS, player.getUUID(), "");
    }

    private void onDisconnect() {
        MMDCameraController.getInstance().exitStageMode();
        PlayerModelSyncManager.onDisconnect();
        MmdSkinRendererPlayerHelper.onDisconnect();
        BoneSyncManager.onDisconnect();
        PlayerVoiceSceneObserver.getInstance().onDisconnect();
        VoicePlaybackManager.getInstance().onDisconnect();
        StageSessionService.getInstance().onDisconnect();
    }

    private void tryOpenMaidConfigWheel(Minecraft minecraft) {
        HitResult hitResult = minecraft.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }

        Entity target = ((EntityHitResult) hitResult).getEntity();
        String className = target.getClass().getName();
        if (className.contains("EntityMaid") || className.contains("touhoulittlemaid")) {
            String maidName = target.getName().getString();
            minecraft.setScreen(new MaidConfigWheelScreen(target.getUUID(), target.getId(), maidName, keyMaidConfigWheel));
        }
    }
}
