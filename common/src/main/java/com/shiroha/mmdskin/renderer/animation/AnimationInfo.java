package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 动画信息类（VMD 文件）
 * 继承 {@link AbstractAssetInfo}，差异仅为扩展名和前缀清理规则。
 */
public class AnimationInfo extends AbstractAssetInfo<AnimationInfo.AnimSource> {
    private static final Logger logger = LogManager.getLogger();
    private static final String EXTENSION = ".vmd";

    public enum AnimSource {
        DEFAULT("默认动画"),
        CUSTOM("自定义动画"),
        MODEL("模型专属");

        private final String displayName;
        AnimSource(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public AnimationInfo(String animName, String displayName, String filePath,
                         String fileName, AnimSource source, String modelName, long fileSize) {
        super(animName, displayName, filePath, fileName, source, modelName, fileSize);
    }

    /** 兼容旧 API */
    public String getAnimName() { return getName(); }

    @Override
    protected String getSourceDisplayName(AnimSource source) {
        return source.getDisplayName();
    }

    // ==================== 工厂 ====================

    private static final AssetFactory<AnimationInfo, AnimSource> FACTORY =
            (name, path, fName, src, model, size) ->
                    new AnimationInfo(name, formatDisplayName(name), path, fName, src, model, size);

    // ==================== 扫描方法 ====================

    /** 扫描所有动画目录 */
    public static List<AnimationInfo> scanAllAnimations() {
        List<AnimationInfo> list = new ArrayList<>();
        list.addAll(scanDirectory(PathConstants.getDefaultAnimDir(), EXTENSION, AnimSource.DEFAULT, null, FACTORY));
        list.addAll(scanDirectory(PathConstants.getCustomAnimDir(), EXTENSION, AnimSource.CUSTOM, null, FACTORY));

        File entityPlayerDir = PathConstants.getEntityPlayerDir();
        if (entityPlayerDir.exists() && entityPlayerDir.isDirectory()) {
            File[] modelDirs = entityPlayerDir.listFiles(File::isDirectory);
            if (modelDirs != null) {
                for (File modelDir : modelDirs) {
                    list.addAll(scanDirectory(modelDir, EXTENSION, AnimSource.MODEL, modelDir.getName(), FACTORY));
                }
            }
        }
        sortBySourceAndName(list);
        logger.info("共扫描到 {} 个动画文件", list.size());
        return list;
    }

    /** 只扫描自定义动画目录（用于轮盘） */
    public static List<AnimationInfo> scanCustomAnimations() {
        List<AnimationInfo> list = new ArrayList<>(
                scanDirectory(PathConstants.getCustomAnimDir(), EXTENSION, AnimSource.CUSTOM, null, FACTORY));
        list.sort(Comparator.comparing(AnimationInfo::getAnimName, String.CASE_INSENSITIVE_ORDER));
        logger.info("共扫描到 {} 个自定义动画", list.size());
        return list;
    }

    /** 扫描指定模型的所有可用动画 */
    public static List<AnimationInfo> scanAnimationsForModel(String modelName) {
        List<AnimationInfo> list = new ArrayList<>();
        if (modelName != null && !modelName.isEmpty()) {
            list.addAll(scanDirectory(PathConstants.getModelDir(modelName), EXTENSION, AnimSource.MODEL, modelName, FACTORY));
        }
        list.addAll(scanDirectory(PathConstants.getCustomAnimDir(), EXTENSION, AnimSource.CUSTOM, null, FACTORY));
        list.addAll(scanDirectory(PathConstants.getDefaultAnimDir(), EXTENSION, AnimSource.DEFAULT, null, FACTORY));
        sortBySourceAndName(list);
        return list;
    }

    /** 根据动画名查找 */
    public static AnimationInfo findByAnimName(String animName) {
        for (AnimationInfo info : scanAllAnimations()) {
            if (info.getAnimName().equals(animName)) return info;
        }
        return null;
    }

    // ==================== 格式化 ====================

    private static String formatDisplayName(String animName) {
        String name = animName.replace("itemActive_", "").replace("minecraft.", "");
        if (!name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }
}
