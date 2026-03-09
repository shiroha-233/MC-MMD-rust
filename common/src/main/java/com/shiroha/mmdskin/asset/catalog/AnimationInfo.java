package com.shiroha.mmdskin.asset.catalog;
import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 动画信息类（VMD / FBX 文件）
 */

public class AnimationInfo extends AbstractAssetInfo<AnimationInfo.AnimSource> {
    private static final Logger logger = LogManager.getLogger();
    private static final String EXTENSION = ".vmd";
    private static final String FBX_EXTENSION = ".fbx";

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

    public String getAnimName() { return getName(); }

    @Override
    protected String getSourceDisplayName(AnimSource source) {
        return source.getDisplayName();
    }

    private static final AssetFactory<AnimationInfo, AnimSource> FACTORY =
            (name, path, fName, src, model, size) ->
                    new AnimationInfo(name, formatDisplayName(name), path, fName, src, model, size);

    public static List<AnimationInfo> scanAllAnimations() {
        List<AnimationInfo> list = new ArrayList<>();
        for (String ext : new String[]{EXTENSION, FBX_EXTENSION}) {
            list.addAll(scanDirectory(PathConstants.getDefaultAnimDir(), ext, AnimSource.DEFAULT, null, FACTORY));
            list.addAll(scanDirectory(PathConstants.getCustomAnimDir(), ext, AnimSource.CUSTOM, null, FACTORY));
        }

        File entityPlayerDir = PathConstants.getEntityPlayerDir();
        if (entityPlayerDir.exists() && entityPlayerDir.isDirectory()) {
            File[] modelDirs = entityPlayerDir.listFiles(File::isDirectory);
            if (modelDirs != null) {
                for (File modelDir : modelDirs) {
                    File animsSubDir = new File(modelDir, PathConstants.MODEL_ANIMS_DIR);
                    for (String ext : new String[]{EXTENSION, FBX_EXTENSION}) {
                        list.addAll(scanDirectory(animsSubDir, ext, AnimSource.MODEL, modelDir.getName(), FACTORY));
                        list.addAll(scanDirectory(modelDir, ext, AnimSource.MODEL, modelDir.getName(), FACTORY));
                    }
                }
            }
        }

        list = new ArrayList<>(new LinkedHashSet<>(list));
        sortBySourceAndName(list);
        logger.info("共扫描到 {} 个动画文件", list.size());
        return list;
    }

    public static List<AnimationInfo> scanCustomAnimations() {
        List<AnimationInfo> list = new ArrayList<>();
        list.addAll(scanDirectory(PathConstants.getCustomAnimDir(), EXTENSION, AnimSource.CUSTOM, null, FACTORY));
        list.addAll(scanDirectory(PathConstants.getCustomAnimDir(), FBX_EXTENSION, AnimSource.CUSTOM, null, FACTORY));
        list.sort(Comparator.comparing(AnimationInfo::getAnimName, String.CASE_INSENSITIVE_ORDER));
        logger.info("共扫描到 {} 个自定义动画", list.size());
        return list;
    }

    public static List<AnimationInfo> scanAnimationsForModel(String modelName) {
        List<AnimationInfo> list = new ArrayList<>();
        if (modelName != null && !modelName.isEmpty()) {
            for (String ext : new String[]{EXTENSION, FBX_EXTENSION}) {
                list.addAll(scanDirectory(PathConstants.getModelAnimsDir(modelName), ext, AnimSource.MODEL, modelName, FACTORY));
                list.addAll(scanDirectory(PathConstants.getModelDir(modelName), ext, AnimSource.MODEL, modelName, FACTORY));
            }
        }
        for (String ext : new String[]{EXTENSION, FBX_EXTENSION}) {
            list.addAll(scanDirectory(PathConstants.getCustomAnimDir(), ext, AnimSource.CUSTOM, null, FACTORY));
            list.addAll(scanDirectory(PathConstants.getDefaultAnimDir(), ext, AnimSource.DEFAULT, null, FACTORY));
        }

        list = new ArrayList<>(new LinkedHashSet<>(list));
        sortBySourceAndName(list);
        return list;
    }

    public static AnimationInfo findByAnimName(String animName) {
        for (AnimationInfo info : scanAllAnimations()) {
            if (info.getAnimName().equals(animName)) return info;
        }
        return null;
    }

    public static AnimationInfo findByCatalogKey(String catalogKey) {
        for (AnimationInfo info : scanAllAnimations()) {
            if (info.matchesCatalogKey(catalogKey)) {
                return info;
            }
        }
        return null;
    }

    private static String formatDisplayName(String animName) {
        String name = animName.replace("itemActive_", "").replace("minecraft.", "");
        if (!name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }
}
