// 负责从实时玩家状态提取不持有实体引用的不可变动画意图。
package com.shiroha.mmdskin.player.animation;

import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

public final class PlayerAnimationIntentExtractor {
    static final String DRINK_ANIMATION = "Drink";

    private PlayerAnimationIntentExtractor() {
    }

    public static MmdAnimationIntent capture(AbstractClientPlayer player) {
        if (player == null) {
            return MmdAnimationIntent.none();
        }
        return new MmdAnimationIntent(
                resolveBaseState(player).propertyName,
                resolveAction(player),
                player.isShiftKeyDown() && !player.isVisuallyCrawling()
                        ? EntityAnimState.State.Sneak.propertyName : null);
    }

    private static EntityAnimState.State resolveBaseState(AbstractClientPlayer player) {
        if (player.getHealth() == 0.0F) return EntityAnimState.State.Die;
        if (player.isFallFlying()) return EntityAnimState.State.ElytraFly;
        if (player.isSleeping()) return EntityAnimState.State.Sleep;
        if (player.isPassenger()) return resolveRidingState(player);
        if (player.isSwimming()) return EntityAnimState.State.Swim;
        if (player.onClimbable()) return resolveClimbingState(player);
        if (player.isSprinting() && !player.isShiftKeyDown()) return EntityAnimState.State.Sprint;
        if (player.isVisuallyCrawling()) {
            return hasMovement(player) ? EntityAnimState.State.Crawl : EntityAnimState.State.LieDown;
        }
        if (hasMovement(player)) return EntityAnimState.State.Walk;
        return EntityAnimState.State.Idle;
    }

    private static EntityAnimState.State resolveRidingState(AbstractClientPlayer player) {
        var vehicle = player.getVehicle();
        if (vehicle != null && isHorselike(vehicle.getType()) && hasMovement(player)) {
            return EntityAnimState.State.OnHorse;
        }
        return EntityAnimState.State.Ride;
    }

    private static EntityAnimState.State resolveClimbingState(AbstractClientPlayer player) {
        double verticalMovement = player.getY() - player.yo;
        if (verticalMovement > 0.0D) return EntityAnimState.State.OnClimbableUp;
        if (verticalMovement < 0.0D) return EntityAnimState.State.OnClimbableDown;
        return EntityAnimState.State.OnClimbable;
    }

    private static MmdAnimationIntent.Action resolveAction(AbstractClientPlayer player) {
        if (player.isSleeping()) {
            return MmdAnimationIntent.Action.clear();
        }
        if (player.hurtTime > 0) {
            return MmdAnimationIntent.Action.preserve();
        }
        if (player.isUsingItem()) {
            InteractionHand hand = player.getUsedItemHand();
            return actionFor(player.getItemInHand(hand), hand, true);
        }
        if (player.swinging && player.swingingArm != null) {
            return actionFor(player.getItemInHand(player.swingingArm), player.swingingArm, false);
        }
        return MmdAnimationIntent.Action.clear();
    }

    private static MmdAnimationIntent.Action actionFor(ItemStack stack, InteractionHand hand, boolean using) {
        String side = hand == InteractionHand.MAIN_HAND ? "Right" : "Left";
        String fallback = hand == InteractionHand.MAIN_HAND
                ? EntityAnimState.State.SwingRight.propertyName
                : EntityAnimState.State.SwingLeft.propertyName;
        ItemUseAnimation useAnimation = stack.getUseAnimation();
        String itemAnimation = buildItemAnimationKey(getItemId(stack), side, using ? "using" : "swinging");
        String alternate = using && useAnimation == ItemUseAnimation.BOW
                ? buildItemAnimationKey(getItemId(stack), "Right".equals(side) ? "Left" : "Right", "using")
                : null;
        String trigger = using ? resolveUseTriggerAnimationName(useAnimation) : null;
        if (trigger != null) {
            return MmdAnimationIntent.Action.play(trigger, itemAnimation, alternate != null ? alternate : fallback, false);
        }
        return MmdAnimationIntent.Action.play(itemAnimation, alternate, fallback, !using);
    }

    static String resolveUseTriggerAnimationName(ItemUseAnimation useAnimation) {
        if (useAnimation == null) {
            return null;
        }
        return switch (useAnimation) {
            case EAT, DRINK -> DRINK_ANIMATION;
            default -> null;
        };
    }

    static String buildItemAnimationKey(String itemName, String activeHand, String handState) {
        return "itemActive_" + itemName + "_" + activeHand + "_" + handState;
    }

    private static String getItemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().replace(':', '.');
    }

    private static boolean hasMovement(AbstractClientPlayer player) {
        return player.getX() != player.xo || player.getZ() != player.zo;
    }

    private static boolean isHorselike(EntityType<?> type) {
        return type == EntityTypes.HORSE || type == EntityTypes.DONKEY
                || type == EntityTypes.MULE || type == EntityTypes.SKELETON_HORSE
                || type == EntityTypes.ZOMBIE_HORSE;
    }
}
