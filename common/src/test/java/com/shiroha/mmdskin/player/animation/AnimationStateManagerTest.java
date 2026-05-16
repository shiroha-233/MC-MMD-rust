package com.shiroha.mmdskin.player.animation;

import net.minecraft.world.item.ItemUseAnimation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationStateManagerTest {
    @Test
    void shouldResolveDrinkAnimationForEatAndDrinkTriggers() {
        assertEquals("Drink", AnimationStateManager.resolveUseTriggerAnimationName(ItemUseAnimation.DRINK));
        assertEquals("Drink", AnimationStateManager.resolveUseTriggerAnimationName(ItemUseAnimation.EAT));
    }

    @Test
    void shouldIgnoreNonConsumableUseTriggers() {
        assertNull(AnimationStateManager.resolveUseTriggerAnimationName(ItemUseAnimation.BOW));
        assertNull(AnimationStateManager.resolveUseTriggerAnimationName(ItemUseAnimation.BLOCK));
        assertNull(AnimationStateManager.resolveUseTriggerAnimationName(ItemUseAnimation.NONE));
    }

    @Test
    void shouldFallbackToOppositeHandAnimationWhenUsingBow() {
        assertEquals(
                List.of("itemActive_minecraft.bow_Right_using", "itemActive_minecraft.bow_Left_using"),
                AnimationStateManager.resolveItemAnimationKeys("minecraft.bow", "Right", ItemUseAnimation.BOW, "using")
        );
    }

    @Test
    void shouldKeepSingleLookupForNormalUsingItems() {
        assertEquals(
                List.of("itemActive_minecraft.shield_Right_using"),
                AnimationStateManager.resolveItemAnimationKeys("minecraft.shield", "Right", ItemUseAnimation.BLOCK, "using")
        );
    }
}
