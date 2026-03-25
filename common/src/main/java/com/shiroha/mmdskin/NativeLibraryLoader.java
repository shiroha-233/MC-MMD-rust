package com.shiroha.mmdskin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 原生库加载器（SRP：仅负责平台检测、库提取/下载/清理/加载）
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

    static final String LIBRARY_VERSION = "v1.0.4";
    private static final String RELEASE_BASE_URL =
            "https://github.com/shiroha-23/MC-MMD-rust/releases/download/" + LIBRARY_VERSION + "/";

    private static volatile String gameDirectory;
    private static final Object DIR_LOCK = new Object();

    private static String getGameDirectory() {
        if (gameDirectory == null) {
            synchronized (DIR_LOCK) {
                if (gameDirectory == null) {
                    gameDirectory = Minecraft.getInstance().gameDirectory.getAbsolutePath();
                }
            }
        }
        return gameDirectory;
    }

    private NativeLibraryLoader() {}

    static void loadAndVerify(NativeFunc instance) {
        if (isAndroid) {
            loadAndroid();
        } else {
            loadDesktop();
        }
        verifyLoadedLibraryVersion(instance);
    }

    private static void loadDesktop() {
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

        cleanupOldLibraries(fileName, downloadFileName);

        File extracted = extractNativeLibrary(resourcePath, fileName);
        if (extracted != null) {
            try {
                System.load(extracted.getAbsolutePath());
                return;
            } catch (Error e) {
                logger.error("内置库加载失败: " + e.getClass().getName() + ": " + e.getMessage(), e);
            }
        }

        File downloaded = downloadNativeLibrary(downloadFileName);
        if (downloaded != null) {
            try {
                System.load(downloaded.getAbsolutePath());
                return;
            } catch (Error e) {
                logger.error("下载的库加载失败: " + e.getClass().getName() + ": " + e.getMessage(), e);
            }
        }

        throw new UnsatisfiedLinkError("无法加载原生库: " + getVersionedFileName(fileName)
                + "，请检查网络连接或从 " + RELEASE_BASE_URL + " 手动下载");
    }

    private static void loadAndroid() {
        logger.info("Android Env Detected! Arch: a" + (isArm64 ? "rm" : "md") + "64");

        String resourcePath = "/natives/android-a" + (isArm64 ? "rm" : "md") + "64/libmmd_engine.so";
        String libcResPath = "/natives/android-a" + (isArm64 ? "rm" : "md") + "64/libc++_shared.so";
        String libcPath = findLibcPath();
        String soFileName = "libmmd_engine.so";
        String libcFileName = "libc++_shared.so";

        boolean isLibcLoaded = isLibcLoaded();

        if (!isLibcLoaded && libcPath != null) {
            try {
                System.load(libcPath);
                logger.info("[Android] libc++_shared.so 已加载：" + libcPath);
                isLibcLoaded = true;
            } catch (Throwable e) {
                logger.warn("[Android] " + libcFileName + "加载失败。", e);
                libcPath = null;
            }
        }

        var javaHome = System.getProperty("java.home");
        var javaLibDir = new File(javaHome, "lib");
        var javaLibSoFile = new File(javaLibDir, soFileName);
        if (javaLibDir.canWrite()) {
            try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.warn("[Android] 策略0: 内置资源未找到: " + resourcePath);
                } else {

                    if (!isLibcLoaded) {
                        isLibcLoaded = ensureLibcLoaded(libcResPath, libcFileName, javaLibDir);
                    }

                    if (isLibcLoaded) {
                        Files.copy(is, javaLibSoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        System.load(javaLibSoFile.getAbsolutePath());
                        return;
                    }
                }
            } catch (IOException | Error e) {
                logger.error("[Android] " + javaLibSoFile.getAbsolutePath() + "加载失败：" + e.getMessage());
            }
        } else {
            logger.warn("[Android] JAVA_HOME 无法写入，跳过");
        }

        try {

            if (!isLibcLoaded) {
                System.loadLibrary("c++_shared");
                isLibcLoaded = isLibcLoaded();
            }
            if (isLibcLoaded) {
                    System.loadLibrary("mmd_engine");
                    return;
            }
        } catch (Error e) {
            logger.warn("[Android] 策略1 失败: " + e.getMessage());
        }

        String modRuntimeDir = System.getenv("MOD_ANDROID_RUNTIME");
        if (modRuntimeDir != null && !modRuntimeDir.isEmpty()) {
            try {
                if (!isLibcLoaded) {

                    isLibcLoaded = ensureLibcLoaded(libcResPath, libcFileName, new File(modRuntimeDir));
                }
                if (isLibcLoaded) {
                    File runtimeDir = new File(modRuntimeDir);
                    if (!runtimeDir.exists()) runtimeDir.mkdirs();
                    File targetFile = new File(runtimeDir, soFileName);

                    try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
                        if (is != null) {
                            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                System.load(targetFile.getAbsolutePath());
                                return;
                        }
                    }
                }
            } catch (Exception | Error e) {
                logger.warn("[Android] 策略2 失败 (MOD_ANDROID_RUNTIME): " + e.getMessage());
            }
        }

        String pojavNativeDir = System.getenv("POJAV_NATIVEDIR");
        if (pojavNativeDir != null && !pojavNativeDir.isEmpty()) {
            try {
                if (!isLibcLoaded) {
                    File nativeDirTemp = new File(pojavNativeDir);
                    isLibcLoaded = ensureLibcLoaded(libcResPath, libcFileName, nativeDirTemp);
                }
                if (isLibcLoaded) {
                    File nativeDir = new File(pojavNativeDir);
                    if (!nativeDir.exists()) nativeDir.mkdirs();
                    File targetFile = new File(nativeDir, soFileName);

                    try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
                        if (is != null) {
                            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            System.load(targetFile.getAbsolutePath());
                            return;
                        }
                    }
                }
            } catch (Exception | Error e) {
                logger.warn("[Android] 策略3 失败 (POJAV_NATIVEDIR): " + e.getMessage());
            }
        }

        File extracted = extractNativeLibrary(resourcePath, soFileName);
        if (extracted != null) {
            try {
                if (!isLibcLoaded) {
                    File gameDir = new File(getGameDirectory());
                    isLibcLoaded = ensureLibcLoaded(libcResPath, libcFileName, gameDir);
                }
                if (isLibcLoaded) {
                        System.load(extracted.getAbsolutePath());
                        return;
                }
            } catch (Error e) {
                logger.error("[Android] 策略4 失败 (游戏目录): " + e.getClass().getName() + ": " + e.getMessage());
            }
        }

        File downloaded = downloadNativeLibrary("libmmd_engine-android-a" + (isArm64 ? "rm" : "md") + "64.so");
        if (downloaded != null) {
            try {
                if (!isLibcLoaded) {
                    File gameDir = new File(getGameDirectory());
                    isLibcLoaded = ensureLibcLoaded(libcResPath, libcFileName, gameDir);
                }
                if (isLibcLoaded) {
                        System.load(downloaded.getAbsolutePath());
                        return;
                }
            } catch (Error e) {
                logger.error("[Android] 策略5 失败 (下载): " + e.getClass().getName() + ": " + e.getMessage());
            }
        }

        if (!isLibcLoaded)
            throw new UnsatisfiedLinkError("[Android] 无法加载 libc++_shared.so，请尝试重启游戏。如果仍出现此错误，请到github寻求帮助。\n Couldn't load libc++_shared.so. Please try restarting the game. If the error persists, please seek help on GitHub.\nhttps://github.com/shiroha-233/MC-MMD-rust/issues/");
        else throw new UnsatisfiedLinkError("[Android] 无法加载原生库 libmmd_engine.so，所有策略均失败。"
                + "请检查日志获取详细信息，或从 " + RELEASE_BASE_URL + " 手动下载 "
                + getVersionedFileName("libmmd_engine-android-a" + (isArm64 ? "rm" : "md") + "64.so"));
    }

    private static boolean isLibcLoaded() {
        File[] maps = new File("/proc/" + ProcessHandle.current().pid() + "/map_files").listFiles();
        for (var f : maps)
            if (f.getAbsolutePath().contains("libc++_shared.so")) {
                logger.info("[Android] libc++_shared.so 已预加载：" + f.getAbsolutePath());
                return true;
            }
        logger.warn("[Android] 未加载libc++_shared.so，将在之后尝试加载。");
        return false;
    }

    private static String findLibcPath() {
        String libcName = "libc++_shared.so";
        String libPaths = System.getenv("LD_LIBRARY_PATH");
        String pojavLibs = System.getenv("POJAV_NATIVEDIR");
        String fclLibs = System.getenv("FCL_NATIVEDIR");
        String javaLib = System.getProperty("java.home") + "/lib";

        var dirsToSearch = new java.util.ArrayList<File>();
        if (libPaths != null && !libPaths.isEmpty()) {
            for (String part : libPaths.split(":")) {
                if (!part.isEmpty()) {
                    dirsToSearch.add(new File(part));
                }
            }
        }
        if (pojavLibs != null && !pojavLibs.isEmpty()) {
            dirsToSearch.add(new File(pojavLibs));
        }
        if (fclLibs != null && !fclLibs.isEmpty()) {
            dirsToSearch.add(new File(fclLibs));
        }
        dirsToSearch.add(new File(javaLib));

        for (var dir : dirsToSearch)
            if (dir.isDirectory() && dir.isAbsolute() && dir.canRead())
                for (var file : dir.listFiles())
                    if (file.getName().equals(libcName) && file.isFile())
                        return file.getAbsolutePath();
        return null;
    }

    private static boolean ensureLibcLoaded(String libcResource, String libcFileName, File targetDir) {
        if (isLibcLoaded()) {
            return true;
        }
        if (targetDir == null) {
            targetDir = new File(getGameDirectory());
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            logger.warn("[Android] 无法创建目标目录以解压 libc: " + targetDir);
        }

        if (!targetDir.canWrite()) {
            logger.warn("[Android] 目标目录不可写，无法解压 libc: " + targetDir);
            return false;
        }

        try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(libcResource)) {
            if (is == null) {
                logger.warn("[Android] 内置 libc 资源未找到: " + libcResource);
                return false;
            }
            File out = new File(targetDir, libcFileName);
            Files.copy(is, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("[Android] 已将 libc++_shared.so 解压至 " + out.getAbsolutePath());
            try {
                System.load(out.getAbsolutePath());
            } catch (Throwable t) {
                logger.warn("[Android] 解压后的 libc 加载失败: " + t.getMessage(), t);
            }
        } catch (IOException e) {
            logger.warn("[Android] 解压 libc 失败: " + e.getMessage(), e);
        }
        return isLibcLoaded();
    }

    private static String getVersionedFileName(String baseFileName) {
        int dotIndex = baseFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return baseFileName.substring(0, dotIndex) + "_" + LIBRARY_VERSION + baseFileName.substring(dotIndex);
        }
        return baseFileName + "_" + LIBRARY_VERSION;
    }

    private static File extractNativeLibrary(String resourcePath, String fileName) {
        try {
            String versionedName = getVersionedFileName(fileName);
            Path targetPath = Paths.get(getGameDirectory(), versionedName);
            File targetFile = targetPath.toFile();

            if (targetFile.exists()) {
                return targetFile;
            }

            try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.warn("内置原生库未找到: " + resourcePath);
                    return null;
                }
                Path tempPath = Paths.get(getGameDirectory(), versionedName + ".tmp");
                Files.copy(is, tempPath, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return targetFile;
        } catch (Exception e) {
            logger.error("提取原生库失败: " + resourcePath + " - " + e.getMessage());
            return null;
        }
    }

    private static File downloadNativeLibrary(String downloadFileName) {
        try {
            String versionedName = getVersionedFileName(downloadFileName);
            Path targetPath = Paths.get(getGameDirectory(), versionedName);

            if (targetPath.toFile().exists()) {
                return targetPath.toFile();
            }

            String urlStr = RELEASE_BASE_URL + versionedName;

            HttpURLConnection conn = null;
            for (int i = 0; i < 5; i++) {
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "MMDSkin-Mod/" + LIBRARY_VERSION);

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

            Path tempPath = Paths.get(getGameDirectory(), versionedName + ".download");
            try (InputStream is = conn.getInputStream()) {
                Files.copy(is, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            conn.disconnect();
            return targetPath.toFile();
        } catch (Exception e) {
            logger.error("下载原生库失败: " + downloadFileName + " - " + e.getMessage());
            try {
                Files.deleteIfExists(Paths.get(getGameDirectory(), getVersionedFileName(downloadFileName) + ".download"));
            } catch (Exception ignored) {}
            return null;
        }
    }

    private static void cleanupOldLibraries(String baseFileName, String downloadBaseFileName) {
        try {
            File gameDir = new File(getGameDirectory());
            int dotIndex = baseFileName.lastIndexOf('.');
            if (dotIndex <= 0) return;

            String baseName = baseFileName.substring(0, dotIndex);
            String ext = baseFileName.substring(dotIndex);
            String currentVersionedLocal = getVersionedFileName(baseFileName);
            String currentVersionedDownload = getVersionedFileName(downloadBaseFileName);

            File[] files = gameDir.listFiles();
            if (files == null) return;

            for (File f : files) {
                String name = f.getName();
                if (name.equals(currentVersionedLocal) || name.equals(currentVersionedDownload)) continue;

                boolean shouldDelete = false;

                if (name.startsWith(baseName) && name.contains("_v") && (name.endsWith(ext)
                        || name.endsWith(ext + ".download") || name.endsWith(ext + ".tmp"))) {
                    shouldDelete = true;
                }
                if (name.equals(baseFileName)
                        || name.equals(baseFileName + ".version")
                        || name.equals(baseFileName + ".old")
                        || name.equals(baseFileName + ".new")
                        || name.equals(baseFileName + ".download")) {
                    shouldDelete = true;
                }

                if (shouldDelete) {
                    try {
                        if (f.delete()) {
                        }
                    } catch (Exception e) {
                        logger.debug("清理旧文件失败（可能仍被锁定）: " + name);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("清理旧版本库文件时出错: " + e.getMessage());
        }
    }

    private static void verifyLoadedLibraryVersion(NativeFunc instance) {
        try {
            String rustVersion = instance.GetVersion();
            if (LIBRARY_VERSION.equals(rustVersion)) {
                return;
            }
            logger.warn("原生库版本不匹配！Java 侧期望: " + LIBRARY_VERSION + ", Rust 侧实际: " + rustVersion);
            logger.warn("这通常发生在开发环境或手动放置库文件时，请确保 Rust 引擎和 Java 模组版本一致。");
        } catch (Exception | Error e) {
            logger.warn("运行时版本校验失败（GetVersion 调用异常）: " + e.getMessage());
        }
    }
}
