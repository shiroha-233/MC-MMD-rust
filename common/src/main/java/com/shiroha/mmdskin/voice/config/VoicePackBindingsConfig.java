package com.shiroha.mmdskin.voice.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.voice.VoiceUsageMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VoicePackBindingsConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VoicePackBindingsConfig instance;

    private Data data;

    private VoicePackBindingsConfig() {
        load();
    }

    public static synchronized VoicePackBindingsConfig getInstance() {
        if (instance == null) {
            instance = new VoicePackBindingsConfig();
        }
        return instance;
    }

    public synchronized void save() {
        File configFile = PathConstants.getVoicePackBindingsConfigFile();
        PathConstants.ensureDirectoryExists(configFile.getParentFile());
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.error("保存语音包绑定配置失败", e);
        }
    }

    private void load() {
        File configFile = PathConstants.getVoicePackBindingsConfigFile();
        if (!configFile.exists()) {
            data = new Data();
            save();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            data = GSON.fromJson(reader, Data.class);
            if (data == null) {
                data = new Data();
            }
            data.normalize();
        } catch (Exception e) {
            LOGGER.error("加载语音包绑定配置失败，使用默认值", e);
            data = new Data();
            save();
        }
    }

    public synchronized String getPlayerDefaultPackId() {
        return normalizePackId(data.player.defaultPackId);
    }

    public synchronized void setPlayerDefaultPackId(String packId) {
        data.player.defaultPackId = normalizePackId(packId);
        save();
    }

    public synchronized String getPlayerModelPackId(String modelName) {
        return normalizePackId(data.player.byModel.get(modelName));
    }

    public synchronized void setPlayerModelPackId(String modelName, String packId) {
        setModelBinding(data.player.byModel, modelName, packId);
        save();
    }

    public synchronized String getPlayerUsagePackId(VoiceUsageMode mode) {
        return getUsageBinding(data.player.byUsageMode, mode);
    }

    public synchronized void setPlayerUsagePackId(VoiceUsageMode mode, String packId) {
        setUsageBinding(data.player.byUsageMode, mode, packId);
        save();
    }

    public synchronized String getMaidDefaultPackId() {
        return normalizePackId(data.maid.defaultPackId);
    }

    public synchronized void setMaidDefaultPackId(String packId) {
        data.maid.defaultPackId = normalizePackId(packId);
        save();
    }

    public synchronized String getMaidModelPackId(String modelName) {
        return normalizePackId(data.maid.byModel.get(modelName));
    }

    public synchronized void setMaidModelPackId(String modelName, String packId) {
        setModelBinding(data.maid.byModel, modelName, packId);
        save();
    }

    public synchronized String getMaidUsagePackId(VoiceUsageMode mode) {
        return getUsageBinding(data.maid.byUsageMode, mode);
    }

    public synchronized void setMaidUsagePackId(VoiceUsageMode mode, String packId) {
        setUsageBinding(data.maid.byUsageMode, mode, packId);
        save();
    }

    public synchronized String getMobDefaultPackId() {
        return normalizePackId(data.mob.defaultPackId);
    }

    public synchronized void setMobDefaultPackId(String packId) {
        data.mob.defaultPackId = normalizePackId(packId);
        save();
    }

    public synchronized String getMobEntityTypePackId(String entityTypeId) {
        return normalizePackId(data.mob.byEntityType.get(entityTypeId));
    }

    public synchronized void setMobEntityTypePackId(String entityTypeId, String packId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return;
        }
        String normalized = normalizePackId(packId);
        if (normalized == null) {
            data.mob.byEntityType.remove(entityTypeId);
        } else {
            data.mob.byEntityType.put(entityTypeId, normalized);
        }
        save();
    }

    private static String getUsageBinding(Map<String, String> bindings, VoiceUsageMode mode) {
        if (mode == null) {
            return null;
        }
        return normalizePackId(bindings.get(mode.configKey()));
    }

    private static void setUsageBinding(Map<String, String> bindings, VoiceUsageMode mode, String packId) {
        if (mode == null) {
            return;
        }
        String normalized = normalizePackId(packId);
        if (normalized == null) {
            bindings.remove(mode.configKey());
        } else {
            bindings.put(mode.configKey(), normalized);
        }
    }

    private static void setModelBinding(Map<String, String> bindings, String modelName, String packId) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        String normalized = normalizePackId(packId);
        if (normalized == null) {
            bindings.remove(modelName);
        } else {
            bindings.put(modelName, normalized);
        }
    }

    private static String normalizePackId(String packId) {
        if (packId == null || packId.isBlank() || "none".equalsIgnoreCase(packId)) {
            return null;
        }
        return packId.trim();
    }

    private static final class Data {
        private BindingGroup player = new BindingGroup();
        private BindingGroup maid = new BindingGroup();
        private MobBindingGroup mob = new MobBindingGroup();

        private void normalize() {
            if (player == null) {
                player = new BindingGroup();
            }
            if (maid == null) {
                maid = new BindingGroup();
            }
            if (mob == null) {
                mob = new MobBindingGroup();
            }
            player.normalize();
            maid.normalize();
            mob.normalize();
        }
    }

    private static class BindingGroup {
        private String defaultPackId;
        private Map<String, String> byModel = new LinkedHashMap<>();
        private Map<String, String> byUsageMode = new LinkedHashMap<>();

        private void normalize() {
            if (byModel == null) {
                byModel = new LinkedHashMap<>();
            }
            if (byUsageMode == null) {
                byUsageMode = new LinkedHashMap<>();
            }
        }
    }

    private static final class MobBindingGroup {
        private String defaultPackId;
        private Map<String, String> byEntityType = new LinkedHashMap<>();

        private void normalize() {
            if (byEntityType == null) {
                byEntityType = new LinkedHashMap<>();
            }
        }
    }
}
