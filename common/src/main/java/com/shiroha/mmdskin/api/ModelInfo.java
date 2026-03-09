package com.shiroha.mmdskin.api;

import java.util.Collections;
import java.util.List;

/**
 * MMD 模型信息快照（不可变）
 */

public final class ModelInfo {
    private final int boneCount;
    private final int vertexCount;
    private final int materialCount;
    private final List<String> boneNames;
    private final float[] bonePositions;

    ModelInfo(int boneCount, int vertexCount, int materialCount,
             List<String> boneNames, float[] bonePositions) {
        this.boneCount = boneCount;
        this.vertexCount = vertexCount;
        this.materialCount = materialCount;
        this.boneNames = boneNames;
        this.bonePositions = bonePositions;
    }

    public int getBoneCount() { return boneCount; }

    public int getVertexCount() { return vertexCount; }

    public int getMaterialCount() { return materialCount; }

    public List<String> getBoneNames() {
        return Collections.unmodifiableList(boneNames);
    }

    public float[] getBonePositions() {
        return bonePositions.clone();
    }

    public float[] getBonePosition(int index) {
        if (index < 0 || index >= boneCount) return null;
        int off = index * 3;
        if (off + 2 >= bonePositions.length) return null;
        return new float[]{ bonePositions[off], bonePositions[off + 1], bonePositions[off + 2] };
    }

    public int findBoneIndex(String name) {
        if (name == null) return -1;
        for (int i = 0; i < boneNames.size(); i++) {
            if (name.equals(boneNames.get(i))) return i;
        }
        return -1;
    }
}
