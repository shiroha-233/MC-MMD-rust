package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.renderer.core.EntityAnimState;
import com.shiroha.mmdskin.renderer.model.MMDModelManager.Model;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;

/**
 * 动画状态管理器
 * 根据玩家状态切换动画，区分本地/远程玩家的中断策略
 */
public class AnimationStateManager {
    
    private static final float TRANSITION_TIME = 0.25f;
    
    public static void updateAnimationState(AbstractClientPlayer player, Model model) {
        if (model.entityData.playCustomAnim) {
            // 舞台动画只能由网络包 STAGE_END 结束，不做本地判断
            if (!model.entityData.playStageAnim) {
                boolean local = isLocalPlayer(player);
                if (local && shouldStopCustomAnimation(player)) {
                    // 本地玩家：检测到移动/状态变化时中断，并通知服务器
                    stopCustomAnim(model);
                    ActionWheelNetworkHandler.sendAnimStopToServer();
                }
                // 远程玩家的自定义动画中断完全依赖网络包 RESET_PHYSICS，
                // 不在此处做本地猜测，避免插值抖动导致误判
            }
        }
        
        if (!model.entityData.playCustomAnim) {
            updateLayer0Animation(player, model);
            updateLayer1Animation(player, model);
            updateLayer2Animation(player, model);
        }
    }
    
    private static boolean shouldStopCustomAnimation(AbstractClientPlayer player) {
        return player.getHealth() == 0.0f ||
               player.isFallFlying() ||
               player.isSleeping() ||
               player.isSwimming() ||
               player.onClimbable() ||
               player.isSprinting() ||
               player.isVisuallyCrawling() ||
               player.isPassenger() ||
               hasMovement(player);
    }
    
    private static boolean hasMovement(AbstractClientPlayer player) {
        return player.getX() - player.xo != 0.0f || player.getZ() - player.zo != 0.0f;
    }
    
    /**
     * 中断自定义动画并恢复 idle
     */
    private static void stopCustomAnim(Model model) {
        model.entityData.playCustomAnim = false;
        model.entityData.playStageAnim = false;
        model.model.changeAnim(MMDAnimManager.GetAnimModel(model.model, "idle"), 0);
        model.model.setLayerLoop(1, true);
        model.model.changeAnim(0, 1);
        model.model.changeAnim(0, 2);
        model.entityData.invalidateStateLayers();
    }
    
    private static boolean isLocalPlayer(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(player.getUUID());
    }

    private static void updateLayer0Animation(AbstractClientPlayer player, Model model) {
        if (player.getHealth() == 0.0f) {
            changeAnimationOnce(model, EntityAnimState.State.Die, 0);
        } else if (player.isFallFlying()) {
            changeAnimationOnce(model, EntityAnimState.State.ElytraFly, 0);
        } else if (player.isSleeping()) {
            changeAnimationOnce(model, EntityAnimState.State.Sleep, 0);
        } else if (player.isPassenger()) {
            updateRidingAnimation(player, model);
        } else if (player.isSwimming()) {
            changeAnimationOnce(model, EntityAnimState.State.Swim, 0);
        } else if (player.onClimbable()) {
            updateClimbingAnimation(player, model);
        } else if (player.isSprinting() && !player.isShiftKeyDown()) {
            changeAnimationOnce(model, EntityAnimState.State.Sprint, 0);
        } else if (player.isVisuallyCrawling()) {
            updateCrawlingAnimation(player, model);
        } else if (hasMovement(player)) {
            changeAnimationOnce(model, EntityAnimState.State.Walk, 0);
        } else {
            changeAnimationOnce(model, EntityAnimState.State.Idle, 0);
        }
    }
    
    private static void updateRidingAnimation(AbstractClientPlayer player, Model model) {
        var vehicle = player.getVehicle();
        if (vehicle != null && isHorselike(vehicle.getType()) && hasMovement(player)) {
            changeAnimationOnce(model, EntityAnimState.State.OnHorse, 0);
        } else {
            changeAnimationOnce(model, EntityAnimState.State.Ride, 0);
        }
    }

    
    private static boolean isHorselike(EntityType<?> type) {
        return type == EntityType.HORSE
            || type == EntityType.DONKEY
            || type == EntityType.MULE
            || type == EntityType.SKELETON_HORSE
            || type == EntityType.ZOMBIE_HORSE;
    }
    
    private static void updateClimbingAnimation(AbstractClientPlayer player, Model model) {
        double verticalMovement = player.getY() - player.yo;
        if (verticalMovement > 0) {
            changeAnimationOnce(model, EntityAnimState.State.OnClimbableUp, 0);
        } else if (verticalMovement < 0) {
            changeAnimationOnce(model, EntityAnimState.State.OnClimbableDown, 0);
        } else {
            changeAnimationOnce(model, EntityAnimState.State.OnClimbable, 0);
        }
    }
    
    private static void updateCrawlingAnimation(AbstractClientPlayer player, Model model) {
        if (hasMovement(player)) {
            changeAnimationOnce(model, EntityAnimState.State.Crawl, 0);
        } else {
            changeAnimationOnce(model, EntityAnimState.State.LieDown, 0);
        }
    }
    
    private static void updateLayer1Animation(AbstractClientPlayer player, Model model) {
        if ((!player.isUsingItem() && !player.swinging) || player.isSleeping()) {
            if (model.entityData.stateLayers[1] != EntityAnimState.State.Idle) {
                model.entityData.stateLayers[1] = EntityAnimState.State.Idle;
                model.model.setLayerLoop(1, true);
                model.model.transitionAnim(0, 1, TRANSITION_TIME);
            }
        } else {
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
        } else {
            if (model.entityData.stateLayers[2] != EntityAnimState.State.Idle) {
                model.entityData.stateLayers[2] = EntityAnimState.State.Idle;
                model.model.transitionAnim(0, 2, TRANSITION_TIME);
            }
        }
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
