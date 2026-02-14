package com.shiroha.mmdskin;

import java.nio.ByteBuffer;

/**
 * Rust JNI 桥接（SRP：仅负责 native 方法声明和单例管理）
 *
 * 原生库加载逻辑已拆分到 {@link NativeLibraryLoader}。
 */
public class NativeFunc {
    private static volatile NativeFunc inst;
    private static final Object lock = new Object();

    /** 供其他模块查询是否运行在 Android 环境 */
    public static boolean isAndroid() { return NativeLibraryLoader.isAndroid(); }

    public static NativeFunc GetInst() {
        if (inst == null) {
            synchronized (lock) {
                if (inst == null) {
                    NativeFunc newInst = new NativeFunc();
                    NativeLibraryLoader.loadAndVerify(newInst);
                    inst = newInst;
                }
            }
        }
        return inst;
    }

    public native String GetVersion();

    public native byte ReadByte(long data, long pos);

    public native void CopyDataToByteBuffer(ByteBuffer buffer, long data, long pos);

    public native long LoadModelPMX(String filename, String dir, long layerCount);

    public native long LoadModelPMD(String filename, String dir, long layerCount);

    public native void DeleteModel(long model);

    public native void UpdateModel(long model, float deltaTime);

    public native long GetVertexCount(long model);

    public native long GetPoss(long model);

    public native long GetNormals(long model);

    public native long GetUVs(long model);

    public native long GetIndexElementSize(long model);

    public native long GetIndexCount(long model);

    public native long GetIndices(long model);

    public native long GetMaterialCount(long model);

    public native String GetMaterialTex(long model, long pos);

    public native String GetMaterialSpTex(long model, long pos);

    public native String GetMaterialToonTex(long model, long pos);

    public native long GetMaterialAmbient(long model, long pos);

    public native long GetMaterialDiffuse(long model, long pos);

    public native long GetMaterialSpecular(long model, long pos);

    public native float GetMaterialSpecularPower(long model, long pos);

    public native float GetMaterialAlpha(long model, long pos);

    public native long GetMaterialTextureMulFactor(long model, long pos);

    public native long GetMaterialTextureAddFactor(long model, long pos);

    public native int GetMaterialSpTextureMode(long model, long pos);

    public native long GetMaterialSpTextureMulFactor(long model, long pos);

    public native long GetMaterialSpTextureAddFactor(long model, long pos);

    public native long GetMaterialToonTextureMulFactor(long model, long pos);

    public native long GetMaterialToonTextureAddFactor(long model, long pos);

    public native boolean GetMaterialBothFace(long model, long pos);

    public native long GetSubMeshCount(long model);

    public native int GetSubMeshMaterialID(long model, long pos);

    public native int GetSubMeshBeginIndex(long model, long pos);

    public native int GetSubMeshVertexCount(long model, long pos);

    public native void ChangeModelAnim(long model, long anim, long layer);
    
    /**
     * 带过渡地切换动画（矩阵插值过渡）
     * 从当前骨骼姿态平滑过渡到新动画，避免动作切换时的突兀感
     * @param model 模型句柄
     * @param layer 动画层ID（0-3）
     * @param anim 动画句柄（0表示清除动画）
     * @param transitionTime 过渡时间（秒），推荐 0.2 ~ 0.5 秒
     */
    public native void TransitionLayerTo(long model, long layer, long anim, float transitionTime);

    /**
     * 设置动画层是否循环播放
     * @param model 模型句柄
     * @param layer 动画层ID（0-3）
     * @param loop true=循环播放，false=播放到尾帧后停留
     */
    public native void SetLayerLoop(long model, long layer, boolean loop);

    public native void ResetModelPhysics(long model);

    public native long CreateMat();

    public native void DeleteMat(long mat);

    public native boolean CopyMatToBuffer(long mat, java.nio.ByteBuffer buffer);

    public native void GetRightHandMat(long model, long mat);

    public native void GetLeftHandMat(long model, long mat);

    public native long LoadTexture(String filename);

    public native void DeleteTexture(long tex);

    public native int GetTextureX(long tex);

    public native int GetTextureY(long tex);

    public native long GetTextureData(long tex);

    public native boolean TextureHasAlpha(long tex);

    public native long LoadAnimation(long model, String filename);

    public native void DeleteAnimation(long anim);
    
    /**
     * 查询动画是否包含相机数据
     * @param anim 动画句柄
     * @return 是否包含相机数据
     */
    public native boolean HasCameraData(long anim);
    
    /**
     * 获取动画最大帧数（包含相机轨道）
     * @param anim 动画句柄
     * @return 最大帧数
     */
    public native float GetAnimMaxFrame(long anim);
    
    /**
     * 获取相机变换数据，写入 ByteBuffer (32 字节)
     * 布局: pos_x, pos_y, pos_z (3×f32) + rot_x, rot_y, rot_z (3×f32) + fov (f32) + is_perspective (i32)
     * @param anim 动画句柄
     * @param frame 浮点帧数
     * @param buffer 目标 DirectByteBuffer (至少 32 字节)
     */
    public native void GetCameraTransform(long anim, float frame, ByteBuffer buffer);
    
    /**
     * 查询动画是否包含骨骼关键帧
     * @param anim 动画句柄
     * @return 是否包含骨骼数据
     */
    public native boolean HasBoneData(long anim);
    
    /**
     * 查询动画是否包含表情关键帧
     * @param anim 动画句柄
     * @return 是否包含表情数据
     */
    public native boolean HasMorphData(long anim);
    
    /**
     * 将 source 动画的骨骼和 Morph 数据合并到 target 动画中
     * @param target 目标动画句柄（将被修改）
     * @param source 源动画句柄（只读）
     */
    public native void MergeAnimation(long target, long source);

    public native void SetHeadAngle(long model, float x, float y, float z, boolean flag);
    
    /**
     * 设置模型全局变换（用于人物移动时传递位置给物理系统）
     * @param model 模型句柄
     * @param m00-m33 4x4变换矩阵的16个元素（列主序）
     */
    public native void SetModelTransform(long model,
        float m00, float m01, float m02, float m03,
        float m10, float m11, float m12, float m13,
        float m20, float m21, float m22, float m23,
        float m30, float m31, float m32, float m33);
    
    /**
     * 设置模型位置和朝向（简化版，用于惯性计算）
     * @param model 模型句柄
     * @param posX 位置X（已缩放）
     * @param posY 位置Y（已缩放）
     * @param posZ 位置Z（已缩放）
     * @param yaw 人物朝向（弧度）
     */
    public native void SetModelPositionAndYaw(long model, float posX, float posY, float posZ, float yaw);

    // ========== 眼球追踪相关 ==========
    
    /**
     * 设置眼球追踪角度（眼睛看向摄像头）
     * @param model 模型句柄
     * @param eyeX 上下看的角度（弧度，正值向上）
     * @param eyeY 左右看的角度（弧度，正值向左）
     */
    public native void SetEyeAngle(long model, float eyeX, float eyeY);
    
    /**
     * 设置眼球最大转动角度
     * @param model 模型句柄
     * @param maxAngle 最大角度（弧度），默认 0.35（约 20 度）
     */
    public native void SetEyeMaxAngle(long model, float maxAngle);
    
    /**
     * 启用/禁用眼球追踪
     * @param model 模型句柄
     * @param enabled 是否启用
     */
    public native void SetEyeTrackingEnabled(long model, boolean enabled);
    
    /**
     * 获取眼球追踪是否启用
     * @param model 模型句柄
     * @return 是否启用
     */
    public native boolean IsEyeTrackingEnabled(long model);

    // ========== 自动眨眼相关 ==========
    
    /**
     * 启用/禁用自动眨眼
     * @param model 模型句柄
     * @param enabled 是否启用
     */
    public native void SetAutoBlinkEnabled(long model, boolean enabled);
    
    /**
     * 获取自动眨眼是否启用
     * @param model 模型句柄
     * @return 是否启用
     */
    public native boolean IsAutoBlinkEnabled(long model);
    
    /**
     * 设置眨眼参数
     * @param model 模型句柄
     * @param interval 眨眼间隔（秒），默认 4.0
     * @param duration 眨眼持续时间（秒），默认 0.15
     */
    public native void SetBlinkParams(long model, float interval, float duration);

    // ========== 物理系统相关 ==========
    
    /**
     * 初始化物理系统（模型加载时自动调用，通常不需要手动调用）
     * @param model 模型句柄
     * @return 是否成功
     */
    public native boolean InitPhysics(long model);
    
    /**
     * 重置物理系统（将所有刚体重置到初始位置）
     * @param model 模型句柄
     */
    public native void ResetPhysics(long model);
    
    /**
     * 启用/禁用物理
     * @param model 模型句柄
     * @param enabled 是否启用
     */
    public native void SetPhysicsEnabled(long model, boolean enabled);
    
    /**
     * 获取物理是否启用
     * @param model 模型句柄
     * @return 是否启用
     */
    public native boolean IsPhysicsEnabled(long model);
    
    /**
     * 获取物理系统是否已初始化
     * @param model 模型句柄
     * @return 是否已初始化
     */
    public native boolean HasPhysics(long model);
    
    /**
     * 获取物理调试信息（JSON格式）
     * @param model 模型句柄
     * @return JSON字符串，包含刚体和关节信息
     */
    public native String GetPhysicsDebugInfo(long model);
    
    // ========== 材质可见性控制（脱外套等功能） ==========
    
    /**
     * 获取材质是否可见
     * @param model 模型句柄
     * @param index 材质索引
     * @return 是否可见
     */
    public native boolean IsMaterialVisible(long model, int index);
    
    /**
     * 设置材质可见性
     * @param model 模型句柄
     * @param index 材质索引
     * @param visible 是否可见
     */
    public native void SetMaterialVisible(long model, int index, boolean visible);
    
    /**
     * 根据材质名称设置可见性（支持部分匹配）
     * @param model 模型句柄
     * @param name 材质名称（部分匹配）
     * @param visible 是否可见
     * @return 匹配的材质数量
     */
    public native int SetMaterialVisibleByName(long model, String name, boolean visible);
    
    /**
     * 设置所有材质可见性
     * @param model 模型句柄
     * @param visible 是否可见
     */
    public native void SetAllMaterialsVisible(long model, boolean visible);
    
    /**
     * 获取材质名称
     * @param model 模型句柄
     * @param index 材质索引
     * @return 材质名称
     */
    public native String GetMaterialName(long model, int index);
    
    /**
     * 获取所有材质名称（JSON数组格式）
     * @param model 模型句柄
     * @return JSON数组字符串
     */
    public native String GetMaterialNames(long model);
    
    // ========== GPU 蒙皮相关 ==========
    
    /**
     * 获取骨骼数量
     * @param model 模型句柄
     * @return 骨骼数量
     */
    public native int GetBoneCount(long model);
    
    /**
     * 获取蒙皮矩阵数据指针（用于 GPU 蒙皮）
     * @param model 模型句柄
     * @return 蒙皮矩阵数组指针（每个矩阵 16 个 float）
     */
    public native long GetSkinningMatrices(long model);
    
    /**
     * 复制蒙皮矩阵到 ByteBuffer（线程安全）
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要足够大小：骨骼数 * 64 字节）
     * @return 复制的骨骼数量
     */
    public native int CopySkinningMatricesToBuffer(long model, java.nio.ByteBuffer buffer);
    
    /**
     * 获取顶点骨骼索引数据指针（ivec4 格式）
     * @param model 模型句柄
     * @return 骨骼索引数组指针（每顶点 4 个 int）
     */
    public native long GetBoneIndices(long model);
    
    /**
     * 复制骨骼索引到 ByteBuffer（线程安全）
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要 vertexCount * 16 字节）
     * @param vertexCount 顶点数量
     * @return 复制的顶点数量
     */
    public native int CopyBoneIndicesToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);
    
    /**
     * 获取顶点骨骼权重数据指针（vec4 格式）
     * @param model 模型句柄
     * @return 骨骼权重数组指针（每顶点 4 个 float）
     */
    public native long GetBoneWeights(long model);
    
    /**
     * 复制骨骼权重到 ByteBuffer（线程安全）
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要 vertexCount * 16 字节）
     * @param vertexCount 顶点数量
     * @return 复制的顶点数量
     */
    public native int CopyBoneWeightsToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);
    
    /**
     * 获取原始顶点位置数据指针（未蒙皮）
     * @param model 模型句柄
     * @return 原始位置数组指针
     */
    public native long GetOriginalPositions(long model);
    
    /**
     * 复制原始顶点位置到 ByteBuffer（线程安全）
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要 vertexCount * 12 字节）
     * @param vertexCount 顶点数量
     * @return 复制的顶点数量
     */
    public native int CopyOriginalPositionsToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);
    
    /**
     * 获取原始法线数据指针（未蒙皮）
     * @param model 模型句柄
     * @return 原始法线数组指针
     */
    public native long GetOriginalNormals(long model);
    
    /**
     * 复制原始法线到 ByteBuffer（线程安全）
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要 vertexCount * 12 字节）
     * @param vertexCount 顶点数量
     * @return 复制的顶点数量
     */
    public native int CopyOriginalNormalsToBuffer(long model, java.nio.ByteBuffer buffer, int vertexCount);
    
    /**
     * 获取 GPU 蒙皮调试信息
     * @param model 模型句柄
     * @return 调试信息字符串
     */
    public native String GetGpuSkinningDebugInfo(long model);
    
    /**
     * 仅更新动画（不执行 CPU 蒙皮，用于 GPU 蒙皮模式）
     * @param model 模型句柄
     * @param deltaTime 时间增量（秒）
     */
    public native void UpdateAnimationOnly(long model, float deltaTime);
    
    /**
     * 初始化 GPU 蒙皮数据（模型加载后调用一次）
     * @param model 模型句柄
     */
    public native void InitGpuSkinningData(long model);
    
    // ========== GPU Morph 相关 ==========
    
    /**
     * 初始化 GPU Morph 数据
     * 将稀疏的顶点 Morph 偏移转换为密集格式，供 GPU Compute Shader 使用
     * @param model 模型句柄
     */
    public native void InitGpuMorphData(long model);
    
    /**
     * 获取顶点 Morph 数量
     * @param model 模型句柄
     * @return 顶点 Morph 数量
     */
    public native int GetVertexMorphCount(long model);
    
    /**
     * 获取 GPU Morph 偏移数据指针（密集格式：morph_count * vertex_count * 3）
     * @param model 模型句柄
     * @return 数据指针
     */
    public native long GetGpuMorphOffsets(long model);
    
    /**
     * 获取 GPU Morph 偏移数据大小（字节）
     * @param model 模型句柄
     * @return 数据大小
     */
    public native long GetGpuMorphOffsetsSize(long model);
    
    /**
     * 获取 GPU Morph 权重数据指针
     * @param model 模型句柄
     * @return 数据指针
     */
    public native long GetGpuMorphWeights(long model);
    
    /**
     * 同步 GPU Morph 权重（从动画系统更新到 GPU 缓冲区）
     * @param model 模型句柄
     */
    public native void SyncGpuMorphWeights(long model);
    
    /**
     * 复制 GPU Morph 偏移数据到 ByteBuffer
     * @param model 模型句柄
     * @param buffer 目标缓冲区
     * @return 复制的字节数
     */
    public native long CopyGpuMorphOffsetsToBuffer(long model, java.nio.ByteBuffer buffer);
    
    /**
     * 复制 GPU Morph 权重数据到 ByteBuffer
     * @param model 模型句柄
     * @param buffer 目标缓冲区
     * @return 复制的 Morph 数量
     */
    public native int CopyGpuMorphWeightsToBuffer(long model, java.nio.ByteBuffer buffer);
    
    /**
     * 获取 GPU Morph 是否已初始化
     * @param model 模型句柄
     * @return 是否已初始化
     */
    public native boolean IsGpuMorphInitialized(long model);
    
    // ========== VPD 表情预设相关 ==========
    
    /**
     * 应用 VPD 表情/姿势预设到模型
     * 
     * VPD 文件可以同时包含骨骼姿势（Bone）和表情权重（Morph）数据，此函数会同时应用两者。
     * 
     * @param model 模型句柄
     * @param filename VPD 文件路径
     * @return 编码值: 高16位为骨骼匹配数，低16位为 Morph 匹配数
     *         解码方式: boneCount = (result >> 16) & 0xFFFF; morphCount = result & 0xFFFF;
     *         -1 表示加载失败，-2 表示模型不存在
     */
    public native int ApplyVpdMorph(long model, String filename);
    
    /**
     * 重置所有 Morph 权重为 0
     * @param model 模型句柄
     */
    public native void ResetAllMorphs(long model);
    
    /**
     * 通过名称设置单个 Morph 权重
     * @param model 模型句柄
     * @param morphName Morph 名称
     * @param weight 权重值 (0.0-1.0)
     * @return 是否成功
     */
    public native boolean SetMorphWeightByName(long model, String morphName, float weight);
    
    /**
     * 获取 Morph 数量
     * @param model 模型句柄
     * @return Morph 数量
     */
    public native long GetMorphCount(long model);
    
    /**
     * 获取 Morph 名称（通过索引）
     * @param model 模型句柄
     * @param index Morph 索引
     * @return Morph 名称
     */
    public native String GetMorphName(long model, int index);
    
    /**
     * 获取 Morph 权重（通过索引）
     * @param model 模型句柄
     * @param index Morph 索引
     * @return 权重值
     */
    public native float GetMorphWeight(long model, int index);
    
    /**
     * 设置 Morph 权重（通过索引）
     * @param model 模型句柄
     * @param index Morph 索引
     * @param weight 权重值 (0.0-1.0)
     */
    public native void SetMorphWeight(long model, int index, float weight);
    
    // ========== GPU UV Morph 相关 ==========
    
    /**
     * 初始化 GPU UV Morph 数据
     * 将稀疏的 UV Morph 偏移转换为密集格式，供 GPU Compute Shader 使用
     * @param model 模型句柄
     */
    public native void InitGpuUvMorphData(long model);
    
    /**
     * 获取 UV Morph 数量
     * @param model 模型句柄
     * @return UV Morph 数量
     */
    public native int GetUvMorphCount(long model);
    
    /**
     * 获取 GPU UV Morph 偏移数据大小（字节）
     * @param model 模型句柄
     * @return 数据大小
     */
    public native long GetGpuUvMorphOffsetsSize(long model);
    
    /**
     * 复制 GPU UV Morph 偏移数据到 ByteBuffer
     * @param model 模型句柄
     * @param buffer 目标缓冲区
     * @return 复制的字节数
     */
    public native long CopyGpuUvMorphOffsetsToBuffer(long model, java.nio.ByteBuffer buffer);
    
    /**
     * 复制 GPU UV Morph 权重数据到 ByteBuffer
     * @param model 模型句柄
     * @param buffer 目标缓冲区
     * @return 复制的 Morph 数量
     */
    public native int CopyGpuUvMorphWeightsToBuffer(long model, java.nio.ByteBuffer buffer);
    
    // ========== 材质 Morph 结果相关 ==========
    
    /**
     * 获取材质 Morph 结果数量（等于材质数量）
     * @param model 模型句柄
     * @return 材质数量
     */
    public native int GetMaterialMorphResultCount(long model);
    
    /**
     * 复制材质 Morph 结果到 ByteBuffer
     * 每个材质 28 个 float: diffuse(4) + specular(3) + specular_strength(1) +
     * ambient(3) + edge_color(4) + edge_size(1) + texture_tint(4) +
     * environment_tint(4) + toon_tint(4)
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要 materialCount * 28 * 4 字节）
     * @return 材质数量
     */
    public native int CopyMaterialMorphResultsToBuffer(long model, java.nio.ByteBuffer buffer);
    
    // ========== 批量子网格元数据（G3 优化）==========
    
    /**
     * 批量获取所有子网格的渲染元数据，消除逐子网格 JNI 调用
     * 
     * 每子网格 20 字节：
     * - offset  0: int materialID
     * - offset  4: int beginIndex
     * - offset  8: int vertexCount
     * - offset 12: float alpha（基础材质 alpha）
     * - offset 16: byte isVisible (0/1)
     * - offset 17: byte bothFace  (0/1)
     * - offset 18-19: padding
     * 
     * @param model  模型句柄
     * @param buffer 输出缓冲区（DirectByteBuffer，需预分配 subMeshCount * 20 字节）
     * @return 写入的子网格数量
     */
    public native int BatchGetSubMeshData(long model, java.nio.ByteBuffer buffer);
    
    // ========== 物理配置相关 ==========
    
    /**
     * 设置全局物理配置（Bullet3，实时调整）
     *
     * @param enabled 是否启用物理模拟
     * @param gravityY 重力 Y 分量（负数向下，MMD 标准 -98.0）
     * @param physicsFps 物理 FPS（固定时间步）
     * @param maxSubstepCount 每帧最大子步数
     * @param inertiaStrength 惯性效果强度（0.0=无, 1.0=正常）
     * @param maxLinearVelocity 最大线速度（防止物理爆炸）
     * @param maxAngularVelocity 最大角速度（防止物理爆炸）
     * @param jointsEnabled 是否启用关节
     * @param debugLog 是否输出调试日志
     */
    public native void SetPhysicsConfig(
        boolean enabled,
        float gravityY,
        float physicsFps,
        int maxSubstepCount,
        float inertiaStrength,
        float maxLinearVelocity,
        float maxAngularVelocity,
        boolean jointsEnabled,
        boolean debugLog
    );
    
    // ========== 第一人称模式相关 ==========
    
    /**
     * 设置第一人称模式
     * 启用时自动隐藏头部相关子网格（基于骨骼权重检测），禁用时恢复
     * @param model 模型句柄
     * @param enabled 是否启用
     */
    public native void SetFirstPersonMode(long model, boolean enabled);
    
    /**
     * 获取第一人称模式是否启用
     * @param model 模型句柄
     * @return 是否启用
     */
    public native boolean IsFirstPersonMode(long model);
    
    /**
     * 获取头部骨骼的静态 Y 坐标（模型局部空间）
     * 用于第一人称模式下的相机高度计算
     * @param model 模型句柄
     * @return 头部骨骼 Y 坐标（模型局部空间），未找到头部骨骼时返回 0
     */
    public native float GetHeadBonePositionY(long model);
    
    /**
     * 获取眼睛骨骼的当前动画位置（模型局部空间）
     * 每帧调用，返回经过动画/物理更新后的实时位置
     * @param model 模型句柄
     * @param out 输出数组 [x, y, z]，长度至少为 3
     */
    public native void GetEyeBonePosition(long model, float[] out);
    
    // ==================== 公共 API 相关 ====================
    
    /**
     * 获取所有骨骼名称（JSON 数组格式）
     * @param model 模型句柄
     * @return JSON 数组字符串，如 ["センター","上半身",...]
     */
    public native String GetBoneNames(long model);
    
    /**
     * 复制所有骨骼的实时世界位置到 ByteBuffer
     * 每个骨骼 3 个 float (x, y, z)，共 boneCount * 12 字节
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要 boneCount * 12 字节）
     * @return 复制的骨骼数量
     */
    public native int CopyBonePositionsToBuffer(long model, java.nio.ByteBuffer buffer);
    
    /**
     * 复制实时 UV 数据到 ByteBuffer（经过 UV Morph 变形后的坐标）
     * 每个顶点 2 个 float (u, v)，共 vertexCount * 8 字节
     * @param model 模型句柄
     * @param buffer 目标缓冲区（需要 vertexCount * 8 字节）
     * @return 复制的顶点数量
     */
    public native int CopyRealtimeUVsToBuffer(long model, java.nio.ByteBuffer buffer);
    
    // ==================== 内存统计 ====================
    
    /**
     * 获取模型在 Rust 堆上的内存占用（字节）
     * @param model 模型句柄
     * @return 内存占用字节数
     */
    public native long GetModelMemoryUsage(long model);
}
