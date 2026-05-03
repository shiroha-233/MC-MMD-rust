package com.shiroha.mmdskin.bridge.runtime;

import java.nio.ByteBuffer;

/** 文件职责：集中定义模型查询与调试读取相关的 native 能力。 */
public interface NativeModelQueryPort {

    NativeModelQueryPort NOOP = new NativeModelQueryPort() {
        @Override
        public int getMaterialCount(long modelHandle) {
            return 0;
        }

        @Override
        public int getBoneCount(long modelHandle) {
            return 0;
        }

        @Override
        public long getVertexCount(long modelHandle) {
            return 0L;
        }

        @Override
        public long getIndexCount(long modelHandle) {
            return 0L;
        }

        @Override
        public String getBoneNames(long modelHandle) {
            return "[]";
        }

        @Override
        public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return 0;
        }

        @Override
        public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return 0;
        }

        @Override
        public int getVertexMorphCount(long modelHandle) {
            return 0;
        }

        @Override
        public int getUvMorphCount(long modelHandle) {
            return 0;
        }

        @Override
        public long getGpuMorphOffsetsSize(long modelHandle) {
            return 0L;
        }

        @Override
        public long getGpuUvMorphOffsetsSize(long modelHandle) {
            return 0L;
        }

        @Override
        public int getMorphCount(long modelHandle) {
            return 0;
        }

        @Override
        public String getMorphName(long modelHandle, int morphIndex) {
            return "";
        }

        @Override
        public boolean isVrmModel(long modelHandle) {
            return false;
        }

        @Override
        public String getMaterialName(long modelHandle, int materialIndex) {
            return "";
        }

        @Override
        public boolean isMaterialVisible(long modelHandle, int materialIndex) {
            return false;
        }
    };

    static NativeModelQueryPort noop() {
        return NOOP;
    }

    int getMaterialCount(long modelHandle);

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
