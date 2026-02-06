package com.shiroha.mmdskin.fabric.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Fabric 网络 Payload
 * MC 1.21.1: 使用新的 CustomPacketPayload API
 */
public record MmdSkinPayload(int opCode, UUID playerUUID, int intArg, int entityId, String stringArg) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<MmdSkinPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mmdskin", "network"));
    
    public static final StreamCodec<FriendlyByteBuf, MmdSkinPayload> CODEC = StreamCodec.of(
        MmdSkinPayload::write,
        MmdSkinPayload::read
    );
    
    // 构造函数：整数参数
    public static MmdSkinPayload createInt(int opCode, UUID playerUUID, int intArg) {
        return new MmdSkinPayload(opCode, playerUUID, intArg, 0, "");
    }
    
    // 构造函数：字符串参数
    public static MmdSkinPayload createString(int opCode, UUID playerUUID, String stringArg) {
        return new MmdSkinPayload(opCode, playerUUID, 0, 0, stringArg);
    }
    
    // 构造函数：女仆相关（entityId + 字符串）
    public static MmdSkinPayload createMaid(int opCode, UUID playerUUID, int entityId, String stringArg) {
        return new MmdSkinPayload(opCode, playerUUID, 0, entityId, stringArg);
    }
    
    private static MmdSkinPayload read(FriendlyByteBuf buf) {
        int opCode = buf.readInt();
        UUID playerUUID = buf.readUUID();
        int intArg = buf.readInt();
        int entityId = buf.readInt();
        String stringArg = buf.readUtf();
        return new MmdSkinPayload(opCode, playerUUID, intArg, entityId, stringArg);
    }
    
    private static void write(FriendlyByteBuf buf, MmdSkinPayload payload) {
        buf.writeInt(payload.opCode);
        buf.writeUUID(payload.playerUUID);
        buf.writeInt(payload.intArg);
        buf.writeInt(payload.entityId);
        buf.writeUtf(payload.stringArg);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
