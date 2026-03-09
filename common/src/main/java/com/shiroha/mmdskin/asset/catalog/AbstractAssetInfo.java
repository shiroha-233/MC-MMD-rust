package com.shiroha.mmdskin.asset.catalog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 资源文件信息基类
 */

public abstract class AbstractAssetInfo<S extends Enum<S>> {
    private static final Logger logger = LogManager.getLogger();

    private final String name;
    private final String displayName;
    private final String filePath;
    private final String fileName;
    private final S source;
    private final String modelName;
    private final long fileSize;

    protected AbstractAssetInfo(String name, String displayName, String filePath,
                                String fileName, S source, String modelName, long fileSize) {
        this.name = name;
        this.displayName = displayName;
        this.filePath = filePath;
        this.fileName = fileName;
        this.source = source;
        this.modelName = modelName;
        this.fileSize = fileSize;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getFilePath() { return filePath; }
    public String getFileName() { return fileName; }
    public S getSource() { return source; }
    public String getModelName() { return modelName; }
    public long getFileSize() { return fileSize; }
    public String getCatalogKey() { return buildCatalogKey(source, modelName, name); }

    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    protected abstract String getSourceDisplayName(S source);

    public String getSourceDescription() {
        String srcName = getSourceDisplayName(source);
        if ("MODEL".equals(source.name()) && modelName != null) {
            return srcName + " (" + modelName + ")";
        }
        return srcName;
    }

    protected static String buildCatalogKey(Enum<?> source, String modelName, String name) {
        String normalizedModelName = modelName == null ? "" : modelName;
        return source.name() + "|" + normalizedModelName + "|" + name;
    }

    public boolean matchesCatalogKey(String catalogKey) {
        return getCatalogKey().equals(catalogKey);
    }

    protected static <T extends AbstractAssetInfo<?>, S extends Enum<S>> List<T> scanDirectory(
            File dir, String extension, S source, String modelName, AssetFactory<T, S> factory) {
        List<T> results = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return results;

        FileFilter filter = file -> file.isFile() && file.getName().toLowerCase().endsWith(extension);
        File[] files = dir.listFiles(filter);
        if (files == null) return results;

        int extLen = extension.length();
        for (File file : files) {
            String fName = file.getName();
            String assetName = fName.substring(0, fName.length() - extLen);
            results.add(factory.create(assetName, file.getAbsolutePath(), fName, source, modelName, file.length()));
            logger.debug("发现资源: {} [{}]", assetName, source);
        }
        return results;
    }

    @FunctionalInterface
    protected interface AssetFactory<T, S> {
        T create(String name, String filePath, String fileName, S source, String modelName, long fileSize);
    }

    protected static <T extends AbstractAssetInfo<?>> void sortBySourceAndName(List<T> list) {
        list.sort(Comparator
                .<T, String>comparing(a -> a.getSource().name())
                .thenComparing(AbstractAssetInfo::getName, String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractAssetInfo<?> that = (AbstractAssetInfo<?>) o;
        return getCatalogKey().equals(that.getCatalogKey());
    }

    @Override
    public int hashCode() {
        return getCatalogKey().hashCode();
    }
}
