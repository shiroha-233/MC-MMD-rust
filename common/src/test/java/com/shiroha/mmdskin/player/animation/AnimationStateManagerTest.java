package com.shiroha.mmdskin.player.animation;

import net.minecraft.world.item.UseAnim;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationStateManagerTest {
    @Test
    void shouldResolveDrinkAnimationForEatAndDrinkTriggers() {
        assertEquals("Drink", AnimationStateManager.resolveUseTriggerAnimationName(UseAnim.DRINK));
        assertEquals("Drink", AnimationStateManager.resolveUseTriggerAnimationName(UseAnim.EAT));
    }

    @Test
    void shouldIgnoreNonConsumableUseTriggers() {
        assertNull(AnimationStateManager.resolveUseTriggerAnimationName(UseAnim.BOW));
        assertNull(AnimationStateManager.resolveUseTriggerAnimationName(UseAnim.BLOCK));
        assertNull(AnimationStateManager.resolveUseTriggerAnimationName(UseAnim.NONE));
    }

    @Test
    void shouldFallbackToOppositeHandAnimationWhenUsingBow() {
        assertEquals(
                List.of("itemActive_minecraft.bow_Right_using", "itemActive_minecraft.bow_Left_using"),
                AnimationStateManager.resolveItemAnimationKeys("minecraft.bow", "Right", UseAnim.BOW, "using")
        );
    }

    @Test
    void shouldKeepSingleLookupForNormalUsingItems() {
        assertEquals(
                List.of("itemActive_minecraft.shield_Right_using"),
                AnimationStateManager.resolveItemAnimationKeys("minecraft.shield", "Right", UseAnim.BLOCK, "using")
        );
    }

    @Test
    void shouldApplyTaczRifleHoldOnlyForHealthyAwakeGunHolders() {
        assertTrue(AnimationStateManager.shouldApplyTaczRifleHold(true, false, 0));
        assertFalse(AnimationStateManager.shouldApplyTaczRifleHold(false, false, 0));
        assertFalse(AnimationStateManager.shouldApplyTaczRifleHold(true, true, 0));
        assertFalse(AnimationStateManager.shouldApplyTaczRifleHold(true, false, 1));
    }
}
