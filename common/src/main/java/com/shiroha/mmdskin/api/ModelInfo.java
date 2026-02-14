package com.shiroha.mmdskin.api;

import java.util.Collections;
import java.util.List;

/**
 * MMD 模型信息快照（不可变）
 *
 * 包含骨骼、顶点、材质的元数据以及实时骨骼位置。
 * 通过 {@link MmdSkinApi#getModelInfo(net.minecraft.world.entity.player.Player)} 获取。
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

    /** 骨骼数量 */
    public int getBoneCount() { return boneCount; }

    /** 顶点数量 */
    public int getVertexCount() { return vertexCount; }

    /** 材质数量 */
    public int getMaterialCount() { return materialCount; }

    /**
     * 骨骼名称列表（按索引顺序）
     * @return 不可变列表
     */
    public List<String> getBoneNames() {
        return Collections.unmodifiableList(boneNames);
    }

    /**
     * 所有骨骼的实时世界位置（经过动画/物理更新后）
     * 布局：[x0, y0, z0, x1, y1, z1, ...]，长度 = boneCount * 3
     *
     * @return 防御性拷贝数组
     */
    public float[] getBonePositions() {
        return bonePositions.clone();
    }

    /**
     * 获取指定骨骼的实时位置
     *
     * @param index 骨骼索引
     * @return [x, y, z]，索引越界时返回 null
     */
    public float[] getBonePosition(int index) {
        if (index < 0 || index >= boneCount) return null;
        int off = index * 3;
        if (off + 2 >= bonePositions.length) return null;
        return new float[]{ bonePositions[off], bonePositions[off + 1], bonePositions[off + 2] };
    }

    /**
     * 按名称查找骨骼索引
     *
     * @param name 骨骼名称
     * @return 骨骼索引，未找到返回 -1
     */
    public int findBoneIndex(String name) {
        if (name == null) return -1;
        for (int i = 0; i < boneNames.size(); i++) {
            if (name.equals(boneNames.get(i))) return i;
        }
        return -1;
    }
}
