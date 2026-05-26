package com.shiroha.mmdskin.neoforge.maid;

import com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.neoforge.register.MmdSkinAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge 女仆模型同步事件处理器
 */
@EventBusSubscriber(modid = "mmdskin")
public class MaidSyncEventHandler {
    @SubscribeEvent
    public static void onStartTracking(StartTracking event) {
        Entity target = event.getTarget();
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (target.hasData(MmdSkinAttachments.MAID_MMD_MODEL.get())) {
            String modelName = target.getData(MmdSkinAttachments.MAID_MMD_MODEL.get());
            if (modelName != null && !modelName.isEmpty()) {
                MmdSkinNetworkPack syncPack = MmdSkinNetworkPack.forMaid(4, player.getUUID(), target.getId(), modelName);
                PacketDistributor.sendToPlayer(player, syncPack);
            }
        }
    }
}
