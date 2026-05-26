package com.shiroha.mmdskin.config;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Path;

/**
 * 路径常量类，集中管理所有文件路径
 */

public final class PathConstants {

    public static final String SKIN_ROOT_DIR = "3d-skin";
    public static final String CONFIG_ROOT_DIR = "config/mmdskin";

    public static final String ENTITY_PLAYER_DIR = "EntityPlayer";
    public static final String SCENE_MODEL_DIR = "SceneModel";

    public static final String DEFAULT_ANIM_DIR = "DefaultAnim";
    public static final String CUSTOM_ANIM_DIR = "CustomAnim";
    public static final String STAGE_ANIM_DIR = "StageAnim";
    public static final String MODEL_ANIMS_DIR = "anims";
    public static final String MODEL_ANIM_CONFIG = "animations.json";

    public static final String DEFAULT_MORPH_DIR = "DefaultMorph";
    public static final String CUSTOM_MORPH_DIR = "CustomMorph";

    public static final String SHADER_DIR = "shader";

    public static final String ACTION_WHEEL_CONFIG = "action_wheel.json";
    public static final String MODEL_SELECTOR_CONFIG = "model_selector.json";
    public static final String MORPH_WHEEL_CONFIG = "morph_wheel.json";
    public static final String MAIN_CONFIG = "config.json";
    public static final String STAGE_CONFIG = "stage_config.json";

    public static final String VMD_EXTENSION = ".vmd";
    public static final String VPD_EXTENSION = ".vpd";
    public static final String PMX_EXTENSION = ".pmx";
    public static final String PMD_EXTENSION = ".pmd";
    public static final String[] AUDIO_EXTENSIONS = {".mp3", ".ogg", ".wav"};

    public static final String RESOURCE_DOWNLOAD_URL =
        "https://github.com/Gengorou-C/3d-skin-C/releases/download/requiredFiles/3d-skin.zip";
    public static final String RESOURCE_ZIP_NAME = "3d-skin.zip";

    private PathConstants() {}

    public static String getGameDirectory() {
        return Minecraft.getInstance().gameDirectory.getAbsolutePath();
    }

    public static File getSkinRootDir() {
        return new File(getGameDirectory(), SKIN_ROOT_DIR);
    }

    public static String getSkinRootPath() {
        return getSkinRootDir().getAbsolutePath();
    }

    public static File getConfigRootDir() {
        return new File(getGameDirectory(), CONFIG_ROOT_DIR);
    }

    public static Path getConfigRootPath() {
        return getConfigRootDir().toPath();
    }

    public static File getEntityPlayerDir() {
        return new File(getSkinRootDir(), ENTITY_PLAYER_DIR);
    }

    public static File getSceneModelDir() {
        return new File(getSkinRootDir(), SCENE_MODEL_DIR);
    }

    public static File getDefaultAnimDir() {
        return new File(getSkinRootDir(), DEFAULT_ANIM_DIR);
    }

    public static File getCustomAnimDir() {
        return new File(getSkinRootDir(), CUSTOM_ANIM_DIR);
    }

    public static File getStageAnimDir() {
        return new File(getSkinRootDir(), STAGE_ANIM_DIR);
    }

    public static File getDefaultMorphDir() {
        return new File(getSkinRootDir(), DEFAULT_MORPH_DIR);
    }

    public static File getCustomMorphDir() {
        return new File(getSkinRootDir(), CUSTOM_MORPH_DIR);
    }

    public static File getShaderDir() {
        return new File(getSkinRootDir(), SHADER_DIR);
    }

    public static File getModelDir(String modelName) {
        return new File(getEntityPlayerDir(), modelName);
    }

    public static File getConfigFile(String configFileName) {
        return new File(getConfigRootDir(), configFileName);
    }

    public static File getActionWheelConfigFile() {
        return getConfigFile(ACTION_WHEEL_CONFIG);
    }

    public static File getModelSelectorConfigFile() {
        return getConfigFile(MODEL_SELECTOR_CONFIG);
    }

    public static File getMorphWheelConfigFile() {
        return getConfigFile(MORPH_WHEEL_CONFIG);
    }

    public static File getModelAnimsDir(String modelName) {
        return new File(getModelDir(modelName), MODEL_ANIMS_DIR);
    }

    public static File getModelAnimsDirByPath(String modelDirPath) {
        return new File(modelDirPath, MODEL_ANIMS_DIR);
    }

    public static File getModelAnimConfigFile(String modelDirPath) {
        return new File(modelDirPath, MODEL_ANIM_CONFIG);
    }

    public static String getModelAssetPath(String modelName, String fileName) {
        return new File(getModelDir(modelName), fileName).getAbsolutePath();
    }

    public static String getDefaultAnimPath(String animName) {
        return new File(getDefaultAnimDir(), animName + VMD_EXTENSION).getAbsolutePath();
    }

    public static String getCustomAnimPath(String animName) {
        return new File(getCustomAnimDir(), animName + VMD_EXTENSION).getAbsolutePath();
    }

    public static String getDefaultMorphPath(String morphName) {
        return new File(getDefaultMorphDir(), morphName + VPD_EXTENSION).getAbsolutePath();
    }

    public static String getCustomMorphPath(String morphName) {
        return new File(getCustomMorphDir(), morphName + VPD_EXTENSION).getAbsolutePath();
    }

    public static String getModelMorphPath(String modelName, String morphName) {
        return new File(getModelDir(modelName), morphName + VPD_EXTENSION).getAbsolutePath();
    }

    public static boolean ensureDirectoryExists(File dir) {
        if (!dir.exists()) {
            return dir.mkdirs();
        }
        return false;
    }

    public static void ensureStageAnimDir() {
        File stageDir = getStageAnimDir();
        if (!stageDir.exists()) {
            stageDir.mkdirs();
        }
    }

    public static void ensureSceneModelDir() {
        File sceneDir = getSceneModelDir();
        if (!sceneDir.exists()) {
            sceneDir.mkdirs();
        }
    }
}
