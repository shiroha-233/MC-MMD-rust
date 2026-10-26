/* 文件职责：定义 NeoForge 网络 Payload（26.3 用单个 CustomPacketPayload 承载全部操作码）。 */
package com.shiroha.mmdskin.neoforge.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 26.3 适配：1.20.1 用 {@code FriendlyByteBuf} + 通道 id 手写信封，
 * 26.3 改成了单个 {@link CustomPacketPayload} record，二进制载荷走 {@code binaryData}。
 */
public record MmdSkinPayload(int opCode, UUID playerUUID, int intArg, int entityId, String stringArg, byte[] binaryData)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MmdSkinPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mmdskin", "network"));

    public static final StreamCodec<FriendlyByteBuf, MmdSkinPayload> CODEC = StreamCodec.of(
        MmdSkinPayload::write,
        MmdSkinPayload::read
    );

    public static MmdSkinPayload createInt(int opCode, UUID playerUUID, int intArg) {
        return new MmdSkinPayload(opCode, playerUUID, intArg, 0, "", new byte[0]);
    }

    public static MmdSkinPayload createString(int opCode, UUID playerUUID, String stringArg) {
        return new MmdSkinPayload(opCode, playerUUID, 0, 0, stringArg, new byte[0]);
    }

    public static MmdSkinPayload createBinary(int opCode, UUID playerUUID, byte[] data) {
        return new MmdSkinPayload(opCode, playerUUID, 0, 0, "", data);
    }

    private static MmdSkinPayload read(FriendlyByteBuf buf) {
        int opCode = buf.readInt();
        UUID playerUUID = buf.readUUID();
        int intArg = buf.readInt();
        int entityId = buf.readInt();
        String stringArg = buf.readUtf();
        byte[] binaryData = buf.readByteArray();
        return new MmdSkinPayload(opCode, playerUUID, intArg, entityId, stringArg, binaryData);
    }

    private static void write(FriendlyByteBuf buf, MmdSkinPayload payload) {
        buf.writeInt(payload.opCode());
        buf.writeUUID(payload.playerUUID());
        buf.writeInt(payload.intArg());
        buf.writeInt(payload.entityId());
        buf.writeUtf(payload.stringArg());
        buf.writeByteArray(payload.binaryData());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
