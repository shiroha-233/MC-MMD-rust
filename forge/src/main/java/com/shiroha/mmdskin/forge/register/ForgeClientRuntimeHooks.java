package com.shiroha.mmdskin.forge.register;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.shiroha.mmdskin.forge.network.MmdSkinNetworkPack;
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
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** 文件职责：承接 Forge 客户端运行时事件并执行业务钩子。 */
@OnlyIn(Dist.CLIENT)
final class ForgeClientRuntimeHooks {
    private final KeyMapping keyConfigWheel;
    private final KeyMapping keyMaidConfigWheel;
    private final KeyMapping[] keyQuickModels;

    private boolean configWheelKeyWasDown;
    private boolean maidConfigWheelKeyWasDown;

    ForgeClientRuntimeHooks(KeyMapping keyConfigWheel, KeyMapping keyMaidConfigWheel, KeyMapping[] keyQuickModels) {
        this.keyConfigWheel = keyConfigWheel;
        this.keyMaidConfigWheel = keyMaidConfigWheel;
        this.keyQuickModels = keyQuickModels;
    }

    void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ClientRenderRuntime.get().modelRepository().tick();
        VoicePlaybackManager.getInstance().tick();
        PlayerVoiceSceneObserver.getInstance().tick(minecraft);
        StageAnimSyncHelper.tickPending();

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

    void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(minecraft.player.getName().getString());
        if (selectedModel != null && !selectedModel.isEmpty() && !selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME)) {
            PlayerModelSyncManager.broadcastLocalModelSelection(minecraft.player.getUUID(), selectedModel);
        }
        MmdSkinRegisterCommon.channel.sendToServer(
            new MmdSkinNetworkPack(NetworkOpCode.REQUEST_ALL_MODELS, minecraft.player.getUUID(), ""));
    }

    void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MMDCameraController.getInstance().exitStageMode();
        PlayerModelSyncManager.onDisconnect();
        MmdSkinRendererPlayerHelper.onDisconnect();
        PlayerVoiceSceneObserver.getInstance().onDisconnect();
        VoicePlaybackManager.getInstance().onDisconnect();
        StageSessionService.getInstance().onDisconnect();
    }

    void onPlayerDeath(LivingDeathEvent event) {
        exitStageModeIfLocalPlayer(event.getEntity().getUUID());
    }

    void onRenderGui(RenderGuiEvent.Post event) {
        PerformanceHud.render(event.getGuiGraphics());
    }

    void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        exitStageModeIfLocalPlayer(event.getEntity().getUUID());
    }

    private void exitStageModeIfLocalPlayer(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && playerId.equals(minecraft.player.getUUID())) {
            MMDCameraController controller = MMDCameraController.getInstance();
            if (controller.isInStageMode()) {
                controller.exitStageMode();
            }
        }
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
