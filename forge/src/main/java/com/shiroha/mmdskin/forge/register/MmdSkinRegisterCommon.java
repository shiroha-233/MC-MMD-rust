package com.shiroha.mmdskin.forge.register;

import com.shiroha.mmdskin.forge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.forge.stage.ForgeStageSessionRegistry;
import com.shiroha.mmdskin.ui.network.ServerModelRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 服务端网络注册
 */
public class MmdSkinRegisterCommon {
    public static SimpleChannel channel;
    static String networkVersion = "1";

    public static void Register() {
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("3d-skin", "network_pack"),
                () -> networkVersion,
                NetworkRegistry.acceptMissingOr(networkVersion),
                version -> version.equals(networkVersion));

        channel.registerMessage(0, MmdSkinNetworkPack.class,
                MmdSkinNetworkPack::pack, MmdSkinNetworkPack::new, MmdSkinNetworkPack::handle);

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            ServerModelRegistry.onPlayerLeave(event.getEntity().getUUID());
            if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
                ForgeStageSessionRegistry.getInstance().onPlayerDisconnect(player.getServer(), player);
            }
        });
    }
}

