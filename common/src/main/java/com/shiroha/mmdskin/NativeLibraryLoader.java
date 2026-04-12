package com.shiroha.mmdskin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 原生库加载器，只负责平台识别、临时解压、加载与版本校验。
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
        String[] launcherEnvKeys = {"FCL_NATIVEDIR", "POJAV_NATIVEDIR", "MOD_ANDROID_RUNTIME", "FCL_VERSION_CODE"};
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
            } catch (Exception ignored) {
            }
        }
        if (!androidDetected) {
            String vendor = System.getProperty("java.vendor", "").toLowerCase();
            String vmName = System.getProperty("java.vm.name", "").toLowerCase();
            androidDetected = vendor.contains("android") || vmName.contains("dalvik") || vmName.contains("art");
        }
        isAndroid = androidDetected;
        isLinux = System.getProperty("os.name").toLowerCase().contains("linux") && !isAndroid;
    }

    static final String LIBRARY_VERSION = "v1.0.4";

    private static final String TEMP_DIR_PREFIX = "mmdskin-native-" + LIBRARY_VERSION + "-";
    private static final Object TEMP_DIR_LOCK = new Object();
    private static volatile Path sessionTempDirectory;

    public static boolean isAndroid() {
        return isAndroid;
    }

    private NativeLibraryLoader() {
    }

    static void loadAndVerify(NativeFunc instance) {
        for (NativeLibrarySpec library : resolveLibrariesForCurrentPlatform()) {
            loadBundledLibrary(library);
        }
        verifyLoadedLibraryVersion(instance);
    }

    private static List<NativeLibrarySpec> resolveLibrariesForCurrentPlatform() {
        if (isAndroid) {
            String archDir = isArm64 ? "android-arm64" : "android-amd64";
            logger.info("Android Env Detected! Arch: " + archDir);
            return List.of(
                    new NativeLibrarySpec("/natives/" + archDir + "/libc++_shared.so", "libc++_shared.so"),
                    new NativeLibrarySpec("/natives/" + archDir + "/libmmd_engine.so", "libmmd_engine.so")
            );
        }

        if (isWindows) {
            String archDir = isArm64 ? "windows-arm64" : "windows-x64";
            logger.info("Windows Env Detected! Arch: " + archDir);
            return List.of(new NativeLibrarySpec("/natives/" + archDir + "/mmd_engine.dll", "mmd_engine.dll"));
        }

        if (isMacOS) {
            String archDir = isArm64 ? "macos-arm64" : "macos-x64";
            logger.info("macOS Env Detected! Arch: " + archDir);
            return List.of(new NativeLibrarySpec("/natives/" + archDir + "/libmmd_engine.dylib", "libmmd_engine.dylib"));
        }

        if (isLinux) {
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
            return List.of(new NativeLibrarySpec("/natives/" + archDir + "/libmmd_engine.so", "libmmd_engine.so"));
        }

        String osName = System.getProperty("os.name");
        throw new UnsupportedOperationException("Unsupported OS: " + osName);
    }

    private static void loadBundledLibrary(NativeLibrarySpec library) {
        Path extractedPath = extractBundledLibrary(library);
        try {
            System.load(extractedPath.toAbsolutePath().toString());
        } catch (Error error) {
            throw buildLinkError(
                    "无法加载内置原生库: " + library.resourcePath() + " -> " + extractedPath + "，原因: " + error.getMessage(),
                    error
            );
        }
    }

    private static Path extractBundledLibrary(NativeLibrarySpec library) {
        Path targetPath = ensureSessionTempDirectory().resolve(library.fileName());
        try (InputStream is = NativeLibraryLoader.class.getResourceAsStream(library.resourcePath())) {
            if (is == null) {
                throw new UnsatisfiedLinkError("未找到内置原生库资源: " + library.resourcePath());
            }

            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            markForDeletionOnExit(targetPath);
            return targetPath;
        } catch (IOException exception) {
            throw buildLinkError(
                    "解压内置原生库失败: " + library.resourcePath() + " -> " + targetPath + "，原因: " + exception.getMessage(),
                    exception
            );
        }
    }

    private static Path ensureSessionTempDirectory() {
        if (sessionTempDirectory == null) {
            synchronized (TEMP_DIR_LOCK) {
                if (sessionTempDirectory == null) {
                    sessionTempDirectory = createSessionTempDirectory();
                }
            }
        }
        return sessionTempDirectory;
    }

    private static Path createSessionTempDirectory() {
        IOException lastError = null;
        for (Path root : resolveTempRootCandidates()) {
            try {
                Files.createDirectories(root);
                if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                    continue;
                }

                Path directory = Files.createTempDirectory(root, TEMP_DIR_PREFIX);
                markForDeletionOnExit(directory);
                registerShutdownCleanup(directory);
                logger.info("原生库临时目录: " + directory.toAbsolutePath());
                return directory;
            } catch (IOException exception) {
                lastError = exception;
                logger.debug("无法在临时目录候选路径创建原生库目录: " + root, exception);
            }
        }

        throw buildLinkError("无法创建原生库临时目录", lastError);
    }

    private static List<Path> resolveTempRootCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        addCandidate(candidates, System.getProperty("java.io.tmpdir"));

        if (isAndroid) {
            addCandidate(candidates, System.getenv("MOD_ANDROID_RUNTIME"));
            addCandidate(candidates, System.getenv("POJAV_NATIVEDIR"));
            addCandidate(candidates, System.getenv("FCL_NATIVEDIR"));
        }

        addCandidate(candidates, System.getProperty("user.home"));
        return List.copyOf(candidates);
    }

    private static void addCandidate(LinkedHashSet<Path> candidates, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }

        try {
            candidates.add(Path.of(rawPath).toAbsolutePath().normalize());
        } catch (Exception ignored) {
        }
    }

    private static void registerShutdownCleanup(Path directory) {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> deleteRecursively(directory),
                "mmdskin-native-cleanup"
        ));
    }

    private static void markForDeletionOnExit(Path path) {
        path.toFile().deleteOnExit();
    }

    private static void deleteRecursively(Path path) {
        if (path == null || Files.notExists(path)) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            logger.debug("清理原生库临时目录失败: " + path, exception);
        }
    }

    private static UnsatisfiedLinkError buildLinkError(String message, Throwable cause) {
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(message);
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    private static void verifyLoadedLibraryVersion(NativeFunc instance) {
        try {
            String rustVersion = instance.GetVersion();
            if (LIBRARY_VERSION.equals(rustVersion)) {
                return;
            }
            logger.warn("原生库版本不匹配！Java 侧期望: " + LIBRARY_VERSION + ", Rust 侧实际: " + rustVersion);
            logger.warn("这通常发生在开发环境或手动替换内置库文件时，请确保 Rust 引擎和 Java 模组版本一致。");
        } catch (Exception | Error e) {
            logger.warn("运行时版本校验失败（GetVersion 调用异常）: " + e.getMessage());
        }
    }

    private record NativeLibrarySpec(String resourcePath, String fileName) {
    }
}
