package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ModelAnimConfig;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MMDAnimManager {
    public static final Logger logger = LogManager.getLogger();
    static NativeFunc nf;
    static Map<IMMDModel, Map<String, Long>> animModel;
    
    static String defaultAnimDir;
    static String customAnimDir;
    
    // 已警告过的动画名称（避免重复刷屏）
    static Set<String> warnedAnimations;

    public static void Init() {
        nf = NativeFunc.GetInst();
        animModel = new ConcurrentHashMap<>(); // 线程安全
        warnedAnimations = ConcurrentHashMap.newKeySet(); // 线程安全
        
        // 初始化目录路径
        defaultAnimDir = PathConstants.getDefaultAnimDir().getAbsolutePath();
        customAnimDir = PathConstants.getCustomAnimDir().getAbsolutePath();
        
        // 确保目录存在
        ensureDirectoriesExist();
    }

    private static void ensureDirectoriesExist() {
        File defaultDir = PathConstants.getDefaultAnimDir();
        File customDir = PathConstants.getCustomAnimDir();
        
        if (PathConstants.ensureDirectoryExists(defaultDir)) {
        }
        
        if (PathConstants.ensureDirectoryExists(customDir)) {
        }
    }

    public static void AddModel(IMMDModel model) {
        animModel.put(model, new ConcurrentHashMap<>()); // 线程安全
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
        FbxDefaultAnimPack.onModelRemoved(model);
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
        String loadedFrom = null;
        
        if (anim == 0 && modelDir != null) {
            String mappedFile = ModelAnimConfig.getMappedFile(modelDir, animName);
            if (mappedFile != null) {
                anim = tryLoadAnimation(model, modelDir, mappedFile, animName);
                if (anim != 0) {
                    loadedFrom = "映射配置";
                }
            }
        }
        
        if (anim == 0 && modelDir != null) {
            String animsDir = PathConstants.getModelAnimsDirByPath(modelDir).getAbsolutePath();
            anim = tryLoadFromDir(model, animsDir, animName);
            if (anim != 0) {
                loadedFrom = "模型 anims/";
            }
        }
        
        if (anim == 0 && modelDir != null) {
            anim = tryLoadFromDir(model, modelDir, animName);
            if (anim != 0) {
                loadedFrom = "模型根目录";
            }
        }
        
        if (anim == 0) {
            anim = tryLoadFromDir(model, customAnimDir, animName);
            if (anim != 0) {
                loadedFrom = "自定义目录";
            }
        }
        
        if (anim == 0) {
            anim = FbxDefaultAnimPack.loadAnimation(model, animName);
            if (anim != 0) {
                loadedFrom = "FBX 默认包";
            }
        }
        
        if (anim == 0) {
            anim = tryLoadFromDir(model, defaultAnimDir, animName);
            if (anim != 0) {
                loadedFrom = "默认目录";
            }
        }
        
        sub.put(animName, anim);
        if (anim == 0 && warnedAnimations.add(animName)) {
            logger.warn("未找到动画文件: {}", animName);
        }
        
        return anim;
    }
    
    private static final String[] ANIM_EXTENSIONS = {".vmd", ".fbx"};

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
                    long handle = nf.LoadAnimation(
                        model.getModelHandle(),
                        fbx.getAbsolutePath() + "#" + animName
                    );
                    if (handle != 0) return handle;
                }
            }
        }
        return 0;
    }
    
    private static long tryLoadAnimation(IMMDModel model, String modelDir, 
                                          String mappedFile, String animName) {
        if (mappedFile.contains("..") || mappedFile.contains("/") || mappedFile.contains("\\")) {
            logger.warn("动画映射文件名包含非法字符，已忽略: {} (槽位: {})", mappedFile, animName);
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
        
        logger.warn("映射配置的动画文件不存在: {} -> {} (槽位: {})", modelDir, mappedFile, animName);
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
