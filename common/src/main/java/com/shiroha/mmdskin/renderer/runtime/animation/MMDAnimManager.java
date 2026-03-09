package com.shiroha.mmdskin.renderer.runtime.animation;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ModelAnimConfig;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MMDAnimManager {
    public static final Logger logger = LogManager.getLogger();

    static NativeFunc nf;
    static Map<IMMDModel, Map<String, Long>> animModel;
    static String defaultAnimDir;
    static String customAnimDir;
    static Set<String> warnedAnimations;

    private static final String[] ANIM_EXTENSIONS = {".vmd", ".fbx"};

    public static void Init() {
        nf = NativeFunc.GetInst();
        animModel = new ConcurrentHashMap<>();
        warnedAnimations = ConcurrentHashMap.newKeySet();

        defaultAnimDir = PathConstants.getDefaultAnimDir().getAbsolutePath();
        customAnimDir = PathConstants.getCustomAnimDir().getAbsolutePath();

        ensureDirectoriesExist();
    }

    private static void ensureDirectoriesExist() {
        PathConstants.ensureDirectoryExists(PathConstants.getDefaultAnimDir());
        PathConstants.ensureDirectoryExists(PathConstants.getCustomAnimDir());
    }

    public static void AddModel(IMMDModel model) {
        animModel.put(model, new ConcurrentHashMap<>());
    }

    public static void DeleteModel(IMMDModel model) {
        if (nf == null || animModel == null) return;

        Map<String, Long> sub = animModel.get(model);
        if (sub != null) {
            for (Long handle : sub.values()) {
                if (handle != null) {
                    nf.DeleteAnimation(handle);
                }
            }
        }
        animModel.remove(model);
    }

    public static long GetAnimModel(IMMDModel model, String animName) {
        Map<String, Long> sub = animModel.get(model);
        if (sub == null) return 0;

        Long cached = sub.get(animName);
        if (cached != null) {
            return cached;
        }

        String modelDir = model.getModelDir();
        long anim = 0;

        if (anim == 0 && modelDir != null) {
            String mappedFile = ModelAnimConfig.getMappedFile(modelDir, animName);
            if (mappedFile != null) {
                anim = tryLoadAnimation(model, modelDir, mappedFile, animName);
            }
        }

        if (anim == 0 && modelDir != null) {
            String animsDir = PathConstants.getModelAnimsDirByPath(modelDir).getAbsolutePath();
            anim = tryLoadFromDir(model, animsDir, animName);
        }

        if (anim == 0 && modelDir != null) {
            anim = tryLoadFromDir(model, modelDir, animName);
        }

        if (anim == 0) {
            anim = tryLoadFromDir(model, customAnimDir, animName);
        }

        if (anim == 0) {
            anim = tryLoadFromDir(model, defaultAnimDir, animName);
        }

        sub.put(animName, anim);
        if (anim == 0 && warnedAnimations.add(animName)) {
            logger.warn("Animation not found: {}", animName);
        }

        return anim;
    }

    private static long tryLoadFromDir(IMMDModel model, String dir, String animName) {
        for (String ext : ANIM_EXTENSIONS) {
            File file = new File(dir, animName + ext);
            if (file.exists()) {
                return nf.LoadAnimation(model.getModelHandle(), file.getAbsolutePath());
            }
        }

        File dirFile = new File(dir);
        if (dirFile.isDirectory()) {
            File[] fbxFiles = dirFile.listFiles((d, name) -> name.toLowerCase().endsWith(".fbx"));
            if (fbxFiles != null) {
                for (File fbx : fbxFiles) {
                    long handle = nf.LoadAnimation(model.getModelHandle(), fbx.getAbsolutePath() + "#" + animName);
                    if (handle != 0) return handle;
                }
            }
        }

        return 0;
    }

    private static long tryLoadAnimation(IMMDModel model, String modelDir, String mappedFile, String animName) {
        if (mappedFile.contains("..") || mappedFile.contains("/") || mappedFile.contains("\\")) {
            logger.warn("Invalid mapped animation file name: {} (slot: {})", mappedFile, animName);
            return 0;
        }

        File animsDir = PathConstants.getModelAnimsDirByPath(modelDir);
        File target = new File(animsDir, mappedFile);
        if (target.exists()) {
            return nf.LoadAnimation(model.getModelHandle(), target.getAbsolutePath());
        }

        target = new File(modelDir, mappedFile);
        if (target.exists()) {
            return nf.LoadAnimation(model.getModelHandle(), target.getAbsolutePath());
        }

        logger.warn("Mapped animation file not found: {} -> {} (slot: {})", modelDir, mappedFile, animName);
        return 0;
    }

    public static void invalidateAnimCache(IMMDModel model) {
        Map<String, Long> sub = animModel.get(model);
        if (sub != null) {
            for (Long handle : sub.values()) {
                nf.DeleteAnimation(handle);
            }
            sub.clear();
        }
    }

    static String GetAnimationFilename(String dir, String animName) {
        File animFilename = new File(dir, animName + ".vmd");
        return animFilename.getAbsolutePath();
    }

    public static String getDefaultAnimDir() {
        return defaultAnimDir;
    }

    public static String getCustomAnimDir() {
        return customAnimDir;
    }
}
