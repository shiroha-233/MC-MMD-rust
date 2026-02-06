package com.shiroha.mmdskin.fabric.network;

import java.util.UUID;

import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.renderer.render.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.ui.PlayerModelSyncManager;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Fabric 网络包处理
 * MC 1.21.1: 使用新的 Payload API
 */
public class MmdSkinNetworkPack {
    /**
     * 发送到服务器（整数参数版本）
     */
    public static void sendToServer(int opCode, UUID playerUUID, int arg0) {
        ClientPlayNetworking.send(MmdSkinPayload.createInt(opCode, playerUUID, arg0));
    }
    
    /**
     * 发送到服务器（字符串参数版本）
     */
    public static void sendToServer(int opCode, UUID playerUUID, String animId) {
        ClientPlayNetworking.send(MmdSkinPayload.createString(opCode, playerUUID, animId));
    }
    
    /**
     * 发送到服务器（entityId + 字符串参数版本）
     */
    public static void sendToServer(int opCode, UUID playerUUID, int entityId, String data) {
        ClientPlayNetworking.send(MmdSkinPayload.createMaid(opCode, playerUUID, entityId, data));
    }
    
    /**
     * 客户端处理 Payload
     */
    public static void handlePayload(MmdSkinPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (payload.playerUUID().equals(mc.player.getUUID())) return;
        
        int opCode = payload.opCode();
        UUID playerUUID = payload.playerUUID();
        
        switch (opCode) {
            case 1: {
                // 执行动画
                if (mc.level == null) return;
                Player target = mc.level.getPlayerByUUID(playerUUID);
                if (target != null)
                    MmdSkinRendererPlayerHelper.CustomAnim(target, payload.stringArg());
                break;
            }
            case 2: {
                // 重置物理
                if (mc.level == null) return;
                Player target = mc.level.getPlayerByUUID(playerUUID);
                if (target != null)
                    MmdSkinRendererPlayerHelper.ResetPhysics(target);
                break;
            }
            case 3: {
                // 模型选择同步
                PlayerModelSyncManager.onRemotePlayerModelReceived(playerUUID, payload.stringArg());
                break;
            }
            case 4: {
                // 女仆模型变更
                if (mc.level == null) return;
                Entity maidEntity = mc.level.getEntity(payload.entityId());
                if (maidEntity != null)
                    MaidMMDModelManager.bindModel(maidEntity.getUUID(), payload.stringArg());
                break;
            }
            case 5: {
                // 女仆动作变更
                if (mc.level == null) return;
                Entity maidEntity = mc.level.getEntity(payload.entityId());
                if (maidEntity != null)
                    MaidMMDModelManager.playAnimation(maidEntity.getUUID(), payload.stringArg());
                break;
            }
        }
    }
}
