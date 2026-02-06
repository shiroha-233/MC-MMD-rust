package com.shiroha.mmdskin.neoforge.network;

import java.util.UUID;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.renderer.render.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.ui.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge 网络包处理
 * 使用 NeoForge 1.21.1 的 Payload 系统
 * 支持动作同步和物理重置
 */
public record MmdSkinNetworkPack(int opCode, UUID playerUUID, String animId, int arg0) implements CustomPacketPayload {
    
    public static final Type<MmdSkinNetworkPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MmdSkin.MOD_ID, "network_pack"));
    
    public static final StreamCodec<FriendlyByteBuf, MmdSkinNetworkPack> STREAM_CODEC = StreamCodec.of(
        MmdSkinNetworkPack::encode,
        MmdSkinNetworkPack::decode
    );
    
    // 工厂方法（字符串参数）
    public static MmdSkinNetworkPack withAnimId(int opCode, UUID playerUUID, String animId) {
        return new MmdSkinNetworkPack(opCode, playerUUID, animId, 0);
    }
    
    // 工厂方法（整数参数）
    public static MmdSkinNetworkPack withArg(int opCode, UUID playerUUID, int arg0) {
        return new MmdSkinNetworkPack(opCode, playerUUID, "", arg0);
    }
    
    // 工厂方法（女仆模型变更）
    public static MmdSkinNetworkPack forMaid(int opCode, UUID playerUUID, int entityId, String modelName) {
        return new MmdSkinNetworkPack(opCode, playerUUID, modelName, entityId);
    }
    
    private static void encode(FriendlyByteBuf buffer, MmdSkinNetworkPack pack) {
        buffer.writeInt(pack.opCode);
        buffer.writeUUID(pack.playerUUID);
        if (pack.opCode == 1 || pack.opCode == 3) {
            buffer.writeUtf(pack.animId);
        } else if (pack.opCode == 4 || pack.opCode == 5) {
            buffer.writeInt(pack.arg0);
            buffer.writeUtf(pack.animId);
        } else {
            buffer.writeInt(pack.arg0);
        }
    }
    
    private static MmdSkinNetworkPack decode(FriendlyByteBuf buffer) {
        int opCode = buffer.readInt();
        UUID playerUUID = buffer.readUUID();
        String animId = "";
        int arg0 = 0;
        
        if (opCode == 1 || opCode == 3) {
            animId = buffer.readUtf();
        } else if (opCode == 4 || opCode == 5) {
            arg0 = buffer.readInt();
            animId = buffer.readUtf();
        } else {
            arg0 = buffer.readInt();
        }
        return new MmdSkinNetworkPack(opCode, playerUUID, animId, arg0);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(MmdSkinNetworkPack pack, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                pack.handleClient();
            } else {
                // 服务器端：转发给所有客户端
                PacketDistributor.sendToAllPlayers(pack);
            }
        });
    }
    
    private void handleClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || playerUUID.equals(mc.player.getUUID())) {
            return;
        }
        
        if (mc.level == null) return;
        Player target = mc.level.getPlayerByUUID(playerUUID);
        if (target == null) return;
            
        switch (opCode) {
            case 1 -> MmdSkinRendererPlayerHelper.CustomAnim(target, animId);
            case 2 -> MmdSkinRendererPlayerHelper.ResetPhysics(target);
            case 3 -> PlayerModelSyncManager.onRemotePlayerModelReceived(playerUUID, animId);
            case 4 -> {
                Entity maidEntity = mc.level.getEntity(arg0);
                if (maidEntity != null) {
                    MaidMMDModelManager.bindModel(maidEntity.getUUID(), animId);
                }
            }
            case 5 -> {
                Entity maidEntity = mc.level.getEntity(arg0);
                if (maidEntity != null) {
                    MaidMMDModelManager.playAnimation(maidEntity.getUUID(), animId);
                }
            }
        }
    }
}
