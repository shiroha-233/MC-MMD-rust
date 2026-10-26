package com.shiroha.mmdskin.ui.network;

/**
 * 网络操作码常量定义
 */
public final class NetworkOpCode {
    public static final int CUSTOM_ANIM = 1;
    public static final int RESET_PHYSICS = 2;
    public static final int MODEL_SELECT = 3;
    public static final int MORPH_SYNC = 6;
    public static final int REQUEST_ALL_MODELS = 10;
    public static final int STAGE_MULTI = 11;
    public static final int BONE_SYNC = 12;

    // ===== 模型分发（房主主动授权后，服务器把模型文件分发给客户端） =====
    /** S→C：可分发模型清单。 */
    public static final int DISTRIB_MANIFEST = 20;
    /** S→C：单个模型的详细清单（文件列表 + 大小 + MD5）。 */
    public static final int DISTRIB_OFFER = 21;
    /** S→C：文件分块数据。 */
    public static final int DISTRIB_CHUNK = 22;
    /** C→S：客户端请求某个模型（带断点续传偏移）。 */
    public static final int DISTRIB_REQUEST = 23;
    /** S→C：传输结束。 */
    public static final int DISTRIB_COMPLETE = 24;

    public static boolean isStringPayload(int opCode) {
        return opCode == CUSTOM_ANIM || opCode == MODEL_SELECT
                || opCode == MORPH_SYNC || opCode == REQUEST_ALL_MODELS
                || opCode == STAGE_MULTI
                || opCode == DISTRIB_REQUEST || opCode == DISTRIB_COMPLETE;
    }

    /** 模型分发的二进制载荷（清单 / 详细清单 / 分块）。 */
    public static boolean isDistributionBinaryPayload(int opCode) {
        return opCode == DISTRIB_MANIFEST || opCode == DISTRIB_OFFER || opCode == DISTRIB_CHUNK;
    }

    /** 是否属于模型分发协议。 */
    public static boolean isDistribution(int opCode) {
        return opCode >= DISTRIB_MANIFEST && opCode <= DISTRIB_COMPLETE;
    }

    private NetworkOpCode() {}
}
