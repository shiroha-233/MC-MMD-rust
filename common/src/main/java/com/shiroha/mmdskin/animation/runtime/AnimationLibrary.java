package com.shiroha.mmdskin.animation.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationBridge;
import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.config.ModelAnimConfig;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：为单个模型实例管理动画句柄缓存与释放。 */
public final class AnimationLibrary {
    private static final Logger logger = LogManager.getLogger();
    private static final String[] ANIMATION_EXTENSIONS = {".vmd", ".fbx"};
    private static final Set<String> warnedAnimations = ConcurrentHashMap.newKeySet();
    private static volatile String defaultAnimDir;
    private static volatile String customAnimDir;

    private final NativeAnimationPort nativeAnimationPort;
    private final ModelInstance modelInstance;
    private final Map<String, Long> handlesByName = new ConcurrentHashMap<>();

    public AnimationLibrary(ModelInstance modelInstance) {
        this(modelInstance, new NativeAnimationBridge());
    }

    AnimationLibrary(ModelInstance modelInstance, NativeAnimationPort nativeAnimationPort) {
        this.modelInstance = modelInstance;
        this.nativeAnimationPort = nativeAnimationPort;
        initializeDirectories();
    }

    public long animation(String animationName) {
        if (animationName == null || animationName.isBlank()) {
            return 0L;
        }
        return handlesByName.computeIfAbsent(animationName, this::loadAnimation);
    }

    public void invalidate() {
        handlesByName.values().forEach(this::deleteHandle);
        handlesByName.clear();
    }

    public void dispose() {
        invalidate();
    }

    private long loadAnimation(String animationName) {
        String modelDir = modelInstance.modelDir();
        long animationHandle = 0L;

        if (modelDir != null) {
            String mappedFile = ModelAnimConfig.getMappedFile(modelDir, animationName);
            if (mappedFile != null) {
                animationHandle = tryLoadMapped(modelDir, mappedFile, animationName);
            }
        }

        if (animationHandle == 0L && modelDir != null) {
            animationHandle = tryLoadFromDirectory(PathConstants.getModelAnimsDirByPath(modelDir).getAbsolutePath(), animationName);
        }
        if (animationHandle == 0L && modelDir != null) {
            animationHandle = tryLoadFromDirectory(modelDir, animationName);
        }
        if (animationHandle == 0L) {
            animationHandle = tryLoadFromDirectory(customAnimDir, animationName);
        }
        if (animationHandle == 0L) {
            animationHandle = tryLoadFromDirectory(defaultAnimDir, animationName);
        }

        if (animationHandle == 0L && warnedAnimations.add(animationName)) {
            logger.warn("Animation not found: {}", animationName);
        }
        return animationHandle;
    }

    private long tryLoadMapped(String modelDir, String mappedFile, String animationName) {
        if (mappedFile.contains("..") || mappedFile.contains("/") || mappedFile.contains("\\")) {
            logger.warn("Invalid mapped animation file name: {} (slot: {})", mappedFile, animationName);
            return 0L;
        }
        File animsDir = PathConstants.getModelAnimsDirByPath(modelDir);
        File target = new File(animsDir, mappedFile);
        if (target.exists()) {
            return nativeAnimationPort.loadAnimation(modelInstance.modelHandle(), target.getAbsolutePath());
        }
        target = new File(modelDir, mappedFile);
        if (target.exists()) {
            return nativeAnimationPort.loadAnimation(modelInstance.modelHandle(), target.getAbsolutePath());
        }
        logger.warn("Mapped animation file not found: {} -> {} (slot: {})", modelDir, mappedFile, animationName);
        return 0L;
    }

    private long tryLoadFromDirectory(String directory, String animationName) {
        if (directory == null || directory.isBlank()) {
            return 0L;
        }
        for (String extension : ANIMATION_EXTENSIONS) {
            File file = new File(directory, animationName + extension);
            if (file.exists()) {
                return nativeAnimationPort.loadAnimation(modelInstance.modelHandle(), file.getAbsolutePath());
            }
        }

        File dirFile = new File(directory);
        if (dirFile.isDirectory()) {
            File[] fbxFiles = dirFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".fbx"));
            if (fbxFiles != null) {
                for (File fbx : fbxFiles) {
                    long handle = nativeAnimationPort.loadAnimation(modelInstance.modelHandle(), fbx.getAbsolutePath() + "#" + animationName);
                    if (handle != 0L) {
                        return handle;
                    }
                }
            }
        }
        return 0L;
    }

    private void deleteHandle(Long handle) {
        if (handle != null && handle != 0L) {
            nativeAnimationPort.deleteAnimation(handle);
        }
    }

    private static synchronized void initializeDirectories() {
        if (defaultAnimDir != null && customAnimDir != null) {
            return;
        }
        PathConstants.ensureDirectoryExists(PathConstants.getDefaultAnimDir());
        PathConstants.ensureDirectoryExists(PathConstants.getCustomAnimDir());
        defaultAnimDir = PathConstants.getDefaultAnimDir().getAbsolutePath();
        customAnimDir = PathConstants.getCustomAnimDir().getAbsolutePath();
    }
}
