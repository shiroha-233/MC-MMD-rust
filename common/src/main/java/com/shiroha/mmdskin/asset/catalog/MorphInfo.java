package com.shiroha.mmdskin.asset.catalog;
import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 表情预设信息类（VPD 文件）
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

    public String getMorphName() { return getName(); }

    @Override
    protected String getSourceDisplayName(MorphSource source) {
        return source.getDisplayName();
    }

    private static final AssetFactory<MorphInfo, MorphSource> FACTORY =
            (name, path, fName, src, model, size) ->
                    new MorphInfo(name, formatDisplayName(name), path, fName, src, model, size);

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

    public static List<MorphInfo> scanCustomMorphs() {
        File customDir = PathConstants.getCustomMorphDir();
        PathConstants.ensureDirectoryExists(customDir);
        List<MorphInfo> list = new ArrayList<>(
                scanDirectory(customDir, EXTENSION, MorphSource.CUSTOM, null, FACTORY));
        list.sort(Comparator.comparing(MorphInfo::getMorphName, String.CASE_INSENSITIVE_ORDER));
        logger.info("共扫描到 {} 个自定义表情", list.size());
        return list;
    }

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

    public static MorphInfo findByMorphName(String morphName) {
        for (MorphInfo info : scanAllMorphs()) {
            if (info.getMorphName().equals(morphName)) return info;
        }
        return null;
    }

    public static MorphInfo findByCatalogKey(String catalogKey) {
        for (MorphInfo info : scanAllMorphs()) {
            if (info.matchesCatalogKey(catalogKey)) {
                return info;
            }
        }
        return null;
    }

    public static String getDefaultMorphDirPath() {
        return PathConstants.getDefaultMorphDir().getAbsolutePath();
    }

    public static String getCustomMorphDirPath() {
        return PathConstants.getCustomMorphDir().getAbsolutePath();
    }

    private static String formatDisplayName(String morphName) {
        String name = morphName.replace("morph_", "").replace("face_", "").replace("expression_", "");
        if (!name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }
}
