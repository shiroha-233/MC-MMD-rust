package com.shiroha.mmdskin.config;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Path;

/**
 * 路径常量类
 * 集中管理所有文件路径，避免硬编码
 */
public final class PathConstants {
    
    // ==================== 根目录 ====================
    /** 3D皮肤根目录名称 */
    public static final String SKIN_ROOT_DIR = "3d-skin";
    
    /** 配置根目录名称 */
    public static final String CONFIG_ROOT_DIR = "config/mmdskin";
    
    // ==================== 模型目录 ====================
    /** 玩家模型目录名称 */
    public static final String ENTITY_PLAYER_DIR = "EntityPlayer";
    
    // ==================== 动画目录 ====================
    /** 默认动画目录名称 */
    public static final String DEFAULT_ANIM_DIR = "DefaultAnim";
    
    /** 自定义动画目录名称 */
    public static final String CUSTOM_ANIM_DIR = "CustomAnim";
    
    /** 舞台动画目录名称（存放相机 VMD 文件） */
    public static final String STAGE_ANIM_DIR = "StageAnim";
    
    // ==================== 表情目录 ====================
    /** 默认表情目录名称 */
    public static final String DEFAULT_MORPH_DIR = "DefaultMorph";
    
    /** 自定义表情目录名称 */
    public static final String CUSTOM_MORPH_DIR = "CustomMorph";
    
    // ==================== 着色器目录 ====================
    /** 着色器目录名称 */
    public static final String SHADER_DIR = "shader";
    
    // ==================== 配置文件名 ====================
    /** 动作轮盘配置文件 */
    public static final String ACTION_WHEEL_CONFIG = "action_wheel.json";
    
    /** 模型选择配置文件 */
    public static final String MODEL_SELECTOR_CONFIG = "model_selector.json";
    
    /** 表情轮盘配置文件 */
    public static final String MORPH_WHEEL_CONFIG = "morph_wheel.json";
    
    /** 主配置文件 */
    public static final String MAIN_CONFIG = "config.json";
    
    /** 舞台模式配置文件 */
    public static final String STAGE_CONFIG = "stage_config.json";
    
    // ==================== 文件扩展名 ====================
    /** VMD 动画文件扩展名 */
    public static final String VMD_EXTENSION = ".vmd";
    
    /** VPD 表情文件扩展名 */
    public static final String VPD_EXTENSION = ".vpd";
    
    /** PMX 模型文件扩展名 */
    public static final String PMX_EXTENSION = ".pmx";
    
    /** PMD 模型文件扩展名 */
    public static final String PMD_EXTENSION = ".pmd";
    
    /** 支持的音频文件扩展名 */
    public static final String[] AUDIO_EXTENSIONS = {".mp3", ".ogg", ".wav"};
    
    // ==================== 下载链接 ====================
    /** 资源包下载地址 */
    public static final String RESOURCE_DOWNLOAD_URL = 
        "https://github.com/Gengorou-C/3d-skin-C/releases/download/requiredFiles/3d-skin.zip";
    
    /** 资源包文件名 */
    public static final String RESOURCE_ZIP_NAME = "3d-skin.zip";
    
    // ==================== 路径获取方法 ====================
    
    private PathConstants() {
        // 工具类，禁止实例化
    }
    
    /**
     * 获取游戏目录
     */
    public static String getGameDirectory() {
        return Minecraft.getInstance().gameDirectory.getAbsolutePath();
    }
    
    /**
     * 获取3D皮肤根目录
     */
    public static File getSkinRootDir() {
        return new File(getGameDirectory(), SKIN_ROOT_DIR);
    }
    
    /**
     * 获取3D皮肤根目录路径字符串
     */
    public static String getSkinRootPath() {
        return getSkinRootDir().getAbsolutePath();
    }
    
    /**
     * 获取配置根目录
     */
    public static File getConfigRootDir() {
        return new File(getGameDirectory(), CONFIG_ROOT_DIR);
    }
    
    /**
     * 获取配置根目录 Path
     */
    public static Path getConfigRootPath() {
        return getConfigRootDir().toPath();
    }
    
    /**
     * 获取玩家模型目录
     */
    public static File getEntityPlayerDir() {
        return new File(getSkinRootDir(), ENTITY_PLAYER_DIR);
    }
    
    /**
     * 获取默认动画目录
     */
    public static File getDefaultAnimDir() {
        return new File(getSkinRootDir(), DEFAULT_ANIM_DIR);
    }
    
    /**
     * 获取自定义动画目录
     */
    public static File getCustomAnimDir() {
        return new File(getSkinRootDir(), CUSTOM_ANIM_DIR);
    }
    
    /**
     * 获取舞台动画目录
     */
    public static File getStageAnimDir() {
        return new File(getSkinRootDir(), STAGE_ANIM_DIR);
    }
    
    /**
     * 获取默认表情目录
     */
    public static File getDefaultMorphDir() {
        return new File(getSkinRootDir(), DEFAULT_MORPH_DIR);
    }
    
    /**
     * 获取自定义表情目录
     */
    public static File getCustomMorphDir() {
        return new File(getSkinRootDir(), CUSTOM_MORPH_DIR);
    }
    
    /**
     * 获取着色器目录
     */
    public static File getShaderDir() {
        return new File(getSkinRootDir(), SHADER_DIR);
    }
    
    /**
     * 获取指定模型的目录
     */
    public static File getModelDir(String modelName) {
        return new File(getEntityPlayerDir(), modelName);
    }
    
    /**
     * 获取配置文件路径
     */
    public static File getConfigFile(String configFileName) {
        return new File(getConfigRootDir(), configFileName);
    }
    
    /**
     * 获取动作轮盘配置文件
     */
    public static File getActionWheelConfigFile() {
        return getConfigFile(ACTION_WHEEL_CONFIG);
    }
    
    /**
     * 获取模型选择配置文件
     */
    public static File getModelSelectorConfigFile() {
        return getConfigFile(MODEL_SELECTOR_CONFIG);
    }
    
    /**
     * 获取表情轮盘配置文件
     */
    public static File getMorphWheelConfigFile() {
        return getConfigFile(MORPH_WHEEL_CONFIG);
    }
    
    /**
     * 构建模型专属动画/表情路径
     * @param modelName 模型名称
     * @param fileName 文件名（含扩展名）
     */
    public static String getModelAssetPath(String modelName, String fileName) {
        return new File(getModelDir(modelName), fileName).getAbsolutePath();
    }
    
    /**
     * 构建默认动画路径
     */
    public static String getDefaultAnimPath(String animName) {
        return new File(getDefaultAnimDir(), animName + VMD_EXTENSION).getAbsolutePath();
    }
    
    /**
     * 构建自定义动画路径
     */
    public static String getCustomAnimPath(String animName) {
        return new File(getCustomAnimDir(), animName + VMD_EXTENSION).getAbsolutePath();
    }
    
    /**
     * 构建默认表情路径
     */
    public static String getDefaultMorphPath(String morphName) {
        return new File(getDefaultMorphDir(), morphName + VPD_EXTENSION).getAbsolutePath();
    }
    
    /**
     * 构建自定义表情路径
     */
    public static String getCustomMorphPath(String morphName) {
        return new File(getCustomMorphDir(), morphName + VPD_EXTENSION).getAbsolutePath();
    }
    
    /**
     * 构建模型专属表情路径
     */
    public static String getModelMorphPath(String modelName, String morphName) {
        return new File(getModelDir(modelName), morphName + VPD_EXTENSION).getAbsolutePath();
    }
    
    /**
     * 确保目录存在
     */
    public static boolean ensureDirectoryExists(File dir) {
        if (!dir.exists()) {
            return dir.mkdirs();
        }
        return true;
    }
    
    /**
     * 确保 StageAnim 目录存在，首次创建时放置说明文件
     */
    public static void ensureStageAnimDir() {
        File stageDir = getStageAnimDir();
        if (!stageDir.exists()) {
            if (stageDir.mkdirs()) {
                // 首次创建，写入说明文件
                try {
                    File readme = new File(stageDir, "_readme.txt");
                    java.nio.file.Files.writeString(readme.toPath(),
                        "=== StageAnim 舞台动画目录 ===\n\n" +
                        "请将舞台动画 VMD 文件按子文件夹组织：\n\n" +
                        "StageAnim/\n" +
                        "  千本桜/\n" +
                        "    motion.vmd      (动作VMD)\n" +
                        "    camera.vmd      (相机VMD)\n" +
                        "    face.vmd        (表情VMD)\n" +
                        "  极乐净土/\n" +
                        "    ...\n\n" +
                        "每个子文件夹视为一个「舞台包」，其中的所有非相机VMD会自动合并播放。\n" +
                        "含相机数据的VMD会自动识别为相机文件。\n\n" +
                        "音频支持：将 .mp3 / .ogg / .wav 文件放在同一子文件夹中，播放时自动随动作同步播放。\n"
                    );
                } catch (Exception ignored) {}
            }
        }
    }
}
