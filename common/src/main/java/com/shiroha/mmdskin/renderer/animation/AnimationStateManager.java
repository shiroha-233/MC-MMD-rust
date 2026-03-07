package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.renderer.core.EntityAnimState;
import com.shiroha.mmdskin.renderer.model.MMDModelManager.Model;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;

public class AnimationStateManager {

    private static final float TRANSITION_TIME = 0.25f;

    public static void updateAnimationState(AbstractClientPlayer player, Model model) {
        if (model.entityData.playCustomAnim) {
            if (!model.entityData.playStageAnim) {
                boolean local = isLocalPlayer(player);
                if (local && shouldStopCustomAnimation(player)) {
                    stopCustomAnim(model);
                    ActionWheelNetworkHandler.sendAnimStopToServer();
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
                model.model.setLayerLoop(1, true);
                model.model.transitionAnim(0, 1, TRANSITION_TIME);
            }
        } else if (player.hurtTime <= 0) {
            updateHandAnimation(player, model);
        }
    }

    private static void updateHandAnimation(AbstractClientPlayer player, Model model) {
        if (player.getUsedItemHand() == InteractionHand.MAIN_HAND && player.isUsingItem()) {
            String itemId = getItemId(player, InteractionHand.MAIN_HAND);
            applyCustomItemAnimation(model, EntityAnimState.State.ItemRight, itemId, "Right", "using", 1);
        } else if (player.swingingArm == InteractionHand.MAIN_HAND && player.swinging) {
            String itemId = getItemId(player, InteractionHand.MAIN_HAND);
            applyCustomItemAnimation(model, EntityAnimState.State.SwingRight, itemId, "Right", "swinging", 1);
        } else if (player.getUsedItemHand() == InteractionHand.OFF_HAND && player.isUsingItem()) {
            String itemId = getItemId(player, InteractionHand.OFF_HAND);
            applyCustomItemAnimation(model, EntityAnimState.State.ItemLeft, itemId, "Left", "using", 1);
        } else if (player.swingingArm == InteractionHand.OFF_HAND && player.swinging) {
            String itemId = getItemId(player, InteractionHand.OFF_HAND);
            applyCustomItemAnimation(model, EntityAnimState.State.SwingLeft, itemId, "Left", "swinging", 1);
        }
    }

    private static void updateLayer2Animation(AbstractClientPlayer player, Model model) {
        if (player.isShiftKeyDown() && !player.isVisuallyCrawling()) {
            changeAnimationOnce(model, EntityAnimState.State.Sneak, 2);
            return;
        }

        if (model.entityData.stateLayers[2] != EntityAnimState.State.Idle) {
            model.entityData.stateLayers[2] = EntityAnimState.State.Idle;
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
        if (model.entityData.stateLayers[layer] != targetState) {
            model.entityData.stateLayers[layer] = targetState;
            model.model.transitionAnim(
                    MMDAnimManager.GetAnimModel(model.model, targetState.propertyName), layer, TRANSITION_TIME);
        }
    }

    private static void applyCustomItemAnimation(Model model, EntityAnimState.State targetState,
                                                  String itemName, String activeHand, String handState, int layer) {
        boolean shouldLoop = !"using".equals(handState);

        long anim = MMDAnimManager.GetAnimModel(model.model,
                String.format("itemActive_%s_%s_%s", itemName, activeHand, handState));

        if (anim != 0) {
            if (model.entityData.stateLayers[layer] != targetState) {
                model.entityData.stateLayers[layer] = targetState;
                model.model.setLayerLoop(layer, shouldLoop);
                model.model.transitionAnim(anim, layer, TRANSITION_TIME);
            }
            return;
        }

        if (targetState == EntityAnimState.State.ItemRight || targetState == EntityAnimState.State.SwingRight) {
            if (model.entityData.stateLayers[layer] != EntityAnimState.State.SwingRight) {
                model.model.setLayerLoop(layer, shouldLoop);
            }
            changeAnimationOnce(model, EntityAnimState.State.SwingRight, layer);
        } else if (targetState == EntityAnimState.State.ItemLeft || targetState == EntityAnimState.State.SwingLeft) {
            if (model.entityData.stateLayers[layer] != EntityAnimState.State.SwingLeft) {
                model.model.setLayerLoop(layer, shouldLoop);
            }
            changeAnimationOnce(model, EntityAnimState.State.SwingLeft, layer);
        }
    }

    private static String getItemId(AbstractClientPlayer player, InteractionHand hand) {
        String descriptionId = player.getItemInHand(hand).getItem().getDescriptionId();
        return descriptionId.substring(descriptionId.indexOf(".") + 1);
    }
}