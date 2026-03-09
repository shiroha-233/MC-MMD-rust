package com.shiroha.mmdskin.stage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class StageClientContext {
    public record NearbyPlayer(UUID uuid, String name) {
    }

    private StageClientContext() {
    }

    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    public static UUID getLocalPlayerUUID() {
        Minecraft mc = minecraft();
        return mc.player != null ? mc.player.getUUID() : null;
    }

    public static String resolvePlayerName(UUID uuid) {
        if (uuid == null) {
            return "Unknown";
        }
        Minecraft mc = minecraft();
        if (mc.level != null) {
            Player player = mc.level.getPlayerByUUID(uuid);
            if (player != null) {
                return player.getName().getString();
            }
        }
        return uuid.toString().substring(0, 8);
    }

    public static List<NearbyPlayer> getNearbyPlayers(double range) {
        List<NearbyPlayer> result = new ArrayList<>();
        Minecraft mc = minecraft();
        if (mc.player == null || mc.level == null) {
            return result;
        }
        UUID selfUUID = mc.player.getUUID();
        for (Player player : mc.level.players()) {
            if (player.getUUID().equals(selfUUID)) {
                continue;
            }
            if (mc.player.distanceTo(player) <= range) {
                result.add(new NearbyPlayer(player.getUUID(), player.getName().getString()));
            }
        }
        result.sort(Comparator.comparing(NearbyPlayer::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
