package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.NativeFunc;

import java.nio.ByteBuffer;

public class EntityAnimState {

    public enum State {
        Idle("idle"), Walk("walk"), Sprint("sprint"), Air("air"),
        OnClimbable("onClimbable"), OnClimbableUp("onClimbableUp"), OnClimbableDown("onClimbableDown"),
        Swim("swim"), Ride("ride"), Ridden("ridden"), Driven("driven"),
        Sleep("sleep"), ElytraFly("elytraFly"), Die("die"),
        SwingRight("swingRight"), SwingLeft("swingLeft"), ItemRight("itemRight"), ItemLeft("itemLeft"),
        Sneak("sneak"), OnHorse("onHorse"), Crawl("crawl"), LieDown("lieDown");

        public final String propertyName;

        State(String propertyName) {
            this.propertyName = propertyName;
        }
    }

    public enum AnimPhase { NONE, ENTERING, LOOPING, EXITING }

    public boolean playCustomAnim;
    public boolean playStageAnim;
    public long rightHandMat;
    public long leftHandMat;
    public State[] stateLayers;
    public ByteBuffer matBuffer;
    public AnimPhase[] layerPhases;
    public String[] layerAnimationKeys;
    public String[] layerGroupIds;
    public String[] layerExitStacks;
    public String[] layerLoopStacks;
    public boolean layer1BoneMaskSet;

    public EntityAnimState(int layerCount) {
        NativeFunc nf = NativeFunc.GetInst();
        this.stateLayers = new State[layerCount];
        this.playCustomAnim = false;
        this.rightHandMat = nf.CreateMat();
        this.leftHandMat = nf.CreateMat();
        this.matBuffer = ByteBuffer.allocateDirect(64);
        this.layerPhases = new AnimPhase[layerCount];
        this.layerAnimationKeys = new String[layerCount];
        this.layerGroupIds = new String[layerCount];
        this.layerExitStacks = new String[layerCount];
        this.layerLoopStacks = new String[layerCount];
        for (int i = 0; i < layerCount; i++) {
            layerPhases[i] = AnimPhase.NONE;
        }
    }

    public void invalidateStateLayers() {
        for (int i = 0; i < stateLayers.length; i++) {
            stateLayers[i] = null;
            layerPhases[i] = AnimPhase.NONE;
            layerAnimationKeys[i] = null;
            layerGroupIds[i] = null;
            layerExitStacks[i] = null;
            layerLoopStacks[i] = null;
        }
    }

    public void dispose() {
        NativeFunc nf = NativeFunc.GetInst();
        if (rightHandMat != 0) {
            nf.DeleteMat(rightHandMat);
            rightHandMat = 0;
        }
        if (leftHandMat != 0) {
            nf.DeleteMat(leftHandMat);
            leftHandMat = 0;
        }
    }

    public static String getPropertyName(State state) {
        return state.propertyName;
    }
}
