package com.shiroha.mmdskin.scene.client;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.config.PathConstants;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 场景资源目录扫描与短期缓存。
 */
public final class SceneModelCatalog {
    private static final long CACHE_TTL_MILLIS = 5000L;
    private static final SceneModelCatalog INSTANCE = new SceneModelCatalog();

    private volatile List<ModelInfo> cachedModels;
    private volatile long cacheTimestamp;

    private SceneModelCatalog() {
    }

    public static SceneModelCatalog getInstance() {
        return INSTANCE;
    }

    public List<ModelInfo> listModels() {
        long now = System.currentTimeMillis();
        List<ModelInfo> snapshot = cachedModels;
        if (snapshot != null && (now - cacheTimestamp) < CACHE_TTL_MILLIS) {
            return snapshot;
        }

        List<ModelInfo> loadedModels = scanSceneModelDirectory();
        List<ModelInfo> immutableModels = List.copyOf(loadedModels);
        cachedModels = immutableModels;
        cacheTimestamp = now;
        return immutableModels;
    }

    public Optional<ModelInfo> findByFolderName(String folderName) {
        return listModels().stream()
                .filter(info -> info.getFolderName().equals(folderName))
                .findFirst();
    }

    public void invalidate() {
        cachedModels = null;
        cacheTimestamp = 0L;
    }

    private List<ModelInfo> scanSceneModelDirectory() {
        PathConstants.ensureSceneModelDir();
        File sceneDir = PathConstants.getSceneModelDir();
        if (!sceneDir.exists() || !sceneDir.isDirectory()) {
            return List.of();
        }

        File[] sceneFolders = sceneDir.listFiles(File::isDirectory);
        if (sceneFolders == null || sceneFolders.length == 0) {
            return List.of();
        }

        List<ModelInfo> models = new ArrayList<>();
        for (File sceneFolder : sceneFolders) {
            ModelInfo modelInfo = scanFolder(sceneFolder);
            if (modelInfo != null) {
                models.add(modelInfo);
            }
        }

        models.sort((left, right) -> left.getFolderName().compareToIgnoreCase(right.getFolderName()));
        return models;
    }

    private ModelInfo scanFolder(File dir) {
        FileFilter pmxFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmx");
        FileFilter pmdFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmd");
        FileFilter vrmFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".vrm");

        File[] pmxFiles = dir.listFiles(pmxFilter);
        if (pmxFiles != null && pmxFiles.length > 0) {
            File selectedFile = pickPreferredModelFile(pmxFiles);
            return buildModelInfo(dir, selectedFile, false, false);
        }

        File[] pmdFiles = dir.listFiles(pmdFilter);
        if (pmdFiles != null && pmdFiles.length > 0) {
            File selectedFile = pickPreferredModelFile(pmdFiles);
            return buildModelInfo(dir, selectedFile, true, false);
        }

        File[] vrmFiles = dir.listFiles(vrmFilter);
        if (vrmFiles != null && vrmFiles.length > 0) {
            File selectedFile = pickPreferredModelFile(vrmFiles);
            return buildModelInfo(dir, selectedFile, false, true);
        }

        return null;
    }

    private ModelInfo buildModelInfo(File folder, File modelFile, boolean isPmd, boolean isVrm) {
        return new ModelInfo(folder.getName(), folder.getAbsolutePath(),
                modelFile.getAbsolutePath(), modelFile.getName(), isPmd, isVrm, modelFile.length());
    }

    private File pickPreferredModelFile(File[] files) {
        if (files.length == 1) {
            return files[0];
        }

        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.equals("model.pmx") || name.equals("model.pmd")) {
                return file;
            }
        }

        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return files[0];
    }
}
