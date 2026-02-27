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
    public static final int STAGE_START = 7;
    public static final int STAGE_END = 8;
    public static final int STAGE_AUDIO = 9;
    public static final int REQUEST_ALL_MODELS = 10;
    public static final int STAGE_MULTI = 11;
    public static final int BONE_SYNC = 12;

    /** 判断该 opCode 的载荷是否为纯字符串 */
    public static boolean isStringPayload(int opCode) {
        return opCode == CUSTOM_ANIM || opCode == MODEL_SELECT
                || opCode == MORPH_SYNC || opCode == STAGE_START
                || opCode == STAGE_END || opCode == STAGE_AUDIO
                || opCode == REQUEST_ALL_MODELS || opCode == STAGE_MULTI;
    }

    /** 判断该 opCode 的载荷是否为 entityId + 字符串 */
    public static boolean isEntityStringPayload(int opCode) {
        return opCode == MAID_MODEL || opCode == MAID_ACTION;
    }

    private NetworkOpCode() {}
}
