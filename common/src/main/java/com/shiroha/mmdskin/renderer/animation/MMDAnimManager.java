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

/**
 * MMD 动画管理器
 * 负责加载和管理 VMD 动画文件
 * 
 * 文件组织结构：
 * - 3d-skin/DefaultAnim/           - 通用默认动作（系统预设）
 * - 3d-skin/CustomAnim/            - 自定义动作（用户添加）
 * - 3d-skin/EntityPlayer/模型名/    - 模型根目录动作（向后兼容）
 * - 3d-skin/EntityPlayer/模型名/anims/ - 模型专属动作子文件夹
 * - 3d-skin/EntityPlayer/模型名/animations.json - 动画槽位映射配置
 * 
 * 加载优先级（从高到低）：
 * 1. animations.json 映射（用户显式配置）
 * 2. anims/ 子文件夹同名匹配
 * 3. 模型根目录同名匹配（向后兼容）
 * 4. CustomAnim 目录
 * 5. DefaultAnim 目录
 * 
 * 线程安全：使用 ConcurrentHashMap 保证多线程访问安全
 */
public class MMDAnimManager {
    public static final Logger logger = LogManager.getLogger();
    static NativeFunc nf;
    static Map<IMMDModel, Map<String, Long>> animModel; // 线程安全
    
    // 动画文件目录（延迟初始化）
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

    /**
     * 确保动画目录存在
     */
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
    }

    /**
     * 获取模型动画
     * 加载优先级：animations.json > anims/ > 模型根目录 > CustomAnim > DefaultAnim
     */
    public static long GetAnimModel(IMMDModel model, String animName) {
        // 尝试从缓存获取
        Map<String, Long> sub = animModel.get(model);
        if (sub == null) return 0;
        Long cached = sub.get(animName);
        if (cached != null) {
            return cached;
        }
        
        String modelDir = model.getModelDir();
        long anim = 0;
        String loadedFrom = null;
        
        // 1. 最高优先：animations.json 显式映射
        if (anim == 0 && modelDir != null) {
            String mappedFile = ModelAnimConfig.getMappedFile(modelDir, animName);
            if (mappedFile != null) {
                anim = tryLoadAnimation(model, modelDir, mappedFile, animName);
                if (anim != 0) {
                    loadedFrom = "映射配置";
                }
            }
        }
        
        // 2. 模型 anims/ 子文件夹同名匹配
        if (anim == 0 && modelDir != null) {
            String animsDir = PathConstants.getModelAnimsDirByPath(modelDir).getAbsolutePath();
            anim = tryLoadFromDir(model, animsDir, animName);
            if (anim != 0) {
                loadedFrom = "模型 anims/";
            }
        }
        
        // 3. 模型根目录同名匹配（向后兼容）
        if (anim == 0 && modelDir != null) {
            anim = tryLoadFromDir(model, modelDir, animName);
            if (anim != 0) {
                loadedFrom = "模型根目录";
            }
        }
        
        // 4. 自定义动画目录
        if (anim == 0) {
            anim = tryLoadFromDir(model, customAnimDir, animName);
            if (anim != 0) {
                loadedFrom = "自定义目录";
            }
        }
        
        // 5. 默认动画目录
        if (anim == 0) {
            anim = tryLoadFromDir(model, defaultAnimDir, animName);
            if (anim != 0) {
                loadedFrom = "默认目录";
            }
        }
        
        // 记录加载结果
        if (anim != 0) {
            sub.put(animName, anim);
        } else {
            if (warnedAnimations.add(animName)) {
                logger.warn("未找到动画文件: {}", animName);
            }
        }
        
        return anim;
    }
    
    /**
     * 从指定目录尝试加载同名动画
     */
    private static final String[] ANIM_EXTENSIONS = {".vmd", ".fbx"};

    private static long tryLoadFromDir(IMMDModel model, String dir, String animName) {
        // 1. 精确文件名匹配（animName.vmd / animName.fbx）
        for (String ext : ANIM_EXTENSIONS) {
            File file = new File(dir, animName + ext);
            if (file.exists()) {
                return nf.LoadAnimation(model.getModelHandle(), file.getAbsolutePath());
            }
        }
        // 2. 多 Stack FBX 回退：扫描目录中的 .fbx 文件，尝试 file.fbx#animName
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
    
    /**
     * 根据映射配置加载动画
     * mappedFile 仅允许纯文件名（禁止路径分隔符，防止穿越）
     */
    private static long tryLoadAnimation(IMMDModel model, String modelDir, 
                                          String mappedFile, String animName) {
        // 安全检查：防止路径穿越
        if (mappedFile.contains("..") || mappedFile.contains("/") || mappedFile.contains("\\")) {
            logger.warn("动画映射文件名包含非法字符，已忽略: {} (槽位: {})", mappedFile, animName);
            return 0;
        }
        
        // 优先在 anims/ 子文件夹中查找
        File animsDir = PathConstants.getModelAnimsDirByPath(modelDir);
        File target = new File(animsDir, mappedFile);
        if (target.exists()) {
            return nf.LoadAnimation(model.getModelHandle(), target.getAbsolutePath());
        }
        
        // 回退：在模型根目录中查找
        target = new File(modelDir, mappedFile);
        if (target.exists()) {
            return nf.LoadAnimation(model.getModelHandle(), target.getAbsolutePath());
        }
        
        logger.warn("映射配置的动画文件不存在: {} -> {} (槽位: {})", modelDir, mappedFile, animName);
        return 0;
    }
    
    /**
     * 清除指定模型的动画缓存（动画映射变更时调用）
     * 下次 GetAnimModel 会重新按优先级加载
     */
    public static void invalidateAnimCache(IMMDModel model) {
        Map<String, Long> sub = animModel.get(model);
        if (sub != null) {
            for (Long handle : sub.values()) {
                nf.DeleteAnimation(handle);
            }
            sub.clear();
        }
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
