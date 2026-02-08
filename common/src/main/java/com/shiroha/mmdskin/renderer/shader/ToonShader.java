package com.shiroha.mmdskin.renderer.shader;

import org.lwjgl.opengl.GL46C;

import java.nio.FloatBuffer;

/**
 * GPU 蒙皮版本的 Toon 着色器
 * 
 * 继承 ToonShaderBase，提供包含骨骼蒙皮的顶点着色器。
 * 使用 SSBO 存储骨骼矩阵，在 GPU 上完成蒙皮计算。
 * 
 * 用于 MMDModelGpuSkinning（GPU 蒙皮模式）
 */
public class ToonShader extends ToonShaderBase {
    
    // GPU 蒙皮特有的资源
    private int boneMatrixSSBO = 0;
    
    // GPU 蒙皮特有的 Uniform locations
    private int morphCountLocation = -1;
    private int vertexCountLocation = -1;
    
    // GPU 蒙皮特有的 Attribute locations
    private int boneIndicesLocation = -1;
    private int boneWeightsLocation = -1;
    private int outlineBoneIndicesLocation = -1;
    private int outlineBoneWeightsLocation = -1;
    
    public static int MAX_BONES = 2048;
    
    // ==================== 主着色器（卡通着色） ====================
    private static final String MAIN_VERTEX_SHADER = """
        #version 460 core
        
        layout(location = 0) in vec3 Position;
        layout(location = 1) in vec3 Normal;
        layout(location = 2) in vec2 UV0;
        layout(location = 3) in ivec4 BoneIndices;
        layout(location = 4) in vec4 BoneWeights;
        
        layout(std430, binding = 0) readonly buffer BoneMatrices {
            mat4 boneMatrices[2048];
        };
        
        layout(std430, binding = 1) readonly buffer MorphOffsets {
            float morphOffsets[];
        };
        
        layout(std430, binding = 2) readonly buffer MorphWeights {
            float morphWeights[];
        };
        
        uniform mat4 ProjMat;
        uniform mat4 ModelViewMat;
        uniform int MorphCount;
        uniform int VertexCount;
        
        out vec2 texCoord0;
        out vec3 viewNormal;
        out vec3 viewPos;
        
        void main() {
            // 计算蒙皮矩阵
            float totalWeight = 0.0;
            for (int i = 0; i < 4; i++) {
                int boneIdx = BoneIndices[i];
                if (boneIdx >= 0 && boneIdx < 2048) {
                    totalWeight += BoneWeights[i];
                }
            }
            
            mat4 skinMatrix = mat4(0.0);
            if (totalWeight > 0.001) {
                float invWeight = 1.0 / totalWeight;
                for (int i = 0; i < 4; i++) {
                    int boneIdx = BoneIndices[i];
                    float weight = BoneWeights[i];
                    if (boneIdx >= 0 && boneIdx < 2048 && weight > 0.0) {
                        skinMatrix += boneMatrices[boneIdx] * (weight * invWeight);
                    }
                }
            } else {
                skinMatrix = mat4(1.0);
            }
            
            // 先应用 Morph 偏移
            vec3 morphedPos = Position;
            if (MorphCount > 0 && VertexCount > 0) {
                uint vid = uint(gl_VertexID);
                for (int m = 0; m < MorphCount && m < 128; m++) {
                    float w = morphWeights[m];
                    if (w > 0.001) {
                        uint offsetIdx = m * VertexCount * 3 + vid * 3;
                        morphedPos.x += morphOffsets[offsetIdx] * w;
                        morphedPos.y += morphOffsets[offsetIdx + 1] * w;
                        morphedPos.z += morphOffsets[offsetIdx + 2] * w;
                    }
                }
            }
            
            vec4 skinnedPos = skinMatrix * vec4(morphedPos, 1.0);
            vec4 viewPosition = ModelViewMat * skinnedPos;
            
            mat3 normalMatrix = mat3(ModelViewMat) * mat3(skinMatrix);
            vec3 skinnedNormal = normalMatrix * Normal;
            
            gl_Position = ProjMat * viewPosition;
            texCoord0 = UV0;
            viewNormal = normalize(skinnedNormal);
            viewPos = viewPosition.xyz;
        }
        """;
    
    // ==================== 描边着色器（GPU 蒙皮版） ====================
    private static final String OUTLINE_VERTEX_SHADER = """
        #version 460 core
        
        layout(location = 0) in vec3 Position;
        layout(location = 1) in vec3 Normal;
        layout(location = 3) in ivec4 BoneIndices;
        layout(location = 4) in vec4 BoneWeights;
        
        layout(std430, binding = 0) readonly buffer BoneMatrices {
            mat4 boneMatrices[2048];
        };
        
        uniform mat4 ProjMat;
        uniform mat4 ModelViewMat;
        uniform float OutlineWidth;
        
        out vec3 viewNormal;
        out vec3 viewPos;
        
        void main() {
            // 计算蒙皮矩阵
            float totalWeight = 0.0;
            for (int i = 0; i < 4; i++) {
                int boneIdx = BoneIndices[i];
                if (boneIdx >= 0 && boneIdx < 2048) {
                    totalWeight += BoneWeights[i];
                }
            }
            
            mat4 skinMatrix = mat4(0.0);
            if (totalWeight > 0.001) {
                float invWeight = 1.0 / totalWeight;
                for (int i = 0; i < 4; i++) {
                    int boneIdx = BoneIndices[i];
                    float weight = BoneWeights[i];
                    if (boneIdx >= 0 && boneIdx < 2048 && weight > 0.0) {
                        skinMatrix += boneMatrices[boneIdx] * (weight * invWeight);
                    }
                }
            } else {
                skinMatrix = mat4(1.0);
            }
            
            vec4 skinnedPos = skinMatrix * vec4(Position, 1.0);
            mat3 normalMatrix = mat3(ModelViewMat) * mat3(skinMatrix);
            vec3 skinnedNormal = normalize(normalMatrix * Normal);
            
            // 沿法线方向扩张顶点（背面扩张法）
            vec4 vPos = ModelViewMat * skinnedPos;
            vPos.xyz += skinnedNormal * OutlineWidth;
            
            gl_Position = ProjMat * vPos;
            viewNormal = skinnedNormal;
            viewPos = vPos.xyz;
        }
        """;
    
    // ==================== 实现抽象方法 ====================
    
    @Override
    protected String getGlslVersion() {
        return "#version 460 core";
    }
    
    @Override
    protected String getMainVertexShader() {
        return MAIN_VERTEX_SHADER;
    }
    
    @Override
    protected String getOutlineVertexShader() {
        return OUTLINE_VERTEX_SHADER;
    }
    
    @Override
    protected void onInitialized() {
        // 获取 GPU 蒙皮特有的 uniform/attribute locations
        morphCountLocation = GL46C.glGetUniformLocation(mainProgram, "MorphCount");
        vertexCountLocation = GL46C.glGetUniformLocation(mainProgram, "VertexCount");
        
        boneIndicesLocation = GL46C.glGetAttribLocation(mainProgram, "BoneIndices");
        boneWeightsLocation = GL46C.glGetAttribLocation(mainProgram, "BoneWeights");
        outlineBoneIndicesLocation = GL46C.glGetAttribLocation(outlineProgram, "BoneIndices");
        outlineBoneWeightsLocation = GL46C.glGetAttribLocation(outlineProgram, "BoneWeights");
        
        // 创建骨骼矩阵 SSBO
        boneMatrixSSBO = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, boneMatrixSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, MAX_BONES * 64, GL46C.GL_DYNAMIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 0, boneMatrixSSBO);
    }
    
    @Override
    protected String getShaderName() {
        return "ToonShader(GPU)";
    }
    
    public void uploadBoneMatrices(FloatBuffer matrices, int boneCount) {
        if (!initialized || boneMatrixSSBO == 0) return;
        
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 0, boneMatrixSSBO);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, boneMatrixSSBO);
        
        int actualBones = Math.min(boneCount, MAX_BONES);
        matrices.limit(actualBones * 16);
        matrices.position(0);
        GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, matrices);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    // ==================== GPU 蒙皮特有方法 ====================
    
    public void setMorphParams(int morphCount, int vertexCount) {
        if (morphCountLocation >= 0) {
            GL46C.glUniform1i(morphCountLocation, morphCount);
        }
        if (vertexCountLocation >= 0) {
            GL46C.glUniform1i(vertexCountLocation, vertexCount);
        }
    }
    
    // ==================== GPU 蒙皮特有 Getters ====================
    
    public int getBoneIndicesLocation() { return boneIndicesLocation; }
    public int getBoneWeightsLocation() { return boneWeightsLocation; }
    public int getOutlineBoneIndicesLocation() { return outlineBoneIndicesLocation; }
    public int getOutlineBoneWeightsLocation() { return outlineBoneWeightsLocation; }
    
    @Override
    public void cleanup() {
        super.cleanup();
        if (boneMatrixSSBO > 0) {
            GL46C.glDeleteBuffers(boneMatrixSSBO);
            boneMatrixSSBO = 0;
        }
    }
}
