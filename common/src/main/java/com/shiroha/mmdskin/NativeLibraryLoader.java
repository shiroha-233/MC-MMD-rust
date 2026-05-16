package com.shiroha.mmdskin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 原生库加载器
 *
 * 桌面端：每次启动从 JAR 内提取原生库到临时目录，加载后退出时 JVM 自动清理。
 * Android 端：沿用独立加载路径。
 */

public final class NativeLibraryLoader {
    private static final Logger logger = LogManager.getLogger();

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
                androidDetected = new File("/system/build.prop").exists();
            } catch (Exception ignored) {}
        }
        if (!androidDetected) {
            String vendor = System.getProperty("java.vendor", "").toLowerCase();
            String vmName = System.getProperty("java.vm.name", "").toLowerCase();
            androidDetected = vendor.contains("android") || vmName.contains("dalvik") || vmName.contains("art");
        }
        isAndroid = androidDetected;
        isLinux = System.getProperty("os.name").toLowerCase().contains("linux") && !isAndroid;
    }

    public static boolean isAndroid() { return isAndroid; }

    static final String LIBRARY_VERSION = "v1.0.5";

    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "mmdskin-natives");

    private NativeLibraryLoader() {}

    static void loadAndVerify(NativeFunc instance) {
        if (isAndroid) {
            loadAndroid();
        } else {
            loadDesktop();
        }
        verifyLoadedLibraryVersion(instance);
    }

    // ========================================================================
    // 桌面端：从 JAR 提取到临时目录 → System.load
    // ========================================================================

    private static void loadDesktop() {
        String resourcePath;
        String fileName;

        if (isWindows) {
            String archDir = isArm64 ? "windows-arm64" : "windows-x64";
            resourcePath = "/natives/" + archDir + "/mmd_engine.dll";
            fileName = "mmd_engine.dll";
        } else if (isMacOS) {
            String archDir = isArm64 ? "macos-arm64" : "macos-x64";
            resourcePath = "/natives/" + archDir + "/libmmd_engine.dylib";
            fileName = "libmmd_engine.dylib";
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
            resourcePath = "/natives/" + archDir + "/libmmd_engine.so";
            fileName = "libmmd_engine.so";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + System.getProperty("os.name"));
        }

        logger.info("原生库 JAR 资源: {}", resourcePath);

        // 清理上次残留的临时文件
        cleanTempDir();

        // 创建临时目录
        try {
            Files.createDirectories(TEMP_DIR);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("无法创建临时目录: " + TEMP_DIR);
        }

        // 从 JAR 提取到临时目录
        Path targetPath = TEMP_DIR.resolve(fileName);
        try {
            extractFromJar(resourcePath, targetPath);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("无法提取原生库 " + resourcePath + ": " + e.getMessage());
        }

        // Mark deleteOnExit so JVM cleans up
        try {
            targetPath.toFile().deleteOnExit();
        } catch (Exception ignored) {}

        // 加载
        try {
            System.load(targetPath.toAbsolutePath().toString());
            logger.info("原生库加载成功: {}", targetPath);
        } catch (UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError("加载原生库失败: " + e.getMessage());
        }
    }

    private static void extractFromJar(String resourcePath, Path targetPath) throws IOException {
        try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("JAR 内资源不存在: " + resourcePath);
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void cleanTempDir() {
        File dir = TEMP_DIR.toFile();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                try {
                    Files.deleteIfExists(f.toPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ========================================================================
    // Android 端：加载路径保持不变
    // ========================================================================

    private static void loadAndroid() {
        String archDir = "android-a" + (isArm64 ? "rm" : "md") + "64";
        String soFileName = "libmmd_engine.so";
        String resourcePath = "/natives/" + archDir + "/" + soFileName;

        // 尝试从环境指定的目录加载 (Pojav/FCL)
        String nativeDirEnv = System.getenv("POJAV_NATIVEDIR");
        if (nativeDirEnv == null) {
            nativeDirEnv = System.getenv("FCL_NATIVEDIR");
        }
        if (nativeDirEnv != null) {
            File envDir = new File(nativeDirEnv);
            if (envDir.isDirectory()) {
                File envLib = new File(envDir, soFileName);
                if (envLib.isFile()) {
                    try {
                        System.load(envLib.getAbsolutePath());
                        return;
                    } catch (Error ignored) {
                    }
                }
            }
        }

        // 从 JAR 提取到应用缓存目录
        cleanTempDir();
        try {
            Files.createDirectories(TEMP_DIR);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("[Android] 无法创建原生库目录: " + TEMP_DIR);
        }

        Path targetPath = TEMP_DIR.resolve(soFileName);
        try {
            extractFromJar(resourcePath, targetPath);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("[Android] 无法提取原生库: " + resourcePath);
        }

        try {
            System.load(targetPath.toAbsolutePath().toString());
        } catch (Error e) {
            throw new UnsatisfiedLinkError("[Android] 加载原生库失败: " + e.getMessage());
        }
    }

    // ========================================================================
    // 版本校验
    // ========================================================================

    private static void verifyLoadedLibraryVersion(NativeFunc instance) {
        try {
            String rustVersion = instance.GetVersion();
            if (LIBRARY_VERSION.equals(rustVersion)) {
                return;
            }
            if (!isAndroid) {
                logger.warn("原生库版本不匹配！Java 侧期望: {}, Rust 侧实际: {}", LIBRARY_VERSION, rustVersion);
                logger.warn("请确保 Rust 引擎和 Java 模组版本一致。");
            }
        } catch (Exception | Error e) {
            if (!isAndroid) {
                logger.warn("运行时版本校验失败（GetVersion 调用异常）: {}", e.getMessage());
            }
        }
    }
}
