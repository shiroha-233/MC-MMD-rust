package com.shiroha.mmdskin.player.animation;

import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager.Model;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AnimationStateManager {

    private static final float TRANSITION_TIME = 0.25f;
    static final String DRINK_ANIMATION = "Drink";

    public static void updateAnimationState(AbstractClientPlayer player, Model model) {
        if (model.entityData.playCustomAnim) {
            if (!model.entityData.playStageAnim) {
                boolean local = isLocalPlayer(player);
                if (local && shouldStopCustomAnimation(player)) {
                    stopCustomAnim(model);
                    ActionWheelNetworkHandler.getInstance().syncAnimStop();
                }
            }
        }

        if (!model.entityData.playCustomAnim) {
            updateLayer0Animation(player, model);
            updateLayer1Animation(player, model);
            updateLayer2Animation(player, model);
        }
    }

    private static void updateLayer0Animation(AbstractClientPlayer player, Model model) {
        EntityAnimState.State target = resolveLayer0State(player);
        changeAnimationOnce(model, target, 0);
    }

    private static EntityAnimState.State resolveLayer0State(AbstractClientPlayer player) {
        if (player.getHealth() == 0.0f) return EntityAnimState.State.Die;
        if (player.isFallFlying()) return EntityAnimState.State.ElytraFly;
        if (player.isSleeping()) return EntityAnimState.State.Sleep;
        if (player.isPassenger()) return resolveRidingState(player);
        if (player.isSwimming()) return EntityAnimState.State.Swim;
        if (player.onClimbable()) return resolveClimbingState(player);
        if (player.isSprinting() && !player.isShiftKeyDown()) return EntityAnimState.State.Sprint;
        if (player.isVisuallyCrawling()) return resolveCrawlState(player);
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
        double vy = player.getY() - player.yo;
        if (vy > 0) return EntityAnimState.State.OnClimbableUp;
        if (vy < 0) return EntityAnimState.State.OnClimbableDown;
        return EntityAnimState.State.OnClimbable;
    }

    private static EntityAnimState.State resolveCrawlState(AbstractClientPlayer player) {
        return hasMovement(player) ? EntityAnimState.State.Crawl : EntityAnimState.State.LieDown;
    }

    private static void updateLayer1Animation(AbstractClientPlayer player, Model model) {
        if ((!player.isUsingItem() && !player.swinging && player.hurtTime <= 0) || player.isSleeping()) {
            if (model.entityData.stateLayers[1] != EntityAnimState.State.Idle) {
                model.entityData.stateLayers[1] = EntityAnimState.State.Idle;
                model.entityData.layerAnimationKeys[1] = null;
                model.model.setLayerLoop(1, true);
                model.model.transitionAnim(0, 1, TRANSITION_TIME);
            }
        } else if (player.hurtTime <= 0) {
            updateHandAnimation(player, model);
        }
    }

    private static void updateHandAnimation(AbstractClientPlayer player, Model model) {
        if (player.getUsedItemHand() == InteractionHand.MAIN_HAND && player.isUsingItem()) {
            updateUsingItemAnimation(model, player.getItemInHand(InteractionHand.MAIN_HAND),
                    EntityAnimState.State.ItemRight, "Right", 1);
        } else if (player.swingingArm == InteractionHand.MAIN_HAND && player.swinging) {
            String itemId = getItemId(player.getItemInHand(InteractionHand.MAIN_HAND));
            applyCustomItemAnimation(model, EntityAnimState.State.SwingRight, itemId,
                    "Right", UseAnim.NONE, "swinging", 1);
        } else if (player.getUsedItemHand() == InteractionHand.OFF_HAND && player.isUsingItem()) {
            updateUsingItemAnimation(model, player.getItemInHand(InteractionHand.OFF_HAND),
                    EntityAnimState.State.ItemLeft, "Left", 1);
        } else if (player.swingingArm == InteractionHand.OFF_HAND && player.swinging) {
            String itemId = getItemId(player.getItemInHand(InteractionHand.OFF_HAND));
            applyCustomItemAnimation(model, EntityAnimState.State.SwingLeft, itemId,
                    "Left", UseAnim.NONE, "swinging", 1);
        }
    }

    private static void updateUsingItemAnimation(Model model, ItemStack itemStack,
                                                 EntityAnimState.State targetState, String activeHand, int layer) {
        String triggerAnimation = resolveUseTriggerAnimationName(itemStack.getUseAnimation());
        if (triggerAnimation != null) {
            long triggerAnim = MMDAnimManager.GetAnimModel(model.model, triggerAnimation);
            if (triggerAnim != 0) {
                applyLayerAnimation(model, targetState, triggerAnimation, triggerAnim, layer, false);
                return;
            }
        }

        applyCustomItemAnimation(model, targetState, getItemId(itemStack), activeHand,
                itemStack.getUseAnimation(), "using", layer);
    }

    private static void updateLayer2Animation(AbstractClientPlayer player, Model model) {
        if (player.isShiftKeyDown() && !player.isVisuallyCrawling()) {
            changeAnimationOnce(model, EntityAnimState.State.Sneak, 2);
            return;
        }

        if (model.entityData.stateLayers[2] != EntityAnimState.State.Idle) {
            model.entityData.stateLayers[2] = EntityAnimState.State.Idle;
            model.entityData.layerAnimationKeys[2] = null;
            model.model.transitionAnim(0, 2, TRANSITION_TIME);
        }
    }

    private static void stopCustomAnim(Model model) {
        model.entityData.playCustomAnim = false;
        model.entityData.playStageAnim = false;
        model.model.changeAnim(MMDAnimManager.GetAnimModel(model.model, "idle"), 0);
        model.model.setLayerLoop(1, true);
        model.model.changeAnim(0, 1);
        model.model.changeAnim(0, 2);
        model.model.resetPhysics();
        model.entityData.invalidateStateLayers();
    }

    private static boolean shouldStopCustomAnimation(AbstractClientPlayer player) {
        return player.getHealth() == 0.0f || player.isFallFlying()
                || player.isSleeping() || player.isSwimming()
                || player.onClimbable() || player.isSprinting()
                || player.isVisuallyCrawling() || player.isPassenger()
                || hasMovement(player);
    }

    private static boolean hasMovement(AbstractClientPlayer player) {
        return player.getX() - player.xo != 0.0f || player.getZ() - player.zo != 0.0f;
    }

    private static boolean isLocalPlayer(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(player.getUUID());
    }

    private static boolean isHorselike(EntityType<?> type) {
        return type == EntityType.HORSE || type == EntityType.DONKEY
                || type == EntityType.MULE || type == EntityType.SKELETON_HORSE
                || type == EntityType.ZOMBIE_HORSE;
    }

    private static void changeAnimationOnce(Model model, EntityAnimState.State targetState, int layer) {
        String animationKey = targetState.propertyName;
        if (model.entityData.stateLayers[layer] != targetState
                || !Objects.equals(model.entityData.layerAnimationKeys[layer], animationKey)) {
            model.entityData.stateLayers[layer] = targetState;
            model.entityData.layerAnimationKeys[layer] = animationKey;
            model.model.transitionAnim(MMDAnimManager.GetAnimModel(model.model, animationKey), layer, TRANSITION_TIME);
        }
    }

    private static void applyCustomItemAnimation(Model model, EntityAnimState.State targetState,
                                                 String itemName, String activeHand, UseAnim useAnim,
                                                 String handState, int layer) {
        boolean shouldLoop = !"using".equals(handState);
        for (String animationKey : resolveItemAnimationKeys(itemName, activeHand, useAnim, handState)) {
            long anim = MMDAnimManager.GetAnimModel(model.model, animationKey);
            if (anim != 0) {
                applyLayerAnimation(model, targetState, animationKey, anim, layer, shouldLoop);
                return;
            }
        }

        if (targetState == EntityAnimState.State.ItemRight || targetState == EntityAnimState.State.SwingRight) {
            changeAnimationOnce(model, EntityAnimState.State.SwingRight, layer);
            model.model.setLayerLoop(layer, shouldLoop);
        } else if (targetState == EntityAnimState.State.ItemLeft || targetState == EntityAnimState.State.SwingLeft) {
            changeAnimationOnce(model, EntityAnimState.State.SwingLeft, layer);
            model.model.setLayerLoop(layer, shouldLoop);
        }
    }

    private static void applyLayerAnimation(Model model, EntityAnimState.State targetState, String animationKey,
                                            long animHandle, int layer, boolean shouldLoop) {
        if (animHandle == 0) {
            return;
        }
        if (model.entityData.stateLayers[layer] != targetState
                || !Objects.equals(model.entityData.layerAnimationKeys[layer], animationKey)) {
            model.entityData.stateLayers[layer] = targetState;
            model.entityData.layerAnimationKeys[layer] = animationKey;
            model.model.setLayerLoop(layer, shouldLoop);
            model.model.transitionAnim(animHandle, layer, TRANSITION_TIME);
        }
    }

    static String resolveUseTriggerAnimationName(UseAnim useAnim) {
        if (useAnim == null) {
            return null;
        }
        return switch (useAnim) {
            case EAT, DRINK -> DRINK_ANIMATION;
            default -> null;
        };
    }

    static List<String> resolveItemAnimationKeys(String itemName, String activeHand, UseAnim useAnim,
                                                 String handState) {
        List<String> animationKeys = new ArrayList<>();
        animationKeys.add(buildItemAnimationKey(itemName, activeHand, handState));

        if (useAnim == UseAnim.BOW) {
            String alternateHand = "Right".equals(activeHand) ? "Left" : "Right";
            animationKeys.add(buildItemAnimationKey(itemName, alternateHand, handState));
        }

        return animationKeys;
    }

    private static String buildItemAnimationKey(String itemName, String activeHand, String handState) {
        return String.format("itemActive_%s_%s_%s", itemName, activeHand, handState);
    }

    private static String getItemId(ItemStack itemStack) {
        String descriptionId = itemStack.getItem().getDescriptionId();
        int dotIndex = descriptionId.indexOf('.');
        return dotIndex >= 0 ? descriptionId.substring(dotIndex + 1) : descriptionId;
    }
}
