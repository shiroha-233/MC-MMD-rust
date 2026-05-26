#version 430 core
layout(local_size_x = 256) in;

// 输入数据（只读）
layout(std430, binding = 0) readonly buffer OriginalPositions {
    float origPositions[];
};
layout(std430, binding = 1) readonly buffer OriginalNormals {
    float origNormals[];
};
layout(std430, binding = 2) readonly buffer BoneIndicesBuffer {
    int boneIndices[];
};
layout(std430, binding = 3) readonly buffer BoneWeightsBuffer {
    float boneWeights[];
};
layout(std430, binding = 4) readonly buffer BoneMatrices {
    mat4 boneMatrices[];
};
// 顶点 Morph 数据（只读）
layout(std430, binding = 5) readonly buffer MorphOffsets {
    float morphOffsets[];
};
layout(std430, binding = 6) readonly buffer MorphWeights {
    float morphWeights[];
};
// 输出数据（写入）
layout(std430, binding = 7) writeonly buffer SkinnedPositions {
    float skinnedPositions[];
};
layout(std430, binding = 8) writeonly buffer SkinnedNormals {
    float skinnedNormals[];
};
// UV Morph 数据（只读）
layout(std430, binding = 9) readonly buffer OriginalUVs {
    float origUVs[];
};
layout(std430, binding = 10) readonly buffer UvMorphOffsets {
    float uvMorphOffsets[];
};
layout(std430, binding = 11) readonly buffer UvMorphWeights {
    float uvMorphWeights[];
};
// 蒙皮后 UV（写入）
layout(std430, binding = 12) writeonly buffer SkinnedUVs {
    float skinnedUVs[];
};
uniform int VertexCount;
uniform int MorphCount;
uniform int MaxBones;
uniform int UvMorphCount;

void main() {
    uint vid = gl_GlobalInvocationID.x;
    if (vid >= VertexCount) return;

    uint base3 = vid * 3;
    uint base4 = vid * 4;
    uint base2 = vid * 2;

    // 读取原始位置和法线
    vec3 pos = vec3(origPositions[base3], origPositions[base3 + 1], origPositions[base3 + 2]);
    vec3 nor = vec3(origNormals[base3], origNormals[base3 + 1], origNormals[base3 + 2]);

    // 应用顶点 Morph 偏移
    if (MorphCount > 0) {
        for (int m = 0; m < MorphCount && m < 128; m++) {
            float w = morphWeights[m];
            if (w > 0.001) {
                uint offsetIdx = uint(m) * uint(VertexCount) * 3u + vid * 3u;
                pos.x += morphOffsets[offsetIdx] * w;
                pos.y += morphOffsets[offsetIdx + 1u] * w;
                pos.z += morphOffsets[offsetIdx + 2u] * w;
            }
        }
    }

    // 读取骨骼数据
    ivec4 bi = ivec4(
            boneIndices[base4], boneIndices[base4 + 1],
            boneIndices[base4 + 2], boneIndices[base4 + 3]
    );
    vec4 bw = vec4(
            boneWeights[base4], boneWeights[base4 + 1],
            boneWeights[base4 + 2], boneWeights[base4 + 3]
    );

    // 计算蒙皮矩阵（归一化权重）
    float totalWeight = 0.0;
    for (int i = 0; i < 4; i++) {
        if (bi[i] >= 0 && bi[i] < MaxBones) {
            totalWeight += bw[i];
        }
    }

    mat4 skinMatrix = mat4(0.0);
    if (totalWeight > 0.001) {
        float invWeight = 1.0 / totalWeight;
        for (int i = 0; i < 4; i++) {
            if (bi[i] >= 0 && bi[i] < MaxBones && bw[i] > 0.0) {
                skinMatrix += boneMatrices[bi[i]] * (bw[i] * invWeight);
            }
        }
    } else {
        skinMatrix = mat4(1.0);
    }

    // 应用蒙皮变换
    vec4 skinnedPos = skinMatrix * vec4(pos, 1.0);
    mat3 normalMat = mat3(skinMatrix);
    vec3 skinnedNor = normalize(normalMat * nor);

    // 写入位置/法线输出
    skinnedPositions[base3] = skinnedPos.x;
    skinnedPositions[base3 + 1] = skinnedPos.y;
    skinnedPositions[base3 + 2] = skinnedPos.z;

    skinnedNormals[base3] = skinnedNor.x;
    skinnedNormals[base3 + 1] = skinnedNor.y;
    skinnedNormals[base3 + 2] = skinnedNor.z;

    // 应用 UV Morph 偏移并写入输出（UvMorphCount < 0 表示无 UV 处理）
    if (UvMorphCount >= 0) {
        float u = origUVs[base2];
        float v = origUVs[base2 + 1];
        if (UvMorphCount > 0) {
            for (int m = 0; m < UvMorphCount && m < 32; m++) {
                float w = uvMorphWeights[m];
                if (abs(w) > 0.001) {
                    uint uvIdx = uint(m) * uint(VertexCount) * 2u + vid * 2u;
                    u += uvMorphOffsets[uvIdx] * w;
                    v += uvMorphOffsets[uvIdx + 1u] * w;
                }
            }
        }
        skinnedUVs[base2] = u;
        skinnedUVs[base2 + 1] = v;
    }
}
