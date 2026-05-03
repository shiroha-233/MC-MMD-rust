package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeMatrixPort;
import java.nio.ByteBuffer;

/** 文件职责：保存单个模型实例的动画层与手部矩阵状态。 */
public class EntityAnimState {

    private static final NativeMatrixPort NOOP_MATRIX_PORT = new NativeMatrixPort() {
        @Override
        public long createMatrix() {
            return 0L;
        }

        @Override
        public void deleteMatrix(long matrixHandle) {
        }

        @Override
        public void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand) {
        }

        @Override
        public boolean copyMatrixToBuffer(long matrixHandle, java.nio.ByteBuffer targetBuffer) {
            return false;
        }
    };

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
    private final NativeMatrixPort matrixPort;

    public EntityAnimState(int layerCount, NativeMatrixPort matrixPort) {
        this.matrixPort = matrixPort != null ? matrixPort : NOOP_MATRIX_PORT;
        this.stateLayers = new State[layerCount];
        this.playCustomAnim = false;
        this.rightHandMat = this.matrixPort.createMatrix();
        this.leftHandMat = this.matrixPort.createMatrix();
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
        if (rightHandMat != 0) {
            matrixPort.deleteMatrix(rightHandMat);
            rightHandMat = 0;
        }
        if (leftHandMat != 0) {
            matrixPort.deleteMatrix(leftHandMat);
            leftHandMat = 0;
        }
    }

    public static String getPropertyName(State state) {
        return state.propertyName;
    }
}
