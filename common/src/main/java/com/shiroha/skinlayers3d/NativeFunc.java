package com.shiroha.skinlayers3d;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NativeFunc {
    public static final Logger logger = LogManager.getLogger();
    private static final String RuntimePath = new File(System.getProperty("java.home")).getParent();
    private static final String gameDirectory = Minecraft.getInstance().gameDirectory.getAbsolutePath();
    private static final boolean isAndroid = new File("/system/build.prop").exists();
    private static final boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
    private static final HashMap<runtimeUrlRes, String> urlMap = new HashMap<runtimeUrlRes, String>() {
        {
            put(runtimeUrlRes.windows, "https://github.com/Gengorou-C/KAIMyEntitySaba/releases/download/Rust-20260125/mmd_engine.dll");
            put(runtimeUrlRes.android_arch64, "https://github.com.cnpmjs.org/asuka-mio/KAIMyEntitySaba/releases/download/crossplatform/KAIMyEntitySaba.so");
            put(runtimeUrlRes.android_arch64_libc, "https://github.com.cnpmjs.org/asuka-mio/KAIMyEntitySaba/releases/download/crossplatform/libc++_shared.so");
        }
    };
    private static volatile NativeFunc inst;
    private static final Object lock = new Object();
    static final String libraryVersion = "Rust-20260125";

    public static NativeFunc GetInst() {
        if (inst == null) {
            synchronized (lock) {
                if (inst == null) {
                    NativeFunc newInst = new NativeFunc();
                    newInst.Init();
                    if(!newInst.GetVersion().equals(libraryVersion)){
                        logger.warn("Incompatible Version dll. / loaded ver -> " + newInst.GetVersion() + " / required ver -> "+ libraryVersion);
                        logger.warn("Please restart or download dll.");
                        try{
                            Files.move(Paths.get(gameDirectory, "mmd_engine.dll"),Paths.get(gameDirectory, "mmd_engine.dll.old"));
                        }catch(Exception e){
                            logger.info(e);
                        }
                    }
                    inst = newInst;
                }
            }
        }
        return inst;
    }

    private void DownloadSingleFile(URL url, File file) throws IOException {
        if (file.exists()) {
            try {
                System.load(file.getAbsolutePath());
                return; //File exist and loadable
            } catch (Error e) {
                logger.info("\"" + file.getAbsolutePath() + "\" broken! Trying recover it!");
            }
        }
        try {
            file.delete();
            file.createNewFile();
            FileUtils.copyURLToFile(url, file, 30000, 30000);
            System.load(file.getAbsolutePath());
        } catch (IOException e) {
            file.delete();
            logger.info("Download \"" + url.getPath() + "\" failed!");
            logger.info("Cannot download runtime!");
            logger.info("Check you internet connection and restart game!");
            e.printStackTrace();
            throw e;
        }
    }

    private void DownloadRuntime() throws Exception {
        if (isWindows) {
            DownloadSingleFile(new URL(urlMap.get(runtimeUrlRes.windows)), new File(gameDirectory, "mmd_engine.dll"));
        }
        if (isLinux && !isAndroid) {
            logger.info("Not support!");
            throw new Error();
        }
        if (isLinux && isAndroid) {
            DownloadSingleFile(new URL(urlMap.get(runtimeUrlRes.android_arch64_libc)), new File(RuntimePath, "libc++_shared.so"));
            DownloadSingleFile(new URL(urlMap.get(runtimeUrlRes.android_arch64)), new File(RuntimePath, "KAIMyEntitySaba.so"));
        }
    }

    private void LoadLibrary(File file) {
        try {
            System.load(file.getAbsolutePath());
        } catch (Error e) {
            logger.info("Runtime \"" + file.getAbsolutePath() + "\" not found, try download from github!");
            throw e;
        }
    }

    private void Init() {
        try {
            if (isWindows) {
                logger.info("Win32 Env Detected!");
                LoadLibrary(new File(gameDirectory, "mmd_engine.dll"));//WIN32
            }
            if (isLinux && !isAndroid) {
                logger.info("Linux Env Detected!");
                LoadLibrary(new File(gameDirectory, "KAIMyEntitySaba.so"));//Linux
            }
            if (isLinux && isAndroid) {
                logger.info("Android Env Detected!");
                LoadLibrary(new File(RuntimePath, "libc++_shared.so"));
                LoadLibrary(new File(RuntimePath, "KAIMyEntitySaba.so"));//Android
            }
        } catch (Error e) {
            try {
                DownloadRuntime();
            } catch (Exception ex) {
                throw e;
            }
        }
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

    public native void ResetModelPhysics(long model);

    public native long CreateMat();

    public native void DeleteMat(long mat);

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

    enum runtimeUrlRes {
        windows,android_arch64, android_arch64_libc
    }
}
