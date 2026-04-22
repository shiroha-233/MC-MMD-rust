package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

/** 文件职责：定义矩阵句柄与手部姿态相关的 native 能力边界。 */
public interface NativeMatrixPort {

    long createMatrix();

    void deleteMatrix(long matrixHandle);

    void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand);

    boolean copyMatrixToBuffer(long matrixHandle, ByteBuffer targetBuffer);
}
