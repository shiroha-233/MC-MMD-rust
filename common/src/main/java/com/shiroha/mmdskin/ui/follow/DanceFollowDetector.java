package com.shiroha.mmdskin.ui.follow;

import com.shiroha.mmdskin.renderer.render.PlayerModelResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 跳舞目标检测器，扫描附近正在跳舞的玩家
 */
public final class DanceFollowDetector {

    private static final double DETECT_RANGE = 16.0;
    private static volatile DanceTarget lastDetected = null;

    private DanceFollowDetector() {}

    public record DanceTarget(AbstractClientPlayer player, boolean isStageAnim) {}

    public static DanceTarget detect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;

        UUID selfUUID = mc.player.getUUID();

        for (Player p : mc.level.players()) {
            if (p.getUUID().equals(selfUUID)) continue;
            if (mc.player.distanceTo(p) > DETECT_RANGE) continue;
            if (!(p instanceof AbstractClientPlayer acp)) continue;

            PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(acp);
            if (resolved == null) continue;

            boolean stage = resolved.model().entityData.playStageAnim;
            boolean custom = resolved.model().entityData.playCustomAnim;

            if (stage || custom) {
                lastDetected = new DanceTarget(acp, stage);
                return lastDetected;
            }
        }
        return null;
    }

    public static DanceTarget getLastDetected() { return lastDetected; }

    public static void reset() {
        lastDetected = null;
    }
}
