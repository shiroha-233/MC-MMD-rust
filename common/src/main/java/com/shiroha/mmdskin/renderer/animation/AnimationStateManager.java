package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.animation.FbxDefaultAnimPack.AnimationGroup;
import com.shiroha.mmdskin.renderer.animation.FbxDefaultAnimPack.MoveDir;
import com.shiroha.mmdskin.renderer.core.EntityAnimState;
import com.shiroha.mmdskin.renderer.core.EntityAnimState.AnimPhase;
import com.shiroha.mmdskin.renderer.model.MMDModelManager.Model;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.UseAnim;

import java.util.concurrent.ThreadLocalRandom;

public class AnimationStateManager {

    private static final float TRANSITION_TIME = 0.25f;
    private static final float PHASE_TRANSITION_TIME = 0.1f;
    private static final String UPPER_BODY_BONE = "上半身";
    private static final String HEAD_BONE = "頭";

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
        EntityAnimState.State target = resolveLayer0State(player, model);
        String slotName = target.propertyName;
        AnimationGroup group = FbxDefaultAnimPack.isAvailable()
                ? FbxDefaultAnimPack.getGroup(slotName) : null;

        if (group == null) {
            EntityAnimState ed = model.entityData;
            if (ed.layerPhases[0] != AnimPhase.NONE) {
                resetPhase(ed, 0);
            }
            changeAnimationOnce(model, target, 0);
            return;
        }

        EntityAnimState ed = model.entityData;
        AnimPhase phase = ed.layerPhases[0];
        String curGroupId = ed.layerGroupIds[0];
        String targetGroupId = group.groupId;
        boolean groupChanged = !targetGroupId.equals(curGroupId);

        if (phase == AnimPhase.EXITING) {
            if (isLayerFinished(model, 0)) {
                resetPhase(ed, 0);
            } else {
            }
        }

        if (phase == AnimPhase.ENTERING) {
            if (groupChanged) {
                resetPhase(ed, 0);
            } else if (isLayerFinished(model, 0)) {
                ed.layerPhases[0] = AnimPhase.LOOPING;
                MoveDir dir = detectMoveDirection(player);
                String loopStack = group.getLoopForDir(dir);
                ed.layerLoopStacks[0] = loopStack;
                long anim = FbxDefaultAnimPack.loadStack(model.model, loopStack);
                model.model.setLayerLoop(0, !isDieState(target));
                model.model.transitionAnim(anim, 0, PHASE_TRANSITION_TIME);
                ed.stateLayers[0] = target;
                return;
            } else {
            }
        }

        phase = ed.layerPhases[0];
        curGroupId = ed.layerGroupIds[0];
        groupChanged = !targetGroupId.equals(curGroupId);

        if (groupChanged || phase == AnimPhase.NONE) {
            if (curGroupId != null && ed.layerExitStacks[0] != null && phase == AnimPhase.LOOPING) {
                ed.layerPhases[0] = AnimPhase.EXITING;
                long exitAnim = FbxDefaultAnimPack.loadStack(model.model, ed.layerExitStacks[0]);
                model.model.setLayerLoop(0, false);
                model.model.transitionAnim(exitAnim, 0, TRANSITION_TIME);
                return;
            }

            ed.layerGroupIds[0] = targetGroupId;
            ed.layerExitStacks[0] = group.exit;
            ed.stateLayers[0] = target;

            model.model.setLayerBoneExclude(0,
                    target == EntityAnimState.State.Sprint ? HEAD_BONE : null);

            if (group.hasEnter()) {
                ed.layerPhases[0] = AnimPhase.ENTERING;
                long enterAnim = FbxDefaultAnimPack.loadStack(model.model, group.enter);
                model.model.setLayerLoop(0, false);
                model.model.transitionAnim(enterAnim, 0, TRANSITION_TIME);
            } else {
                ed.layerPhases[0] = AnimPhase.LOOPING;
                String loopStack;
                if (isDieState(target)) {
                    loopStack = randomDieStack(model.model);
                    if (loopStack == null) loopStack = group.loop;
                } else {
                    loopStack = group.getLoopForDir(detectMoveDirection(player));
                }
                ed.layerLoopStacks[0] = loopStack;
                long anim = FbxDefaultAnimPack.loadStack(model.model, loopStack);
                model.model.setLayerLoop(0, !isDieState(target));
                model.model.transitionAnim(anim, 0, TRANSITION_TIME);
            }
        } else if (phase == AnimPhase.LOOPING) {
            ed.stateLayers[0] = target;
            ed.layerExitStacks[0] = group.exit;
            MoveDir dir = detectMoveDirection(player);
            String loopStack = group.getLoopForDir(dir);
            if (!loopStack.equals(ed.layerLoopStacks[0])) {
                ed.layerLoopStacks[0] = loopStack;
                long anim = FbxDefaultAnimPack.loadStack(model.model, loopStack);
                model.model.transitionAnim(anim, 0, TRANSITION_TIME);
            }
        }
    }

    private static EntityAnimState.State resolveLayer0State(AbstractClientPlayer player, Model model) {
        if (player.getHealth() == 0.0f) return EntityAnimState.State.Die;
        if (player.isFallFlying()) return EntityAnimState.State.ElytraFly;
        if (player.isSleeping()) return EntityAnimState.State.Sleep;
        if (player.isPassenger()) return resolveRidingState(player);
        if (player.isSwimming()) return EntityAnimState.State.Swim;
        if (player.onClimbable()) return resolveClimbingState(player);

        if (player.isShiftKeyDown() && !player.isVisuallyCrawling() && FbxDefaultAnimPack.isAvailable()) {
            return EntityAnimState.State.Sneak;
        }

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
        if (player.hurtTime == 10 && FbxDefaultAnimPack.isAvailable()) {
            if (tryApplyHitAnimation(model)) return;
        }

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
        if (player.isUsingItem() && FbxDefaultAnimPack.isAvailable()) {
            UseAnim useAnim = player.getUseItem().getUseAnimation();
            if (useAnim == UseAnim.EAT || useAnim == UseAnim.DRINK) {
                if (tryApplyDrinkAnimation(player, model)) return;
            }
        }

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
        boolean sneakOnLayer0 = FbxDefaultAnimPack.isAvailable()
                && player.isShiftKeyDown() && !player.isVisuallyCrawling();

        if (!sneakOnLayer0 && player.isShiftKeyDown() && !player.isVisuallyCrawling()) {
            changeAnimationOnce(model, EntityAnimState.State.Sneak, 2);
        } else {
            if (model.entityData.stateLayers[2] != EntityAnimState.State.Idle) {
                model.entityData.stateLayers[2] = EntityAnimState.State.Idle;
                model.model.transitionAnim(0, 2, TRANSITION_TIME);
            }
        }
    }

    private static MoveDir detectMoveDirection(AbstractClientPlayer player) {
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;

        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) return null;

        float yaw = (float)(player.getYRot() * Math.PI / 180.0);
        float sin = (float) Math.sin(yaw);
        float cos = (float) Math.cos(yaw);

        float localFwd = (float) (-dx * sin + dz * cos);
        float localRight = (float) (dx * cos + dz * sin);

        boolean fwd   = localFwd > 0.01f;
        boolean bwd   = localFwd < -0.01f;
        boolean right  = localRight > 0.01f;
        boolean left = localRight < -0.01f;

        if (fwd && left)  return MoveDir.FORWARD_LEFT;
        if (fwd && right) return MoveDir.FORWARD_RIGHT;
        if (bwd && left)  return MoveDir.BACKWARD_LEFT;
        if (bwd && right) return MoveDir.BACKWARD_RIGHT;
        if (fwd)  return MoveDir.FORWARD;
        if (bwd)  return MoveDir.BACKWARD;
        if (left) return MoveDir.LEFT;
        if (right) return MoveDir.RIGHT;
        return null;
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
        return player.getHealth() == 0.0f || player.isFallFlying() ||
               player.isSleeping() || player.isSwimming() ||
               player.onClimbable() || player.isSprinting() ||
               player.isVisuallyCrawling() || player.isPassenger() ||
               hasMovement(player);
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

    private static final String[] DIE_SLOTS = {"die", "die2"};
    private static final String[] HIT_SLOTS = {"hit1", "hit2", "hit3", "hit4", "hit5"};

    private static boolean isDieState(EntityAnimState.State s) {
        return s == EntityAnimState.State.Die;
    }

    private static String randomDieStack(com.shiroha.mmdskin.renderer.core.IMMDModel model) {
        String slot = DIE_SLOTS[ThreadLocalRandom.current().nextInt(DIE_SLOTS.length)];
        AnimationGroup g = FbxDefaultAnimPack.getGroup(slot);
        return g != null ? g.loop : null;
    }

    private static boolean isLayerFinished(Model model, int layer) {
        NativeFunc nf = NativeFunc.GetInst();
        return nf.IsLayerAnimationFinished(model.model.getModelHandle(), layer);
    }

    private static void ensureLayer1BoneMask(Model model) {
        if (!model.entityData.layer1BoneMaskSet) {
            model.model.setLayerBoneMask(1, UPPER_BODY_BONE);
            model.entityData.layer1BoneMaskSet = true;
        }
    }

    private static boolean tryApplyHitAnimation(Model model) {
        String slot = HIT_SLOTS[ThreadLocalRandom.current().nextInt(HIT_SLOTS.length)];
        AnimationGroup g = FbxDefaultAnimPack.getGroup(slot);
        if (g == null) return false;

        long anim = FbxDefaultAnimPack.loadStack(model.model, g.loop);
        if (anim == 0) return false;

        ensureLayer1BoneMask(model);
        model.entityData.stateLayers[1] = null;
        model.model.setLayerLoop(1, false);
        model.model.transitionAnim(anim, 1, PHASE_TRANSITION_TIME);
        return true;
    }

    private static boolean tryApplyDrinkAnimation(AbstractClientPlayer player, Model model) {
        AnimationGroup drinkGroup = FbxDefaultAnimPack.getGroup("drink");
        if (drinkGroup == null) return false;

        long anim = FbxDefaultAnimPack.loadStack(model.model, drinkGroup.loop);
        if (anim == 0) return false;

        ensureLayer1BoneMask(model);
        EntityAnimState.State state = (player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                ? EntityAnimState.State.ItemRight : EntityAnimState.State.ItemLeft;

        if (model.entityData.stateLayers[1] != state) {
            model.entityData.stateLayers[1] = state;
            model.model.setLayerLoop(1, true);
            model.model.transitionAnim(anim, 1, TRANSITION_TIME);
        }
        return true;
    }

    private static void resetPhase(EntityAnimState ed, int layer) {
        ed.layerPhases[layer] = AnimPhase.NONE;
        ed.layerGroupIds[layer] = null;
        ed.layerExitStacks[layer] = null;
        ed.layerLoopStacks[layer] = null;
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
