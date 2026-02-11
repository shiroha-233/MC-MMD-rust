package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 表情预设信息类（VPD 文件）
 * 继承 {@link AbstractAssetInfo}，差异仅为扩展名和前缀清理规则。
 */
public class MorphInfo extends AbstractAssetInfo<MorphInfo.MorphSource> {
    private static final Logger logger = LogManager.getLogger();
    private static final String EXTENSION = ".vpd";

    public enum MorphSource {
        DEFAULT("默认表情"),
        CUSTOM("自定义表情"),
        MODEL("模型专属");

        private final String displayName;
        MorphSource(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public MorphInfo(String morphName, String displayName, String filePath,
                     String fileName, MorphSource source, String modelName, long fileSize) {
        super(morphName, displayName, filePath, fileName, source, modelName, fileSize);
    }

    /** 兼容旧 API */
    public String getMorphName() { return getName(); }

    @Override
    protected String getSourceDisplayName(MorphSource source) {
        return source.getDisplayName();
    }

    // ==================== 工厂 ====================

    private static final AssetFactory<MorphInfo, MorphSource> FACTORY =
            (name, path, fName, src, model, size) ->
                    new MorphInfo(name, formatDisplayName(name), path, fName, src, model, size);

    // ==================== 扫描方法 ====================

    /** 扫描所有表情目录 */
    public static List<MorphInfo> scanAllMorphs() {
        List<MorphInfo> list = new ArrayList<>();

        File defaultDir = PathConstants.getDefaultMorphDir();
        PathConstants.ensureDirectoryExists(defaultDir);
        list.addAll(scanDirectory(defaultDir, EXTENSION, MorphSource.DEFAULT, null, FACTORY));

        File customDir = PathConstants.getCustomMorphDir();
        PathConstants.ensureDirectoryExists(customDir);
        list.addAll(scanDirectory(customDir, EXTENSION, MorphSource.CUSTOM, null, FACTORY));

        File entityPlayerDir = PathConstants.getEntityPlayerDir();
        if (entityPlayerDir.exists() && entityPlayerDir.isDirectory()) {
            File[] modelDirs = entityPlayerDir.listFiles(File::isDirectory);
            if (modelDirs != null) {
                for (File modelDir : modelDirs) {
                    list.addAll(scanDirectory(modelDir, EXTENSION, MorphSource.MODEL, modelDir.getName(), FACTORY));
                }
            }
        }
        sortBySourceAndName(list);
        logger.info("共扫描到 {} 个表情文件", list.size());
        return list;
    }

    /** 只扫描自定义表情目录（用于轮盘） */
    public static List<MorphInfo> scanCustomMorphs() {
        File customDir = PathConstants.getCustomMorphDir();
        PathConstants.ensureDirectoryExists(customDir);
        List<MorphInfo> list = new ArrayList<>(
                scanDirectory(customDir, EXTENSION, MorphSource.CUSTOM, null, FACTORY));
        list.sort(Comparator.comparing(MorphInfo::getMorphName, String.CASE_INSENSITIVE_ORDER));
        logger.info("共扫描到 {} 个自定义表情", list.size());
        return list;
    }

    /** 扫描指定模型的所有可用表情 */
    public static List<MorphInfo> scanMorphsForModel(String modelName) {
        List<MorphInfo> list = new ArrayList<>();
        if (modelName != null && !modelName.isEmpty()) {
            list.addAll(scanDirectory(PathConstants.getModelDir(modelName), EXTENSION, MorphSource.MODEL, modelName, FACTORY));
        }
        list.addAll(scanDirectory(PathConstants.getCustomMorphDir(), EXTENSION, MorphSource.CUSTOM, null, FACTORY));
        list.addAll(scanDirectory(PathConstants.getDefaultMorphDir(), EXTENSION, MorphSource.DEFAULT, null, FACTORY));
        sortBySourceAndName(list);
        return list;
    }

    /** 根据表情名查找 */
    public static MorphInfo findByMorphName(String morphName) {
        for (MorphInfo info : scanAllMorphs()) {
            if (info.getMorphName().equals(morphName)) return info;
        }
        return null;
    }

    /** 获取默认表情目录路径 */
    public static String getDefaultMorphDirPath() {
        return PathConstants.getDefaultMorphDir().getAbsolutePath();
    }

    /** 获取自定义表情目录路径 */
    public static String getCustomMorphDirPath() {
        return PathConstants.getCustomMorphDir().getAbsolutePath();
    }

    // ==================== 格式化 ====================

    private static String formatDisplayName(String morphName) {
        String name = morphName.replace("morph_", "").replace("face_", "").replace("expression_", "");
        if (!name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }
}
