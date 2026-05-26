package com.shiroha.mmdskin.ui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** 模型选择配置管理。 */
public class ModelSelectorConfig {
    private static final Logger logger = LogManager.getLogger();
    private static ModelSelectorConfig instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ConfigData data;
    private long lastSaveTime = 0;
    private static final long SAVE_COOLDOWN = 1000;

    private ModelSelectorConfig() {
        load();
    }

    public static synchronized ModelSelectorConfig getInstance() {
        if (instance == null) {
            instance = new ModelSelectorConfig();
        }
        return instance;
    }

    private void load() {
        File configFile = PathConstants.getModelSelectorConfigFile();

        if (configFile.exists()) {
            int retryCount = 0;
            int maxRetries = 3;

            while (retryCount < maxRetries) {
                try (Reader reader = new InputStreamReader(new FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8)) {
                    data = gson.fromJson(reader, ConfigData.class);

                    if (data == null || data.playerModels == null) {
                        throw new IOException("配置数据无效");
                    }

                    if (data.quickModelSlots == null) {
                        data.quickModelSlots = new ConcurrentHashMap<>();
                    }

                    return;
                } catch (Exception e) {
                    retryCount++;
                    logger.warn("加载模型选择配置失败 (尝试 {}/{}): {}", retryCount, maxRetries, e.getMessage());

                    if (retryCount >= maxRetries) {
                        logger.error("配置加载失败，使用默认配置", e);
                        data = new ConfigData();
                        saveInternal(true);
                    }
                }
            }
        } else {
            data = new ConfigData();
            saveInternal(true);
        }
    }

    public synchronized void save() {
        saveInternal(false);
    }

    private void saveInternal(boolean force) {

        long currentTime = System.currentTimeMillis();
        if (!force && currentTime - lastSaveTime < SAVE_COOLDOWN) {
            return;
        }

        File configFile = PathConstants.getModelSelectorConfigFile();

        PathConstants.ensureDirectoryExists(configFile.getParentFile());

        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount < maxRetries) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), java.nio.charset.StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
                lastSaveTime = currentTime;
                logger.debug("模型选择配置保存成功");
                return;
            } catch (Exception e) {
                retryCount++;
                logger.warn("保存模型选择配置失败 (尝试 {}/{}): {}", retryCount, maxRetries, e.getMessage());

                if (retryCount >= maxRetries) {
                    logger.error("配置保存失败", e);
                }
            }
        }
    }

    public String getSelectedModel() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            return getPlayerModel(mc.player.getName().getString());
        }
        return UIConstants.DEFAULT_MODEL_NAME;
    }

    public String getPlayerModel(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return UIConstants.DEFAULT_MODEL_NAME;
        }
        return data.playerModels.getOrDefault(playerName, UIConstants.DEFAULT_MODEL_NAME);
    }

    public void setSelectedModel(String modelName) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            setPlayerModel(mc.player.getName().getString(), modelName);
        }
    }

    public void setPlayerModel(String playerName, String modelName) {
        if (playerName == null || playerName.isEmpty()) {
            logger.warn("尝试为空玩家名设置模型");
            return;
        }

        if (modelName == null) {
            modelName = UIConstants.DEFAULT_MODEL_NAME;
        }

        data.playerModels.put(playerName, modelName);
        save();

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.player.getName().getString().equals(playerName)) {
            PlayerModelSyncManager.broadcastLocalModelSelection(mc.player.getUUID(), modelName);
        }
    }

    public void removePlayerModel(String playerName) {
        if (data.playerModels.remove(playerName) != null) {
            save();
        }
    }

    public Map<String, String> getAllPlayerModels() {
        return new ConcurrentHashMap<>(data.playerModels);
    }

    public static final int QUICK_SLOT_COUNT = 4;

    public String getQuickSlotModel(int slot) {
        if (slot < 0 || slot >= QUICK_SLOT_COUNT) return null;
        String key = String.valueOf(slot);
        return data.quickModelSlots.get(key);
    }

    public void setQuickSlotModel(int slot, String modelName) {
        if (slot < 0 || slot >= QUICK_SLOT_COUNT) return;
        String key = String.valueOf(slot);
        if (modelName == null || modelName.isEmpty()) {
            data.quickModelSlots.remove(key);
        } else {
            data.quickModelSlots.put(key, modelName);
        }
        save();
    }

    public int getQuickSlotForModel(String modelName) {
        if (modelName == null) return -1;
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            String bound = data.quickModelSlots.get(String.valueOf(i));
            if (modelName.equals(bound)) return i;
        }
        return -1;
    }

    private static class ConfigData {
        Map<String, String> playerModels = new ConcurrentHashMap<>();

        Map<String, String> quickModelSlots = new ConcurrentHashMap<>();
    }
}
