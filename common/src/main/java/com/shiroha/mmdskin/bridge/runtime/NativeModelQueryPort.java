package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

/** 文件职责：集中定义模型查询与调试读取相关的 native 能力。 */
public interface NativeModelQueryPort {

    int getBoneCount(long modelHandle);

    long getVertexCount(long modelHandle);

    long getIndexCount(long modelHandle);

    String getBoneNames(long modelHandle);

    int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer);

    int getVertexMorphCount(long modelHandle);

    int getUvMorphCount(long modelHandle);

    long getGpuMorphOffsetsSize(long modelHandle);

    long getGpuUvMorphOffsetsSize(long modelHandle);

    int getMorphCount(long modelHandle);

    String getMorphName(long modelHandle, int morphIndex);

    boolean isVrmModel(long modelHandle);

    String getMaterialName(long modelHandle, int materialIndex);

    boolean isMaterialVisible(long modelHandle, int materialIndex);
}
