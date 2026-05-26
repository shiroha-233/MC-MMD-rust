package com.shiroha.mmdskin.neoforge.register;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.neoforge.stage.NeoForgeStageSessionRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge 网络注册
 * NeoForge 1.21.1 使用新的 Payload 系统
 */
public class MmdSkinRegisterCommon {
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(MmdSkin.MOD_ID, "network_pack");
    
    public static void Register() {
        // NeoForge 1.21.1 网络注册在事件中完成
        // 参见 RegisterPayloadHandlersEvent
        NeoForge.EVENT_BUS.addListener(MmdSkinRegisterCommon::onPlayerLoggedOut);
    }
    
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MmdSkin.MOD_ID);
        registrar.playBidirectional(
            MmdSkinNetworkPack.TYPE,
            MmdSkinNetworkPack.STREAM_CODEC,
            MmdSkinNetworkPack::handle
        );
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            NeoForgeStageSessionRegistry.getInstance().onPlayerDisconnect(player.getServer(), player);
        }
    }
}
