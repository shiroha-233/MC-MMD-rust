package com.shiroha.mmdskin.asset.catalog;

import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型信息类
 */

public class ModelInfo {
    private static final Logger logger = LogManager.getLogger();

    private record CacheSnapshot(List<ModelInfo> models, long timestamp) {}
    private static volatile CacheSnapshot cache = null;
    private static final long CACHE_TTL = 5000;

    private final String folderName;
    private final String folderPath;
    private final String modelFilePath;
    private final String modelFileName;
    private final boolean isPMD;
    private final boolean isVRM;
    private final long fileSize;

    public ModelInfo(String folderName, String folderPath, String modelFilePath, String modelFileName, boolean isPMD, boolean isVRM, long fileSize) {
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.modelFilePath = modelFilePath;
        this.modelFileName = modelFileName;
        this.isPMD = isPMD;
        this.isVRM = isVRM;
        this.fileSize = fileSize;
    }

    public String getFolderName() { return folderName; }
    public String getDisplayName() { return folderName; }
    public String getFolderPath() { return folderPath; }
    public String getModelFilePath() { return modelFilePath; }
    public String getModelFileName() { return modelFileName; }
    public boolean isPMD() { return isPMD; }
    public boolean isVRM() { return isVRM; }
    public long getFileSize() { return fileSize; }
    public String getCatalogKey() { return folderName; }

    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    public String getFormatDescription() {
        if (isVRM) return "VRM";
        return isPMD ? "PMD" : "PMX";
    }

    public static List<ModelInfo> scanModels() {

        CacheSnapshot snapshot = cache;
        long now = System.currentTimeMillis();
        if (snapshot != null && (now - snapshot.timestamp()) < CACHE_TTL) {
            return snapshot.models();
        }

        List<ModelInfo> models = new ArrayList<>();
        File entityPlayerDir = PathConstants.getEntityPlayerDir();

        if (!entityPlayerDir.exists() || !entityPlayerDir.isDirectory()) {
            logger.warn("EntityPlayer 目录不存在: " + entityPlayerDir.getAbsolutePath());
            return models;
        }

        File[] modelDirs = entityPlayerDir.listFiles(File::isDirectory);
        if (modelDirs == null) {
            return models;
        }

        for (File modelDir : modelDirs) {
            ModelInfo info = scanModelFolder(modelDir);
            if (info != null) {
                models.add(info);
                logger.debug("发现模型: {} -> {}", info.getFolderName(), info.getModelFileName());
            }
        }

        models.sort((a, b) -> a.getFolderName().compareToIgnoreCase(b.getFolderName()));

        cache = new CacheSnapshot(models, System.currentTimeMillis());

        return models;
    }

    public static void invalidateCache() {
        cache = null;
    }

    private static ModelInfo scanModelFolder(File modelDir) {
        FileFilter pmxFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmx");
        FileFilter pmdFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmd");
        FileFilter vrmFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".vrm");

        File[] pmxFiles = modelDir.listFiles(pmxFilter);
        if (pmxFiles != null && pmxFiles.length > 0) {
            File selectedFile = findPreferredModel(pmxFiles);
            return new ModelInfo(
                modelDir.getName(), modelDir.getAbsolutePath(),
                selectedFile.getAbsolutePath(), selectedFile.getName(),
                false, false, selectedFile.length()
            );
        }

        File[] pmdFiles = modelDir.listFiles(pmdFilter);
        if (pmdFiles != null && pmdFiles.length > 0) {
            File selectedFile = findPreferredModel(pmdFiles);
            return new ModelInfo(
                modelDir.getName(), modelDir.getAbsolutePath(),
                selectedFile.getAbsolutePath(), selectedFile.getName(),
                true, false, selectedFile.length()
            );
        }

        File[] vrmFiles = modelDir.listFiles(vrmFilter);
        if (vrmFiles != null && vrmFiles.length > 0) {
            File selectedFile = findPreferredModel(vrmFiles);
            return new ModelInfo(
                modelDir.getName(), modelDir.getAbsolutePath(),
                selectedFile.getAbsolutePath(), selectedFile.getName(),
                false, true, selectedFile.length()
            );
        }

        return null;
    }

    private static File findPreferredModel(File[] files) {
        if (files.length == 1) {
            return files[0];
        }

        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.equals("model.pmx") || name.equals("model.pmd")) {
                return file;
            }
        }

        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files[0];
    }

    public static ModelInfo findByFolderName(String folderName) {
        List<ModelInfo> models = scanModels();
        for (ModelInfo info : models) {
            if (info.getFolderName().equals(folderName)) {
                return info;
            }
        }
        return null;
    }
}
