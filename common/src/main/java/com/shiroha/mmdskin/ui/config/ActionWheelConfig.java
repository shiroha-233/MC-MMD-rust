package com.shiroha.mmdskin.ui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.asset.catalog.AnimationInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 动作轮盘配置管理。 */
public class ActionWheelConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static ActionWheelConfig instance;

    private List<ActionEntry> displayedActions;
    private List<ActionEntry> availableActions;

    private ActionWheelConfig() {
        this.displayedActions = new ArrayList<>();
        this.availableActions = new ArrayList<>();
        scanAvailableActions();
    }

    public static synchronized ActionWheelConfig getInstance() {
        if (instance == null) {
            instance = new ActionWheelConfig();
            instance.load();
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = null;
    }

    public void scanAvailableActions() {
        availableActions.clear();

        List<AnimationInfo> animations = AnimationInfo.scanAllAnimations();

        for (AnimationInfo anim : animations) {
            availableActions.add(ActionEntry.from(anim));
        }
    }

    public void load() {
        try {
            File configFile = PathConstants.getActionWheelConfigFile();
            if (!configFile.exists()) {
                loadDefaultDisplayedActions();
                save();
                return;
            }

            try (Reader reader = new InputStreamReader(new FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                ConfigData data = gson.fromJson(reader, ConfigData.class);
                if (data != null && data.displayedActions != null) {
                    this.displayedActions = data.displayedActions;

                    filterValidActions();
                } else {
                    loadDefaultDisplayedActions();
                }
            }
        } catch (Exception e) {
            LOGGER.error("加载动作轮盘配置失败", e);
            loadDefaultDisplayedActions();
        }
    }

    private void loadDefaultDisplayedActions() {
        displayedActions.clear();
        displayedActions.addAll(availableActions);
    }

    private void filterValidActions() {
        displayedActions.removeIf(displayed -> findMatchingAvailableAction(displayed) == null);
        displayedActions.replaceAll(displayed -> {
            ActionEntry matched = findMatchingAvailableAction(displayed);
            return matched != null ? matched : displayed;
        });
    }

    private ActionEntry findMatchingAvailableAction(ActionEntry entry) {
        for (ActionEntry available : availableActions) {
            if (available.matches(entry)) {
                return available;
            }
        }
        return null;
    }

    public void save() {
        try {
            File configFile = PathConstants.getActionWheelConfigFile();
            PathConstants.ensureDirectoryExists(configFile.getParentFile());

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), java.nio.charset.StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                ConfigData data = new ConfigData();
                data.displayedActions = this.displayedActions;
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.error("保存动作轮盘配置失败", e);
        }
    }

    public List<ActionEntry> getDisplayedActions() {
        return Collections.unmodifiableList(displayedActions);
    }

    public List<ActionEntry> getAvailableActions() {
        return Collections.unmodifiableList(availableActions);
    }

    public void setDisplayedActions(List<ActionEntry> actions) {
        this.displayedActions = new ArrayList<>(actions);
    }

    public void rescan() {
        scanAvailableActions();
        filterValidActions();
    }

    public static class ActionEntry {
        public String name;
        public String animId;
        public String source;
        public String modelName;
        public String fileSize;
        public String catalogKey;

        public ActionEntry() {}

        public ActionEntry(String name, String animId) {
            this.name = name;
            this.animId = animId;
            this.source = "CUSTOM";
            this.modelName = null;
            this.fileSize = "";
            this.catalogKey = null;
        }

        public ActionEntry(String name, String animId, String source, String modelName, String fileSize) {
            this.name = name;
            this.animId = animId;
            this.source = source;
            this.modelName = modelName;
            this.fileSize = fileSize;
            this.catalogKey = null;
        }

        public ActionEntry(String name, String animId, String source, String modelName, String fileSize, String catalogKey) {
            this.name = name;
            this.animId = animId;
            this.source = source;
            this.modelName = modelName;
            this.fileSize = fileSize;
            this.catalogKey = catalogKey;
        }

        public static ActionEntry from(AnimationInfo anim) {
            return new ActionEntry(
                anim.getDisplayName(),
                anim.getAnimName(),
                anim.getSource().name(),
                anim.getModelName(),
                anim.getFormattedSize(),
                anim.getCatalogKey()
            );
        }

        public boolean matches(ActionEntry other) {
            if (other == null) {
                return false;
            }
            if (catalogKey != null && other.catalogKey != null) {
                return Objects.equals(catalogKey, other.catalogKey);
            }
            return Objects.equals(animId, other.animId)
                && Objects.equals(source, other.source)
                && Objects.equals(modelName, other.modelName);
        }

        public String getSourceDescription() {
            if (source == null) return "未知";
            switch (source) {
                case "DEFAULT": return "默认动画";
                case "CUSTOM": return "自定义动画";
                case "MODEL": return modelName != null ? "模型专属 (" + modelName + ")" : "模型专属";
                default: return source;
            }
        }
    }

    private static class ConfigData {
        List<ActionEntry> displayedActions;
    }
}
