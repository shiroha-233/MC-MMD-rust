package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MMD 动画管理器
 * 负责加载和管理 VMD 动画文件
 * 
 * 文件组织结构：
 * - 3d-skin/DefaultAnim/  - 通用默认动作（系统预设）
 * - 3d-skin/CustomAnim/   - 自定义动作（用户添加）
 * - 3d-skin/EntityPlayer/模型名/  - 模型专属动作
 * 
 * 加载优先级：模型目录 > CustomAnim > DefaultAnim
 * 线程安全：使用 ConcurrentHashMap 保证多线程访问安全
 */
public class MMDAnimManager {
    public static final Logger logger = LogManager.getLogger();
    static NativeFunc nf;
    static Map<String, Long> animStatic; // 线程安全
    static Map<IMMDModel, Map<String, Long>> animModel; // 线程安全
    
    // 动画文件目录（延迟初始化）
    static String defaultAnimDir;
    static String customAnimDir;
    
    // 已警告过的动画名称（避免重复刷屏）
    static Set<String> warnedAnimations;

    public static void Init() {
        nf = NativeFunc.GetInst();
        animStatic = new ConcurrentHashMap<>(); // 线程安全
        animModel = new ConcurrentHashMap<>(); // 线程安全
        warnedAnimations = ConcurrentHashMap.newKeySet(); // 线程安全
        
        // 初始化目录路径
        defaultAnimDir = PathConstants.getDefaultAnimDir().getAbsolutePath();
        customAnimDir = PathConstants.getCustomAnimDir().getAbsolutePath();
        
        // 确保目录存在
        ensureDirectoriesExist();
        
        logger.info("MMDAnimManager 初始化完成 (使用 ConcurrentHashMap)");
        logger.info("默认动画目录: " + defaultAnimDir);
        logger.info("自定义动画目录: " + customAnimDir);
    }

    /**
     * 确保动画目录存在
     */
    private static void ensureDirectoriesExist() {
        File defaultDir = PathConstants.getDefaultAnimDir();
        File customDir = PathConstants.getCustomAnimDir();
        
        if (PathConstants.ensureDirectoryExists(defaultDir)) {
            logger.info("创建默认动画目录: " + defaultAnimDir);
        }
        
        if (PathConstants.ensureDirectoryExists(customDir)) {
            logger.info("创建自定义动画目录: " + customAnimDir);
        }
    }

    public static void AddModel(IMMDModel model) {
        animModel.put(model, new ConcurrentHashMap<>()); // 线程安全
    }

    public static void DeleteModel(IMMDModel model) {
        Map<String, Long> sub = animModel.get(model);
        if (sub != null) {
            for (Long i : sub.values()) {
                nf.DeleteAnimation(i);
            }
        }
        animModel.remove(model);
    }

    /**
     * 获取模型动画
     * 加载优先级：模型目录 > CustomAnim > DefaultAnim
     */
    public static long GetAnimModel(IMMDModel model, String animName) {
        // 尝试从缓存获取
        Map<String, Long> sub = animModel.get(model);
        if (sub == null) return 0;
        String cacheKey = animName;
        Long cached = sub.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // 按优先级尝试加载动画
        long anim = 0;
        String loadedFrom = null;
        
        // 1. 优先从模型目录加载
        String modelDirFile = GetAnimationFilename(model.GetModelDir(), animName);
        if (new File(modelDirFile).exists()) {
            anim = nf.LoadAnimation(model.GetModelLong(), modelDirFile);
            if (anim != 0) {
                loadedFrom = "模型目录";
            }
        }
        
        // 2. 从自定义动画目录加载
        if (anim == 0) {
            String customFile = GetAnimationFilename(customAnimDir, animName);
            if (new File(customFile).exists()) {
                anim = nf.LoadAnimation(model.GetModelLong(), customFile);
                if (anim != 0) {
                    loadedFrom = "自定义目录";
                }
            }
        }
        
        // 3. 从默认动画目录加载
        if (anim == 0) {
            String defaultFile = GetAnimationFilename(defaultAnimDir, animName);
            if (new File(defaultFile).exists()) {
                anim = nf.LoadAnimation(model.GetModelLong(), defaultFile);
                if (anim != 0) {
                    loadedFrom = "默认目录";
                }
            }
        }
        
        // 记录加载结果
        if (anim != 0) {
            sub.put(cacheKey, anim);
            logger.info("加载动画 '{}' 成功，来源: {}", animName, loadedFrom);
        } else {
            if (warnedAnimations.add(animName)) {
                logger.warn("未找到动画文件: {}", animName);
            }
        }
        
        return anim;
    }

    /**
     * 构建动画文件路径
     */
    static String GetAnimationFilename(String dir, String animName) {
        File animFilename = new File(dir, animName + ".vmd");
        return animFilename.getAbsolutePath();
    }
    
    /**
     * 获取默认动画目录
     */
    public static String getDefaultAnimDir() {
        return defaultAnimDir;
    }
    
    /**
     * 获取自定义动画目录
     */
    public static String getCustomAnimDir() {
        return customAnimDir;
    }
}
