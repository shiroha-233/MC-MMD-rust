package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型信息类
 * 用于扫描和存储模型文件信息
 * 
 * 支持任意名称的 PMX/PMD 文件，按文件夹分类
 */
public class ModelInfo {
    private static final Logger logger = LogManager.getLogger();
    
    // 扫描结果缓存（不可变快照，保证原子性）
    private record CacheSnapshot(List<ModelInfo> models, long timestamp) {}
    private static volatile CacheSnapshot cache = null;
    private static final long CACHE_TTL = 5000; // 5 秒缓存有效期
    
    private final String folderName;      // 文件夹名称（用于显示）
    private final String folderPath;      // 文件夹完整路径
    private final String modelFilePath;   // 模型文件完整路径
    private final String modelFileName;   // 模型文件名
    private final boolean isPMD;          // 是否为 PMD 格式
    private final boolean isVRM;          // 是否为 VRM 格式
    private final long fileSize;          // 文件大小（字节）
    
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
    public String getFolderPath() { return folderPath; }
    public String getModelFilePath() { return modelFilePath; }
    public String getModelFileName() { return modelFileName; }
    public boolean isPMD() { return isPMD; }
    public boolean isVRM() { return isVRM; }
    public long getFileSize() { return fileSize; }
    
    /**
     * 获取格式化的文件大小
     */
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
    
    /**
     * 获取模型格式描述
     */
    public String getFormatDescription() {
        if (isVRM) return "VRM";
        return isPMD ? "PMD" : "PMX";
    }
    
    /**
     * 扫描 EntityPlayer 目录下的所有模型
     * 支持任意名称的 .pmx 和 .pmd 文件
     */
    public static List<ModelInfo> scanModels() {
        // 检查缓存是否有效（单次 volatile 读取，保证原子性）
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
        
        // 按文件夹名称排序
        models.sort((a, b) -> a.getFolderName().compareToIgnoreCase(b.getFolderName()));
        
        // 原子更新缓存（单次 volatile 写入）
        cache = new CacheSnapshot(models, System.currentTimeMillis());
        
        return models;
    }
    
    /**
     * 使缓存失效（下次调用 scanModels 会重新扫描磁盘）
     */
    public static void invalidateCache() {
        cache = null;
    }
    
    /**
     * 扫描单个模型文件夹
     * 优先查找 PMX，其次 PMD
     * 支持任意文件名
     */
    private static ModelInfo scanModelFolder(File modelDir) {
        FileFilter pmxFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmx");
        FileFilter pmdFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".pmd");
        FileFilter vrmFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".vrm");

        // 优先 PMX
        File[] pmxFiles = modelDir.listFiles(pmxFilter);
        if (pmxFiles != null && pmxFiles.length > 0) {
            File selectedFile = findPreferredModel(pmxFiles);
            return new ModelInfo(
                modelDir.getName(), modelDir.getAbsolutePath(),
                selectedFile.getAbsolutePath(), selectedFile.getName(),
                false, false, selectedFile.length()
            );
        }

        // 其次 PMD
        File[] pmdFiles = modelDir.listFiles(pmdFilter);
        if (pmdFiles != null && pmdFiles.length > 0) {
            File selectedFile = findPreferredModel(pmdFiles);
            return new ModelInfo(
                modelDir.getName(), modelDir.getAbsolutePath(),
                selectedFile.getAbsolutePath(), selectedFile.getName(),
                true, false, selectedFile.length()
            );
        }

        // 最后 VRM
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
    
    /**
     * 从多个模型文件中选择首选的
     * 优先级：model.pmx/model.pmd > 其他文件（按名称排序取第一个）
     */
    private static File findPreferredModel(File[] files) {
        if (files.length == 1) {
            return files[0];
        }
        
        // 优先查找 model.pmx 或 model.pmd
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.equals("model.pmx") || name.equals("model.pmd")) {
                return file;
            }
        }
        
        // 按名称排序，选择第一个
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files[0];
    }
    
    /**
     * 根据文件夹名查找模型信息
     */
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
