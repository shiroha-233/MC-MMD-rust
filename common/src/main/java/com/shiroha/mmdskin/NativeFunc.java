package com.shiroha.mmdskin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NativeFunc {
    public static final Logger logger = LogManager.getLogger();
    private static volatile String gameDirectory;

    private static String getGameDirectory() {
        if (gameDirectory == null) {
            synchronized (lock) {
                if (gameDirectory == null) {
                    gameDirectory = Minecraft.getInstance().gameDirectory.getAbsolutePath();
                }
            }
        }
        return gameDirectory;
    }
    private static final boolean isAndroid;
    private static final boolean isLinux;
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
    private static final boolean isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final boolean isArm64;
    private static final boolean isLoongArch64;
    private static final boolean isRiscv64;
    static {
        String arch = System.getProperty("os.arch").toLowerCase();
        isArm64 = arch.contains("aarch64") || arch.contains("arm64");
        isLoongArch64 = arch.contains("loongarch64") || arch.contains("loong64");
        isRiscv64 = arch.contains("riscv64");
        // Android 检测（FCL/PojavLauncher 等启动器使用标准 JVM）
        boolean androidDetected = false;
        String[] launcherEnvKeys = { "FCL_NATIVEDIR", "POJAV_NATIVEDIR", "MOD_ANDROID_RUNTIME", "FCL_VERSION_CODE" };
        for (String key : launcherEnvKeys) {
            String val = System.getenv(key);
            if (val != null && !val.isEmpty()) {
                androidDetected = true;
                break;
            }
        }
        if (!androidDetected) {
            String androidRoot = System.getenv("ANDROID_ROOT");
            String androidData = System.getenv("ANDROID_DATA");
            androidDetected = (androidRoot != null && !androidRoot.isEmpty())
                           || (androidData != null && !androidData.isEmpty());
        }
        if (!androidDetected) {
            try {
                androidDetected = new java.io.File("/system/build.prop").exists();
            } catch (Exception ignored) {}
        }
        if (!androidDetected) {
            String vendor = System.getProperty("java.vendor", "").toLowerCase();
            String vmName = System.getProperty("java.vm.name", "").toLowerCase();
            androidDetected = vendor.contains("android") || vmName.contains("dalvik") || vmName.contains("art");
        }
        isAndroid = androidDetected;
        // isLinux 排除 Android，避免误判
        isLinux = System.getProperty("os.name").toLowerCase().contains("linux") && !isAndroid;
    }
    static final String libraryVersion = "v1.0.1";
    private static final String RELEASE_BASE_URL = "https://github.com/shiroha-23/MC-MMD-rust/releases/download/" + libraryVersion + "/";
    private static volatile NativeFunc inst;
    private static final Object lock = new Object();

    public static NativeFunc GetInst() {
        if (inst == null) {
            synchronized (lock) {
                if (inst == null) {
                    NativeFunc newInst = new NativeFunc();
                    newInst.Init();
                    inst = newInst;
                }
            }
        }
        return inst;
    }

    /**
     * 获取已释放库的版本（通过版本文件）
     */
    private String getInstalledVersion(String fileName) {
        try {
            Path versionPath = Paths.get(getGameDirectory(), fileName + ".version");
            if (Files.exists(versionPath)) {
                return Files.readString(versionPath).trim();
            }
        } catch (Exception e) {
            logger.debug("读取版本文件失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 保存版本文件
     */
    private void saveInstalledVersion(String fileName, String version) {
        try {
            Path versionPath = Paths.get(getGameDirectory(), fileName + ".version");
            Files.writeString(versionPath, version);
        } catch (Exception e) {
            logger.warn("保存版本文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 重命名旧库文件为 .old
     */
    private void renameOldLibrary(String fileName) {
        try {
            Path libPath = Paths.get(getGameDirectory(), fileName);
            Path oldPath = Paths.get(getGameDirectory(), fileName + ".old");
            if (Files.exists(libPath)) {
                // 删除可能存在的旧 .old 文件
                Files.deleteIfExists(oldPath);
                Files.move(libPath, oldPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("已将旧版本库重命名为: " + fileName + ".old");
            }
        } catch (Exception e) {
            logger.warn("重命名旧库文件失败: " + e.getMessage());
        }
    }

    private File extractNativeLibrary(String resourcePath, String fileName) {
        try {
            Path targetPath = Paths.get(getGameDirectory(), fileName);
            File targetFile = targetPath.toFile();
            
            // 检查已安装版本
            String installedVersion = getInstalledVersion(fileName);
            
            if (targetFile.exists() && libraryVersion.equals(installedVersion)) {
                // 版本匹配，直接使用现有文件
                logger.info("原生库版本匹配 (" + libraryVersion + ")，使用缓存: " + fileName);
                return targetFile;
            }
            
            // 版本不匹配或文件不存在，需要释放新版本
            try (InputStream is = NativeFunc.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.warn("内置原生库未找到: " + resourcePath);
                    if (targetFile.exists()) {
                        logger.warn("将回退使用旧版本库: " + fileName + " (版本: " + (installedVersion != null ? installedVersion : "未知") + ")");
                        return targetFile;
                    }
                    return null;
                }

                if (targetFile.exists()) {
                    // 版本不匹配，重命名旧文件
                    logger.info("检测到版本变更: " + (installedVersion != null ? installedVersion : "未知") + " -> " + libraryVersion);
                    renameOldLibrary(fileName);
                }

                // 释放新版本
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 保存版本文件
            saveInstalledVersion(fileName, libraryVersion);
            
            logger.info("已从模组内置资源释放原生库: " + fileName + " (版本: " + libraryVersion + ")");
            return targetFile;
        } catch (Exception e) {
            logger.error("提取原生库失败: " + resourcePath, e);
            return null;
        }
    }

    /**
     * 从 GitHub Release 下载原生库
     */
    private File downloadNativeLibrary(String downloadFileName, String localFileName) {
        try {
            Path targetPath = Paths.get(getGameDirectory(), localFileName);

            // 已有版本匹配的文件，无需下载
            String installedVersion = getInstalledVersion(localFileName);
            if (targetPath.toFile().exists() && libraryVersion.equals(installedVersion)) {
                logger.info("原生库版本匹配，使用已下载的缓存: " + localFileName);
                return targetPath.toFile();
            }

            String urlStr = RELEASE_BASE_URL + downloadFileName;
            logger.info("正在从 GitHub 下载原生库: " + urlStr);

            // GitHub Release 会 302 重定向到 CDN，手动跟随重定向
            HttpURLConnection conn = null;
            for (int i = 0; i < 5; i++) {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "MMDSkin-Mod/" + libraryVersion);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == 307 || code == 308) {
                    urlStr = conn.getHeaderField("Location");
                    conn.disconnect();
                    continue;
                }
                break;
            }

            if (conn == null || conn.getResponseCode() != 200) {
                logger.warn("下载失败，HTTP 状态码: " + (conn != null ? conn.getResponseCode() : "无连接"));
                if (conn != null) conn.disconnect();
                return null;
            }

            long contentLength = conn.getContentLengthLong();
            logger.info("开始下载，文件大小: " +
                    (contentLength > 0 ? (contentLength / 1024) + " KB" : "未知"));

            // 先下载到临时文件，完成后再移动，避免半成品文件
            Path tempPath = Paths.get(getGameDirectory(), localFileName + ".download");
            try (InputStream is = conn.getInputStream()) {
                Files.copy(is, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 旧文件存在时先重命名为 .old
            if (Files.exists(targetPath)) {
                renameOldLibrary(localFileName);
            }

            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            conn.disconnect();
            saveInstalledVersion(localFileName, libraryVersion);
            logger.info("原生库下载完成: " + localFileName);
            return targetPath.toFile();
        } catch (Exception e) {
            logger.error("下载原生库失败: " + downloadFileName, e);
            try {
                Files.deleteIfExists(Paths.get(getGameDirectory(), localFileName + ".download"));
            } catch (Exception ignored) {}
            return null;
        }
    }

    private void LoadLibrary(File file) {
        System.load(file.getAbsolutePath());
    }

    private void initAndroid() {
        logger.info("Android Env Detected! Arch: arm64");
        logger.info("  os.name=" + System.getProperty("os.name") + " os.arch=" + System.getProperty("os.arch"));
        logger.info("  FCL_NATIVEDIR=" + System.getenv("FCL_NATIVEDIR"));
        logger.info("  POJAV_NATIVEDIR=" + System.getenv("POJAV_NATIVEDIR"));
        logger.info("  MOD_ANDROID_RUNTIME=" + System.getenv("MOD_ANDROID_RUNTIME"));
        logger.info("  LD_LIBRARY_PATH=" + System.getenv("LD_LIBRARY_PATH"));
        logger.info("  gameDir=" + getGameDirectory());

        String resourcePath = "/natives/android-arm64/libmmd_engine.so";
        String soFileName = "libmmd_engine.so";

        //直接将 libmmd_engine.so 写入 $JAVA_HOME/lib
        var javaHome = System.getProperty("java.home");
        logger.info("  JAVA_HOME=" + javaHome);
        var javaLibDir = new File(javaHome, "lib");
        var javaLibSoFile = new File(javaLibDir, soFileName);
        if (javaLibDir.canWrite()) {
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                Files.copy(is, javaLibSoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("[Android] 已将 libmmd_engine.so 解压至 " + javaLibSoFile.getAbsolutePath());
                System.load(javaLibSoFile.getAbsolutePath());
                logger.info("[Android] " + javaLibSoFile.getAbsolutePath() + " 已加载");
                return;
            } catch (IOException | Error e) {
                logger.error("[Android] " + javaLibSoFile.getAbsolutePath() + "加载失败：" + e.getMessage());
            }
        } else
            logger.warn("[Android] JAVA_HOME无法写入，跳过。");

        try {
            logger.info("[Android] 策略1: System.loadLibrary(\"mmd_engine\")");
            System.loadLibrary("mmd_engine");
            logger.info("[Android] 策略1 成功！通过 LD_LIBRARY_PATH 加载");
            return;
        } catch (Error e) {
            logger.warn("[Android] 策略1 失败: " + e.getMessage());
        }

        String modRuntimeDir = System.getenv("MOD_ANDROID_RUNTIME");
        if (modRuntimeDir != null && !modRuntimeDir.isEmpty()) {
            try {
                File runtimeDir = new File(modRuntimeDir);
                if (!runtimeDir.exists()) runtimeDir.mkdirs();
                File targetFile = new File(runtimeDir, soFileName);

                try (InputStream is = NativeFunc.class.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logger.info("[Android] 策略2: 已提取到 " + targetFile.getAbsolutePath() + " (" + targetFile.length() + " bytes)");
                        System.load(targetFile.getAbsolutePath());
                        logger.info("[Android] 策略2 成功！从 MOD_ANDROID_RUNTIME 加载");
                        return;
                    }
                }
            } catch (Exception | Error e) {
                logger.warn("[Android] 策略2 失败 (MOD_ANDROID_RUNTIME): " + e.getMessage());
            }
        }

        String pojavNativeDir = System.getenv("POJAV_NATIVEDIR");
        if (pojavNativeDir != null && !pojavNativeDir.isEmpty()) {
            try {
                File nativeDir = new File(pojavNativeDir);
                File targetFile = new File(nativeDir, soFileName);

                try (InputStream is = NativeFunc.class.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logger.info("[Android] 策略3: 已提取到 " + targetFile.getAbsolutePath() + " (" + targetFile.length() + " bytes)");
                        System.load(targetFile.getAbsolutePath());
                        logger.info("[Android] 策略3 成功！从 POJAV_NATIVEDIR 加载");
                        return;
                    }
                }
            } catch (Exception | Error e) {
                logger.warn("[Android] 策略3 失败 (POJAV_NATIVEDIR): " + e.getMessage());
            }
        }

        File extracted = extractNativeLibrary(resourcePath, soFileName);
        if (extracted != null) {
            try {
                logger.info("[Android] 策略4: 尝试从游戏目录加载 " + extracted.getAbsolutePath() + " (" + extracted.length() + " bytes)");
                System.load(extracted.getAbsolutePath());
                logger.info("[Android] 策略4 成功！从游戏目录加载");
                return;
            } catch (Error e) {
                logger.error("[Android] 策略4 失败 (游戏目录): " + e.getClass().getName() + ": " + e.getMessage());
            }
        }

        File downloaded = downloadNativeLibrary("libmmd_engine-android-arm64.so", soFileName);
        if (downloaded != null) {
            try {
                logger.info("[Android] 策略5: 尝试加载下载的库 " + downloaded.getAbsolutePath());
                System.load(downloaded.getAbsolutePath());
                logger.info("[Android] 策略5 成功！从下载的文件加载");
                return;
            } catch (Error e) {
                logger.error("[Android] 策略5 失败 (下载): " + e.getClass().getName() + ": " + e.getMessage());
            }
        }

        throw new UnsatisfiedLinkError("[Android] 无法加载原生库 libmmd_engine.so，所有策略均失败。" +
            "请检查日志获取详细信息，或从 " + RELEASE_BASE_URL + " 手动下载 libmmd_engine-android-arm64.so");
    }

    private void Init() {
        // Android 走专用加载流程
        if (isAndroid) {
            initAndroid();
            return;
        }
        
        String resourcePath;
        String fileName;
        String downloadFileName;
        
        if (isWindows) {
            String archDir = isArm64 ? "windows-arm64" : "windows-x64";
            logger.info("Windows Env Detected! Arch: " + archDir);
            resourcePath = "/natives/" + archDir + "/mmd_engine.dll";
            fileName = "mmd_engine.dll";
            downloadFileName = "mmd_engine-" + archDir + ".dll";
        } else if (isMacOS) {
            String archDir = isArm64 ? "macos-arm64" : "macos-x64";
            logger.info("macOS Env Detected! Arch: " + archDir);
            resourcePath = "/natives/" + archDir + "/libmmd_engine.dylib";
            fileName = "libmmd_engine.dylib";
            downloadFileName = "libmmd_engine-" + archDir + ".dylib";
        } else if (isLinux) {
            String archDir;
                if (isArm64) {
                    archDir = "linux-arm64";
                } else if (isLoongArch64) {
                    archDir = "linux-loongarch64";
                } else if (isRiscv64) {
                    archDir = "linux-riscv64";
                } else {
                    archDir = "linux-x64";
                }
            logger.info("Linux Env Detected! Arch: " + archDir);
            resourcePath = "/natives/" + archDir + "/libmmd_engine.so";
            fileName = "libmmd_engine.so";
            downloadFileName = "libmmd_engine-" + archDir + ".so";
        } else {
            String osName = System.getProperty("os.name");
            logger.error("不支持的操作系统: " + osName);
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }
        
        File libFile = new File(getGameDirectory(), fileName);
        
        // 1. 优先从模组内置资源提取（确保版本一致）
        File extracted = extractNativeLibrary(resourcePath, fileName);
        if (extracted != null) {
            try {
                logger.info("尝试加载内置库: " + extracted.getAbsolutePath() + " (" + extracted.length() + " bytes)");
                LoadLibrary(extracted);
                return;
            } catch (Error e) {
                logger.error("内置库加载失败: " + e.getClass().getName() + ": " + e.getMessage(), e);
            }
        }
        
        // 2. 内置资源不可用时，从 GitHub Release 自动下载
        File downloaded = downloadNativeLibrary(downloadFileName, fileName);
        if (downloaded != null) {
            try {
                logger.info("尝试加载下载的库: " + downloaded.getAbsolutePath() + " (" + downloaded.length() + " bytes)");
                LoadLibrary(downloaded);
                return;
            } catch (Error e) {
                logger.error("下载的库加载失败: " + e.getClass().getName() + ": " + e.getMessage(), e);
            }
        }

        // 3. 回退到游戏目录的外部文件（用户自定义版本）
        // 同时检查不带后缀的文件名（如 mmd_engine.dll）和带平台后缀的文件名（如 mmd_engine-windows-x64.dll）
        File[] candidates = new File[] {
            libFile,
            new File(getGameDirectory(), downloadFileName)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) {
                try {
                    LoadLibrary(candidate);
                    logger.info("已从游戏目录加载原生库: " + candidate.getName());
                    return;
                } catch (Error e) {
                    logger.error("外部库文件加载失败: " + candidate.getName() + " - " + e.getMessage());
                }
            }
        }

        // 4. 全部失败
        throw new UnsatisfiedLinkError("无法加载原生库: " + fileName +
            "（也尝试了 " + downloadFileName + "），请检查网络连接或从 " + RELEASE_BASE_URL + " 手动下载");
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
    
    // ========== NativeRender 顶点构建（P2-9 优化）==========
    
    /**
     * 在 Rust 侧直接构建 MC NEW_ENTITY 顶点格式的交错数据
     * 
     * 消除 Java 侧逐顶点循环，将 SoA→AoS 转换 + 矩阵变换全部在 Rust 完成。
     * 
     * 顶点布局（每顶点 36 字节）：
     * Position(3×f32) + Color(4×u8) + UV0(2×f32) + Overlay(2×i16) + UV2(2×i16) + Normal(3×i8+pad)
     * 
     * @param model 模型句柄
     * @param subMeshIndex 子网格索引
     * @param buffer 输出缓冲区（DirectByteBuffer，需预分配 vertCount * 36 字节）
     * @param poseMatrix 4×4 模型变换矩阵（DirectByteBuffer，64 字节，列主序 float）
     * @param normalMatrix 3×3 法线变换矩阵（DirectByteBuffer，36 字节，列主序 float）
     * @param colorRGBA 打包的 RGBA 颜色值（如 0xFFFFFFFF 白色）
     * @param overlayUV 打包的 overlay 坐标（如 OverlayTexture.pack(0, 10)）
     * @param packedLight MC 打包光照值
     * @return 写入的顶点数量
     */
    public native int BuildMCVertexBuffer(
        long model, int subMeshIndex,
        java.nio.ByteBuffer buffer,
        java.nio.ByteBuffer poseMatrix,
        java.nio.ByteBuffer normalMatrix,
        int colorRGBA, int overlayUV, int packedLight
    );
    
    // ========== 物理配置相关 ==========
    
    /**
     * 设置全局物理配置（实时调整，保存时调用）
     * @param gravityY 重力 Y 分量（负数向下）
     * @param physicsFps 物理模拟 FPS
     * @param maxSubstepCount 每帧最大子步数
     * @param solverIterations 求解器迭代次数
     * @param pgsIterations PGS 迭代次数
     * @param maxCorrectiveVelocity 最大修正速度
     * @param linearDampingScale 线性阻尼缩放
     * @param angularDampingScale 角速度阻尼缩放
     * @param massScale 质量缩放
     * @param linearSpringStiffnessScale 线性弹簧刚度缩放
     * @param angularSpringStiffnessScale 角度弹簧刚度缩放
     * @param linearSpringDampingFactor 线性弹簧阻尼系数
     * @param angularSpringDampingFactor 角度弹簧阻尼系数
     * @param inertiaStrength 惯性效果强度
     * @param maxLinearVelocity 最大线速度
     * @param maxAngularVelocity 最大角速度
     * @param bustPhysicsEnabled 胸部物理是否启用
     * @param bustLinearDampingScale 胸部线性阻尼缩放
     * @param bustAngularDampingScale 胸部角速度阻尼缩放
     * @param bustMassScale 胸部质量缩放
     * @param bustLinearSpringStiffnessScale 胸部线性弹簧刚度缩放
     * @param bustAngularSpringStiffnessScale 胸部角度弹簧刚度缩放
     * @param bustLinearSpringDampingFactor 胸部线性弹簧阻尼系数
     * @param bustAngularSpringDampingFactor 胸部角度弹簧阻尼系数
     * @param bustClampInward 胸部防凹陷修正是否启用
     * @param jointsEnabled 是否启用关节
     * @param debugLog 是否输出调试日志
     */
    public native void SetPhysicsConfig(
        float gravityY,
        float physicsFps,
        int maxSubstepCount,
        int solverIterations,
        int pgsIterations,
        float maxCorrectiveVelocity,
        float linearDampingScale,
        float angularDampingScale,
        float massScale,
        float linearSpringStiffnessScale,
        float angularSpringStiffnessScale,
        float linearSpringDampingFactor,
        float angularSpringDampingFactor,
        float inertiaStrength,
        float maxLinearVelocity,
        float maxAngularVelocity,
        boolean bustPhysicsEnabled,
        float bustLinearDampingScale,
        float bustAngularDampingScale,
        float bustMassScale,
        float bustLinearSpringStiffnessScale,
        float bustAngularSpringStiffnessScale,
        float bustLinearSpringDampingFactor,
        float bustAngularSpringDampingFactor,
        boolean bustClampInward,
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
}
