package com.shiroha.mmdskin.renderer.animation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 资源文件信息基类
 * 封装 AnimationInfo（VMD）和 MorphInfo（VPD）共有的
 * 字段、扫描逻辑、格式化方法。
 *
 * @param <S> 来源枚举类型（AnimSource / MorphSource）
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

    // ==================== Getters ====================

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getFilePath() { return filePath; }
    public String getFileName() { return fileName; }
    public S getSource() { return source; }
    public String getModelName() { return modelName; }
    public long getFileSize() { return fileSize; }

    /** 格式化文件大小 */
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    /** 获取来源枚举的显示名称（子类实现） */
    protected abstract String getSourceDisplayName(S source);

    /** 来源描述（MODEL 类型时附带模型名） */
    public String getSourceDescription() {
        String srcName = getSourceDisplayName(source);
        if ("MODEL".equals(source.name()) && modelName != null) {
            return srcName + " (" + modelName + ")";
        }
        return srcName;
    }

    // ==================== 通用扫描 ====================

    /**
     * 扫描单个目录，返回资源列表
     *
     * @param dir       目录
     * @param extension 文件扩展名（如 ".vmd"）
     * @param source    来源枚举值
     * @param modelName 模型名（MODEL 来源时使用）
     * @param factory   构造工厂
     */
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

    /** 资源构造工厂接口 */
    @FunctionalInterface
    protected interface AssetFactory<T, S> {
        T create(String name, String filePath, String fileName, S source, String modelName, long fileSize);
    }

    /** 对结果列表按来源+名称排序 */
    protected static <T extends AbstractAssetInfo<?>> void sortBySourceAndName(List<T> list) {
        list.sort(Comparator
                .<T, String>comparing(a -> a.getSource().name())
                .thenComparing(AbstractAssetInfo::getName, String.CASE_INSENSITIVE_ORDER));
    }

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractAssetInfo<?> that = (AbstractAssetInfo<?>) o;
        return name.equals(that.name) && source == that.source;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + source.hashCode();
    }
}
