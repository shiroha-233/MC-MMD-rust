package com.shiroha.mmdskin.renderer.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;

import java.nio.FloatBuffer;

/**
 * GPU 蒙皮着色器
 * 将蒙皮计算从 CPU 移到 GPU，大幅提升大面数模型性能
 * 
 * 原理：
 * - 骨骼矩阵通过 SSBO 传递到 GPU
 * - 顶点着色器中根据骨骼索引和权重计算蒙皮
 * - 支持 BDEF1/BDEF2/BDEF4 权重类型
 */
public class GpuSkinningShader {
    private static final Logger logger = LogManager.getLogger();
    
    private int program = 0;
    private int boneMatrixSSBO = 0;
    private int morphOffsetsSSBO = 0;
    private int morphWeightsSSBO = 0;
    private boolean initialized = false;
    private int morphCountLocation = -1;
    private int vertexCountLocation = -1;
    
    // Uniform locations
    private int projMatLocation = -1;
    private int modelViewMatLocation = -1;
    private int sampler0Location = -1;
    private int lightIntensityLocation = -1;
    
    // Attribute locations
    private int positionLocation = -1;
    private int normalLocation = -1;
    private int uv0Location = -1;
    private int boneIndicesLocation = -1;
    private int boneWeightsLocation = -1;
    
    // 最大骨骼数量（通过配置设置）
    public static int MAX_BONES = 2048;
    
    // 顶点着色器源码
    private static final String VERTEX_SHADER = """
        #version 460 core
        
        // 顶点属性
        layout(location = 0) in vec3 Position;
        layout(location = 1) in vec3 Normal;
        layout(location = 2) in vec2 UV0;
        layout(location = 3) in ivec4 BoneIndices;
        layout(location = 4) in vec4 BoneWeights;
        
        // 骨骼矩阵 SSBO
        layout(std430, binding = 0) readonly buffer BoneMatrices {
            mat4 boneMatrices[2048];
        };
        
        // Morph 偏移 SSBO (morphCount * vertexCount * 3)
        layout(std430, binding = 1) readonly buffer MorphOffsets {
            float morphOffsets[];
        };
        
        // Morph 权重 SSBO
        layout(std430, binding = 2) readonly buffer MorphWeights {
            float morphWeights[];
        };
        
        // Uniforms
        uniform mat4 ProjMat;
        uniform mat4 ModelViewMat;
        uniform float LightIntensity;
        uniform int MorphCount;
        uniform int VertexCount;
        
        // 输出到片段着色器
        out vec2 texCoord0;
        out float lightFactor;
        out vec3 viewNormal;
        
        void main() {
            // 先计算总权重
            float totalWeight = 0.0;
            for (int i = 0; i < 4; i++) {
                int boneIdx = BoneIndices[i];
                if (boneIdx >= 0 && boneIdx < 2048) {
                    totalWeight += BoneWeights[i];
                }
            }
            
            // 计算蒙皮矩阵（使用归一化权重）
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
                // 没有有效权重，使用单位矩阵
                skinMatrix = mat4(1.0);
            }
            
            // 先应用 Morph 偏移
            vec3 morphedPos = Position;
            if (MorphCount > 0 && VertexCount > 0) {
                uint vid = uint(gl_VertexID);
                for (int m = 0; m < MorphCount && m < 128; m++) {
                    float weight = morphWeights[m];
                    if (weight > 0.001) {
                        uint offsetIdx = m * VertexCount * 3 + vid * 3;
                        morphedPos.x += morphOffsets[offsetIdx] * weight;
                        morphedPos.y += morphOffsets[offsetIdx + 1] * weight;
                        morphedPos.z += morphOffsets[offsetIdx + 2] * weight;
                    }
                }
            }
            
            // 应用蒙皮变换
            vec4 skinnedPos = skinMatrix * vec4(morphedPos, 1.0);
            
            // 法线变换：使用蒙皮矩阵的3x3部分（简化处理）
            mat3 normalMatrix = mat3(ModelViewMat) * mat3(skinMatrix);
            vec3 skinnedNormal = normalMatrix * Normal;
            
            gl_Position = ProjMat * ModelViewMat * skinnedPos;
            
            texCoord0 = UV0;
            lightFactor = LightIntensity;
            viewNormal = normalize(skinnedNormal);
        }
        """;
    
    // 片段着色器源码
    private static final String FRAGMENT_SHADER = """
        #version 460 core
        
        in vec2 texCoord0;
        in float lightFactor;
        in vec3 viewNormal;
        
        uniform sampler2D Sampler0;
        
        out vec4 fragColor;
        
        void main() {
            vec4 texColor = texture(Sampler0, texCoord0);
            
            // 视图空间光照（与 Minecraft 一致）
            vec3 normal = normalize(viewNormal);
            vec3 lightDir0 = normalize(vec3(0.2, 1.0, -0.7));
            vec3 lightDir1 = normalize(vec3(-0.2, 1.0, 0.7));
            
            float diffuse0 = max(dot(normal, lightDir0), 0.0);
            float diffuse1 = max(dot(normal, lightDir1), 0.0);
            float ambient = 0.4;
            float lighting = min(ambient + diffuse0 * 0.6 + diffuse1 * 0.3, 1.0);
            
            // 应用光照
            vec3 finalColor = texColor.rgb * lightFactor * lighting;
            fragColor = vec4(finalColor, texColor.a);
            
            // 透明度裁剪（降低阈值）
            if (fragColor.a < 0.004) discard;
        }
        """;
    
    public boolean init() {
        if (initialized) return true;
        
        try {
            // 编译顶点着色器
            int vertexShader = GL46C.glCreateShader(GL46C.GL_VERTEX_SHADER);
            GL46C.glShaderSource(vertexShader, VERTEX_SHADER);
            GL46C.glCompileShader(vertexShader);
            
            if (GL46C.glGetShaderi(vertexShader, GL46C.GL_COMPILE_STATUS) == GL46C.GL_FALSE) {
                String log = GL46C.glGetShaderInfoLog(vertexShader, 8192).trim();
                logger.error("GPU蒙皮顶点着色器编译失败: {}", log);
                GL46C.glDeleteShader(vertexShader);
                return false;
            }
            
            // 编译片段着色器
            int fragShader = GL46C.glCreateShader(GL46C.GL_FRAGMENT_SHADER);
            GL46C.glShaderSource(fragShader, FRAGMENT_SHADER);
            GL46C.glCompileShader(fragShader);
            
            if (GL46C.glGetShaderi(fragShader, GL46C.GL_COMPILE_STATUS) == GL46C.GL_FALSE) {
                String log = GL46C.glGetShaderInfoLog(fragShader, 8192).trim();
                logger.error("GPU蒙皮片段着色器编译失败: {}", log);
                GL46C.glDeleteShader(vertexShader);
                GL46C.glDeleteShader(fragShader);
                return false;
            }
            
            // 链接程序
            program = GL46C.glCreateProgram();
            GL46C.glAttachShader(program, vertexShader);
            GL46C.glAttachShader(program, fragShader);
            GL46C.glLinkProgram(program);
            
            if (GL46C.glGetProgrami(program, GL46C.GL_LINK_STATUS) == GL46C.GL_FALSE) {
                String log = GL46C.glGetProgramInfoLog(program, 8192);
                logger.error("GPU蒙皮着色器链接失败: {}", log);
                GL46C.glDeleteProgram(program);
                GL46C.glDeleteShader(vertexShader);
                GL46C.glDeleteShader(fragShader);
                program = 0;
                return false;
            }
            
            // 清理着色器对象
            GL46C.glDeleteShader(vertexShader);
            GL46C.glDeleteShader(fragShader);
            
            // 获取 uniform 位置
            projMatLocation = GL46C.glGetUniformLocation(program, "ProjMat");
            modelViewMatLocation = GL46C.glGetUniformLocation(program, "ModelViewMat");
            sampler0Location = GL46C.glGetUniformLocation(program, "Sampler0");
            lightIntensityLocation = GL46C.glGetUniformLocation(program, "LightIntensity");
            morphCountLocation = GL46C.glGetUniformLocation(program, "MorphCount");
            vertexCountLocation = GL46C.glGetUniformLocation(program, "VertexCount");
            
            // 获取 attribute 位置
            positionLocation = GL46C.glGetAttribLocation(program, "Position");
            normalLocation = GL46C.glGetAttribLocation(program, "Normal");
            uv0Location = GL46C.glGetAttribLocation(program, "UV0");
            // colorLocation 已移除，光照通过 uniform 传递
            boneIndicesLocation = GL46C.glGetAttribLocation(program, "BoneIndices");
            boneWeightsLocation = GL46C.glGetAttribLocation(program, "BoneWeights");
            
            // 创建骨骼矩阵 SSBO
            boneMatrixSSBO = GL46C.glGenBuffers();
            GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, boneMatrixSSBO);
            GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, MAX_BONES * 64, GL46C.GL_DYNAMIC_DRAW);
            GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
            GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 0, boneMatrixSSBO);
            
            // 创建 Morph SSBO（初始大小为 0，会在 uploadMorphData 时重新分配）
            morphOffsetsSSBO = GL46C.glGenBuffers();
            morphWeightsSSBO = GL46C.glGenBuffers();
            
            initialized = true;
            logger.info("GPU蒙皮着色器初始化成功");
            return true;
            
        } catch (Exception e) {
            logger.error("GPU蒙皮着色器初始化异常", e);
            return false;
        }
    }
    
    /**
     * 上传骨骼矩阵到 GPU
     * @param matrices 骨骼矩阵数据（每个矩阵 16 个 float）
     * @param boneCount 骨骼数量
     */
    public void uploadBoneMatrices(FloatBuffer matrices, int boneCount) {
        if (!initialized || boneMatrixSSBO == 0) return;
        
        // 每次渲染时重新绑定 SSBO（Minecraft 可能会改变绑定状态）
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 0, boneMatrixSSBO);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, boneMatrixSSBO);
        
        // 只上传实际使用的骨骼数量
        int actualBones = Math.min(boneCount, MAX_BONES);
        matrices.limit(actualBones * 16);
        matrices.position(0);
        GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, matrices);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    public void use() {
        if (program > 0) {
            GL46C.glUseProgram(program);
        }
    }
    
    public void setProjectionMatrix(FloatBuffer matrix) {
        if (projMatLocation >= 0) {
            matrix.position(0);
            GL46C.glUniformMatrix4fv(projMatLocation, false, matrix);
        }
    }
    
    public void setModelViewMatrix(FloatBuffer matrix) {
        if (modelViewMatLocation >= 0) {
            matrix.position(0);
            GL46C.glUniformMatrix4fv(modelViewMatLocation, false, matrix);
        }
    }
    
    public void setSampler0(int textureUnit) {
        if (sampler0Location >= 0) {
            GL46C.glUniform1i(sampler0Location, textureUnit);
        }
    }
    
    public void setLightIntensity(float intensity) {
        if (lightIntensityLocation >= 0) {
            GL46C.glUniform1f(lightIntensityLocation, intensity);
        }
    }
    
    public int getProgram() { return program; }
    public int getPositionLocation() { return positionLocation; }
    public int getNormalLocation() { return normalLocation; }
    public int getUv0Location() { return uv0Location; }
    public int getBoneIndicesLocation() { return boneIndicesLocation; }
    public int getBoneWeightsLocation() { return boneWeightsLocation; }
    
    public boolean isInitialized() { return initialized; }
    
    /**
     * 上传 Morph 偏移数据（静态，只需上传一次）
     */
    public void uploadMorphOffsets(java.nio.ByteBuffer data, int morphCount, int vertexCount) {
        if (!initialized || morphOffsetsSSBO == 0) return;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphOffsetsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, data, GL46C.GL_STATIC_DRAW);
        
        // 预分配权重缓冲区
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphWeightsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, morphCount * 4L, GL46C.GL_DYNAMIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        
    }
    
    /**
     * 更新 Morph 权重（每帧调用）
     */
    public void updateMorphWeights(java.nio.FloatBuffer weights) {
        if (!initialized || morphWeightsSSBO == 0) return;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphWeightsSSBO);
        weights.position(0);
        GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, weights);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    /**
     * 绑定 Morph SSBO
     */
    public void bindMorphSSBOs() {
        if (morphOffsetsSSBO > 0) {
            GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 1, morphOffsetsSSBO);
        }
        if (morphWeightsSSBO > 0) {
            GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 2, morphWeightsSSBO);
        }
    }
    
    /**
     * 设置 Morph 参数
     */
    public void setMorphParams(int morphCount, int vertexCount) {
        if (morphCountLocation >= 0) {
            GL46C.glUniform1i(morphCountLocation, morphCount);
        }
        if (vertexCountLocation >= 0) {
            GL46C.glUniform1i(vertexCountLocation, vertexCount);
        }
    }
    
    public void cleanup() {
        if (program > 0) {
            GL46C.glDeleteProgram(program);
            program = 0;
        }
        if (boneMatrixSSBO > 0) {
            GL46C.glDeleteBuffers(boneMatrixSSBO);
            boneMatrixSSBO = 0;
        }
        if (morphOffsetsSSBO > 0) {
            GL46C.glDeleteBuffers(morphOffsetsSSBO);
            morphOffsetsSSBO = 0;
        }
        if (morphWeightsSSBO > 0) {
            GL46C.glDeleteBuffers(morphWeightsSSBO);
            morphWeightsSSBO = 0;
        }
        initialized = false;
    }
}
