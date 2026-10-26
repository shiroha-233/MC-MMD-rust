/* 文件职责：模型分发的房主侧配置（服务端权威）。 */
package com.shiroha.mmdskin.distrib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型分发的房主侧配置（服务端权威）。
 *
 * <p>独立于 ConfigManager：ConfigManager 的 provider 只在客户端入口初始化，
 * 专用服务器上取不到值，所以分发配置必须自带一份文件。
 *
 * <p>安全默认：acknowledged / enabled / 每个模型的 distributable 三重全关。
 * acknowledged 是专门给房主的责任确认位——不主动改这一行，分发功能完全不启动。
 */
public final class ModelDistributionConfig {

    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "distribution.json";

    public static final int DEFAULT_RATE_LIMIT_KB_PER_SEC = 256;
    public static final int DEFAULT_GLOBAL_RATE_LIMIT_KB_PER_SEC = 1024;

    /** 房主已确认责任（模型二次配布权由房主自负）。false 时整个分发功能不生效。 */
    public boolean acknowledged = false;

    /** 全局总开关。 */
    public boolean enabled = false;

    /** 单客户端限速（KB/s），0 = 不限。 */
    public int rateLimitKbPerSec = DEFAULT_RATE_LIMIT_KB_PER_SEC;

    /** 服务器总出口配额（KB/s），0 = 不限。 */
    public int globalRateLimitKbPerSec = DEFAULT_GLOBAL_RATE_LIMIT_KB_PER_SEC;

    /** 按模型名配置。 */
    public Map<String, ModelEntry> models = new LinkedHashMap<>();

    public static final class ModelEntry {
        /** 该模型是否允许分发给联机玩家。 */
        public boolean distributable = false;
        /** 可选的 OSS 直链前缀（留空则走服务器转发）。 */
        public String ossUrl = "";
    }

    private static volatile ModelDistributionConfig cached;

    private ModelDistributionConfig() {
    }

    public static File configFile() {
        return new File(PathConstants.getConfigRootDir(), FILE_NAME);
    }

    public static synchronized ModelDistributionConfig load() {
        File file = configFile();
        ModelDistributionConfig config = new ModelDistributionConfig();
        if (file.exists()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                ModelDistributionConfig parsed = GSON.fromJson(reader, ModelDistributionConfig.class);
                if (parsed != null) {
                    config = parsed;
                }
            } catch (Exception e) {
                logger.warn("读取模型分发配置失败，使用默认配置: {}", e.getMessage());
            }
        }
        config.normalizeInPlace();
        cached = config;
        logger.info("模型分发配置: {} (已确认={} 启用={} 单客户端限速={}KB/s 总配额={}KB/s 可分发模型={} 个)",
                file.getAbsolutePath(), config.acknowledged, config.enabled,
                config.rateLimitKbPerSec, config.globalRateLimitKbPerSec, config.models.size());
        return config;
    }

    /** 配置文件的时间戳（用来检测房主改了配置）。 */
    public static long fileStamp() {
        File file = configFile();
        return file.exists() ? file.lastModified() : 0L;
    }

    public static ModelDistributionConfig get() {
        ModelDistributionConfig current = cached;
        if (current == null) {
            current = load();
        }
        return current;
    }

    public static synchronized void reload() {
        load();
    }

    /**
     * 首次运行时写一份带注释的模板，方便房主照着改。
     *
     * <p>房主不该靠猜：模板里把三个开关和两个限速都写清楚了。
     */
    public void ensureTemplate() {
        File file = configFile();
        if (file.exists()) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write(TEMPLATE);
        } catch (Exception e) {
            logger.warn("写分发配置模板失败: {}", e.getMessage());
        }
    }

    private static final String TEMPLATE = ""
            + "{\n"
            + "  \"_说明\": [\n"
            + "    \"启用分发即表示你确认对所列模型拥有二次配布权。\",\n"
            + "    \"本模组不附带任何 MMD 模型，只提供文件传输能力；\",\n"
            + "    \"模型版权与二次配布许可由房主自负，修改后需重启服务器生效。\"\n"
            + "  ],\n"
            + "  \"acknowledged\": false,\n"
            + "  \"enabled\": false,\n"
            + "  \"rateLimitKbPerSec\": 256,\n"
            + "  \"globalRateLimitKbPerSec\": 1024,\n"
            + "  \"models\": {\n"
            + "    \"纳西妲\": { \"distributable\": false, \"ossUrl\": \"\" }\n"
            + "  }\n"
            + "}\n";

    public void save() {
        File file = configFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warn("创建分发配置目录失败: {}", parent);
                return;
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            logger.error("保存模型分发配置失败", e);
        }
    }

    /** 分发是否真正启用：责任已确认 + 总开关打开。 */
    public boolean isActive() {
        return acknowledged && enabled;
    }

    public void normalizeInPlace() {
        if (rateLimitKbPerSec < 0) {
            rateLimitKbPerSec = 0;
        }
        if (globalRateLimitKbPerSec < 0) {
            globalRateLimitKbPerSec = 0;
        }
        if (models == null) {
            models = new LinkedHashMap<>();
        }
        for (Map.Entry<String, ModelEntry> entry : models.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (entry.getValue() == null) {
                entry.setValue(new ModelEntry());
            }
            if (entry.getValue().ossUrl == null) {
                entry.getValue().ossUrl = "";
            }
        }
    }

    /** 该模型是否被房主标记为可分发。 */
    public boolean isDistributable(String modelName) {
        if (!isActive() || modelName == null) {
            return false;
        }
        ModelEntry entry = models.get(modelName);
        return entry != null && entry.distributable;
    }

    /** 该模型配置的 OSS 直链前缀（可能为空）。 */
    public String ossUrlFor(String modelName) {
        if (modelName == null) {
            return "";
        }
        ModelEntry entry = models.get(modelName);
        return entry == null || entry.ossUrl == null ? "" : entry.ossUrl.trim();
    }
}
