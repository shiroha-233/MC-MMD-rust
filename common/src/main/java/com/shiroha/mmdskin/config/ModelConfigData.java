package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 单个模型的独立配置数据
 * 每个模型一个 JSON 文件，存储在 config/mmdskin/model_configs/ 目录下
 */
public class ModelConfigData {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // ==================== 眼球追踪 ====================
    /** 眼球追踪启用（默认 true） */
    public boolean eyeTrackingEnabled = true;
    
    /** 眼球最大转动角度（弧度），默认 0.1745（约 10°） */
    public float eyeMaxAngle = 0.1745f;
    
    // ==================== 模型缩放 ====================
    /** 模型整体缩放（默认 1.0） */
    public float modelScale = 1.0f;
    
    // ==================== 材质可见性 ====================
    /** 隐藏的材质索引集合（未列出的材质默认可见） */
    public Set<Integer> hiddenMaterials = new HashSet<>();
    
    /**
     * 从文件加载配置
     */
    public static ModelConfigData load(File configFile) {
        if (!configFile.exists()) {
            return new ModelConfigData();
        }
        
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            ModelConfigData config = GSON.fromJson(reader, ModelConfigData.class);
            if (config == null) {
                return new ModelConfigData();
            }
            return config;
        } catch (Exception e) {
            logger.warn("加载模型配置失败: {}, 使用默认配置", e.getMessage());
            return new ModelConfigData();
        }
    }
    
    /**
     * 创建副本（用于编辑时不影响缓存）
     */
    public ModelConfigData copy() {
        ModelConfigData c = new ModelConfigData();
        c.eyeTrackingEnabled = this.eyeTrackingEnabled;
        c.eyeMaxAngle = this.eyeMaxAngle;
        c.modelScale = this.modelScale;
        c.hiddenMaterials = new HashSet<>(this.hiddenMaterials);
        return c;
    }
    
    /**
     * 保存配置到文件
     */
    public void save(File configFile) {
        PathConstants.ensureDirectoryExists(configFile.getParentFile());
        
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
            logger.debug("模型配置已保存: {}", configFile.getName());
        } catch (IOException e) {
            logger.error("保存模型配置失败: {}", e.getMessage());
        }
    }
}
