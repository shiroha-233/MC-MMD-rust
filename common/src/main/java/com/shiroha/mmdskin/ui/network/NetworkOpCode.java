package com.shiroha.mmdskin.ui.network;

/**
 * 网络操作码常量定义
 */
public final class NetworkOpCode {
    public static final int CUSTOM_ANIM = 1;
    public static final int RESET_PHYSICS = 2;
    public static final int MODEL_SELECT = 3;
    public static final int MAID_MODEL = 4;
    public static final int MAID_ACTION = 5;
    public static final int MORPH_SYNC = 6;
    public static final int REQUEST_ALL_MODELS = 10;
    public static final int STAGE_MULTI = 11;
    public static final int BONE_SYNC = 12;


    public static boolean isStringPayload(int opCode) {
        return opCode == CUSTOM_ANIM || opCode == MODEL_SELECT
                || opCode == MORPH_SYNC || opCode == REQUEST_ALL_MODELS
                || opCode == STAGE_MULTI;
    }

    
    public static boolean isEntityStringPayload(int opCode) {
        return opCode == MAID_MODEL || opCode == MAID_ACTION;
    }

    private NetworkOpCode() {}
}
