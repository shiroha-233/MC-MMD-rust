package com.shiroha.mmdskin.render.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPersonPoseStateTest {

    @Test
    void worldPassShouldKeepPreparedPoseUntilFirstPersonDraw() {
        FirstPersonPoseState state = new FirstPersonPoseState();

        state.beginPreparation();
        state.markPrepared();
        state.finishRender(false);

        assertTrue(state.isPrepared());
        state.finishRender(true);
        assertFalse(state.isPrepared());
    }

    @Test
    void nextPreparationShouldReplaceUnconsumedPose() {
        FirstPersonPoseState state = new FirstPersonPoseState();

        state.markPrepared();
        state.beginPreparation();

        assertFalse(state.isPrepared());
        state.markPrepared();
        assertTrue(state.isPrepared());
    }

    @Test
    void discardShouldReleasePreparedPose() {
        FirstPersonPoseState state = new FirstPersonPoseState();

        state.markPrepared();
        state.discard();

        assertFalse(state.isPrepared());
    }
}
