package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.fabric.network.MmdSkinPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric 通用注册
 * MC 1.21.1: 使用新的 Payload API
 */
public class MmdSkinRegisterCommon {

    public static void Register() {
        // 注册 Payload 类型
        PayloadTypeRegistry.playC2S().register(MmdSkinPayload.TYPE, MmdSkinPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MmdSkinPayload.TYPE, MmdSkinPayload.CODEC);
        
        // 服务端接收处理：转发给其他玩家
        ServerPlayNetworking.registerGlobalReceiver(MmdSkinPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                for (ServerPlayer serverPlayer : PlayerLookup.all(context.server())) {
                    if (!serverPlayer.equals(context.player())) {
                        ServerPlayNetworking.send(serverPlayer, payload);
                    }
                }
            });
        });
    }
}
