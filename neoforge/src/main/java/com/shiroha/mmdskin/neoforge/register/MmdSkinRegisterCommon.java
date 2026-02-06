package com.shiroha.mmdskin.neoforge.register;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack;

import net.minecraft.resources.ResourceLocation;
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
    }
    
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MmdSkin.MOD_ID);
        registrar.playBidirectional(
            MmdSkinNetworkPack.TYPE,
            MmdSkinNetworkPack.STREAM_CODEC,
            MmdSkinNetworkPack::handle
        );
    }
}
