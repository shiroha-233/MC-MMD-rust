package com.shiroha.mmdskin.player.animation;

import com.shiroha.mmdskin.compat.tacz.TaczGunDetector;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.sync.PlayerActionSyncService;
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
    // 使用版本化名称，确保已提取 v4 的游戏目录仍会获得重新录制的动作。
    static final String TACZ_RIFLE_ADS_ANIMATION = "tacz_hold_rifle_ads_v5";

    public static void updateAnimationState(AbstractClientPlayer player, ManagedModel model) {
        if (model.entityState().playCustomAnim) {
            if (!model.entityState().playStageAnim) {
                boolean local = isLocalPlayer(player);
                if (local && shouldStopCustomAnimation(player)) {
                    stopCustomAnim(model);
                    PlayerActionSyncService.getInstance().syncAnimStop();
                }
            }
        }

        if (!model.entityState().playCustomAnim) {
            updateLayer0Animation(player, model);
            updateLayer1Animation(player, model);
            updateLayer2Animation(player, model);
        }
    }

    private static void updateLayer0Animation(AbstractClientPlayer player, ManagedModel model) {
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

    private static void updateLayer1Animation(AbstractClientPlayer player, ManagedModel model) {
        // TaCZ 枪械统一使用完整权重的持枪动作，不区分本地、远端或视角。
        if (shouldApplyTaczRifleHold(TaczGunDetector.isGun(player.getMainHandItem()),
                player.isSleeping(), player.hurtTime)) {
            applyTaczRifleHoldAnimation(model);
            return;
        }

        // 受伤时原有分支不会更新手部状态，需先清掉上一帧的 TaCZ 动作。
        if (Objects.equals(model.entityState().layerAnimationKeys[1], TACZ_RIFLE_ADS_ANIMATION)) {
            clearLayer1Animation(model);
        }

        // 离开 TaCZ 持枪状态后，后续普通物品动画必须恢复为完整层权重。
        model.modelInstance().setLayerWeight(1, 1.0f);

        if ((!player.isUsingItem() && !player.swinging && player.hurtTime <= 0) || player.isSleeping()) {
            if (model.entityState().stateLayers[1] != EntityAnimState.State.Idle) {
                model.entityState().stateLayers[1] = EntityAnimState.State.Idle;
                model.entityState().layerAnimationKeys[1] = null;
                model.modelInstance().setLayerLoop(1, true);
                model.modelInstance().transitionAnim(0, 1, TRANSITION_TIME);
            }
        } else if (player.hurtTime <= 0) {
            updateHandAnimation(player, model);
        }
    }

    /**
     * 该规则与玩家类型及本地相机无关，便于本地和远端玩家保持一致。
     */
    static boolean shouldApplyTaczRifleHold(boolean isTaczGun, boolean isSleeping, int hurtTime) {
        return isTaczGun && !isSleeping && hurtTime <= 0;
    }

    private static void applyTaczRifleHoldAnimation(ManagedModel model) {
        long animation = model.animationLibrary().animation(TACZ_RIFLE_ADS_ANIMATION);
        if (animation == 0L) {
            clearLayer1Animation(model);
            return;
        }

        if (!Objects.equals(model.entityState().layerAnimationKeys[1], TACZ_RIFLE_ADS_ANIMATION)) {
            model.entityState().stateLayers[1] = EntityAnimState.State.ItemRight;
            model.entityState().layerAnimationKeys[1] = TACZ_RIFLE_ADS_ANIMATION;
            model.modelInstance().setLayerLoop(1, true);
            // 持枪姿势直接切入并循环播放，避免叠加普通物品动画的过渡。
            model.modelInstance().transitionAnim(animation, 1, 0.0f);
        }
        model.modelInstance().setLayerWeight(1, 1.0f);
    }

    private static void clearLayer1Animation(ManagedModel model) {
        if (model.entityState().stateLayers[1] == EntityAnimState.State.Idle
                && model.entityState().layerAnimationKeys[1] == null) {
            return;
        }
        model.entityState().stateLayers[1] = EntityAnimState.State.Idle;
        model.entityState().layerAnimationKeys[1] = null;
        model.modelInstance().setLayerLoop(1, true);
        // 普通物品动画仍按完整权重工作，不能继承上一次 ADS 的零权重。
        model.modelInstance().setLayerWeight(1, 1.0f);
        model.modelInstance().transitionAnim(0, 1, TRANSITION_TIME);
    }

    private static void updateHandAnimation(AbstractClientPlayer player, ManagedModel model) {
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

    private static void updateUsingItemAnimation(ManagedModel model, ItemStack itemStack,
                                                 EntityAnimState.State targetState, String activeHand, int layer) {
        String triggerAnimation = resolveUseTriggerAnimationName(itemStack.getUseAnimation());
        if (triggerAnimation != null) {
            long triggerAnim = model.animationLibrary().animation(triggerAnimation);
            if (triggerAnim != 0) {
                applyLayerAnimation(model, targetState, triggerAnimation, triggerAnim, layer, false);
                return;
            }
        }

        applyCustomItemAnimation(model, targetState, getItemId(itemStack), activeHand,
                itemStack.getUseAnimation(), "using", layer);
    }

    private static void updateLayer2Animation(AbstractClientPlayer player, ManagedModel model) {
        if (player.isShiftKeyDown() && !player.isVisuallyCrawling()) {
            changeAnimationOnce(model, EntityAnimState.State.Sneak, 2);
            return;
        }

        if (model.entityState().stateLayers[2] != EntityAnimState.State.Idle) {
            model.entityState().stateLayers[2] = EntityAnimState.State.Idle;
            model.entityState().layerAnimationKeys[2] = null;
            model.modelInstance().transitionAnim(0, 2, TRANSITION_TIME);
        }
    }

    private static void stopCustomAnim(ManagedModel model) {
        model.entityState().playCustomAnim = false;
        model.entityState().playStageAnim = false;
        model.modelInstance().changeAnim(model.animationLibrary().animation("idle"), 0);
        model.modelInstance().setLayerLoop(1, true);
        model.modelInstance().changeAnim(0, 1);
        model.modelInstance().changeAnim(0, 2);
        model.modelInstance().resetPhysics();
        model.entityState().invalidateStateLayers();
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

    private static void changeAnimationOnce(ManagedModel model, EntityAnimState.State targetState, int layer) {
        String animationKey = targetState.propertyName;
        if (model.entityState().stateLayers[layer] != targetState
                || !Objects.equals(model.entityState().layerAnimationKeys[layer], animationKey)) {
            model.entityState().stateLayers[layer] = targetState;
            model.entityState().layerAnimationKeys[layer] = animationKey;
            model.modelInstance().transitionAnim(model.animationLibrary().animation(animationKey), layer, TRANSITION_TIME);
        }
    }

    private static void applyCustomItemAnimation(ManagedModel model, EntityAnimState.State targetState,
                                                 String itemName, String activeHand, UseAnim useAnim,
                                                 String handState, int layer) {
        boolean shouldLoop = !"using".equals(handState);
        for (String animationKey : resolveItemAnimationKeys(itemName, activeHand, useAnim, handState)) {
            long anim = model.animationLibrary().animation(animationKey);
            if (anim != 0) {
                applyLayerAnimation(model, targetState, animationKey, anim, layer, shouldLoop);
                return;
            }
        }

        if (targetState == EntityAnimState.State.ItemRight || targetState == EntityAnimState.State.SwingRight) {
            changeAnimationOnce(model, EntityAnimState.State.SwingRight, layer);
            model.modelInstance().setLayerLoop(layer, shouldLoop);
        } else if (targetState == EntityAnimState.State.ItemLeft || targetState == EntityAnimState.State.SwingLeft) {
            changeAnimationOnce(model, EntityAnimState.State.SwingLeft, layer);
            model.modelInstance().setLayerLoop(layer, shouldLoop);
        }
    }

    private static void applyLayerAnimation(ManagedModel model, EntityAnimState.State targetState, String animationKey,
                                            long animHandle, int layer, boolean shouldLoop) {
        if (animHandle == 0) {
            return;
        }
        if (model.entityState().stateLayers[layer] != targetState
                || !Objects.equals(model.entityState().layerAnimationKeys[layer], animationKey)) {
            model.entityState().stateLayers[layer] = targetState;
            model.entityState().layerAnimationKeys[layer] = animationKey;
            model.modelInstance().setLayerLoop(layer, shouldLoop);
            model.modelInstance().transitionAnim(animHandle, layer, TRANSITION_TIME);
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
