/* 文件职责：管理表情轮盘可选项与已选项，并支持内建预设和 VPD 发现。 */
package com.shiroha.mmdskin.ui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.shiroha.mmdskin.asset.catalog.MorphInfo;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.expression.BuiltinExpressionRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MorphWheelConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static MorphWheelConfig instance;

    private List<MorphEntry> availableMorphs = new ArrayList<>();
    private List<MorphEntry> displayedMorphs = new ArrayList<>();

    public static class MorphEntry {
        public String entryType;
        public String displayName;
        public String morphName;
        public String presetId;
        public String source;
        public String modelName;
        public String fileSize;
        public String filePath;
        public String catalogKey;
        public boolean selected;

        public MorphEntry() {
        }

        public MorphEntry(String displayName, String morphName, String source, String modelName, String fileSize) {
            this.entryType = "FILE";
            this.displayName = displayName;
            this.morphName = morphName;
            this.presetId = null;
            this.source = source;
            this.modelName = modelName;
            this.fileSize = fileSize;
            this.filePath = null;
            this.catalogKey = null;
            this.selected = false;
        }

        public MorphEntry(String displayName, String morphName, String source,
                          String modelName, String fileSize, String filePath, String catalogKey) {
            this.entryType = "FILE";
            this.displayName = displayName;
            this.morphName = morphName;
            this.presetId = null;
            this.source = source;
            this.modelName = modelName;
            this.fileSize = fileSize;
            this.filePath = filePath;
            this.catalogKey = catalogKey;
            this.selected = false;
        }

        public static MorphEntry from(MorphInfo morph) {
            return new MorphEntry(
                    morph.getDisplayName(),
                    morph.getMorphName(),
                    morph.getSource().name(),
                    morph.getModelName(),
                    morph.getFormattedSize(),
                    morph.getFilePath(),
                    morph.getCatalogKey()
            );
        }

        public static MorphEntry fromPreset(String presetId, String displayName) {
            MorphEntry entry = new MorphEntry();
            entry.entryType = "PRESET";
            entry.displayName = displayName;
            entry.morphName = presetId;
            entry.presetId = presetId;
            entry.source = "BUILTIN";
            entry.modelName = null;
            entry.fileSize = "-";
            entry.filePath = null;
            entry.catalogKey = "preset:" + presetId;
            entry.selected = false;
            return entry;
        }

        public boolean isPreset() {
            return "PRESET".equalsIgnoreCase(entryType) || (presetId != null && !presetId.isEmpty());
        }

        public boolean matches(MorphEntry other) {
            if (other == null) {
                return false;
            }
            if (catalogKey != null && other.catalogKey != null) {
                return Objects.equals(catalogKey, other.catalogKey);
            }
            return Objects.equals(morphName, other.morphName)
                    && Objects.equals(source, other.source)
                    && Objects.equals(modelName, other.modelName);
        }
    }

    public static synchronized MorphWheelConfig getInstance() {
        if (instance == null) {
            instance = new MorphWheelConfig();
            instance.load();
        }
        return instance;
    }

    public void scanAvailableMorphs() {
        availableMorphs.clear();
        availableMorphs.addAll(BuiltinExpressionRegistry.createConfigEntries());

        List<MorphInfo> morphs = MorphInfo.scanAllMorphs();
        for (MorphInfo morph : morphs) {
            availableMorphs.add(MorphEntry.from(morph));
        }

        for (MorphEntry available : availableMorphs) {
            for (MorphEntry displayed : displayedMorphs) {
                if (available.matches(displayed)) {
                    available.selected = true;
                    break;
                }
            }
        }

        List<MorphEntry> normalizedDisplayedMorphs = new ArrayList<>();
        for (MorphEntry displayed : displayedMorphs) {
            MorphEntry matched = findMatchingAvailableMorph(displayed);
            if (matched != null) {
                normalizedDisplayedMorphs.add(matched);
            }
        }
        displayedMorphs = normalizedDisplayedMorphs;
    }

    private MorphEntry findMatchingAvailableMorph(MorphEntry entry) {
        for (MorphEntry available : availableMorphs) {
            if (available.matches(entry)) {
                return available;
            }
        }
        return null;
    }

    public List<MorphEntry> getAvailableMorphs() {
        return Collections.unmodifiableList(availableMorphs);
    }

    public List<MorphEntry> getDisplayedMorphs() {
        return Collections.unmodifiableList(displayedMorphs);
    }

    public void setDisplayedMorphs(List<MorphEntry> morphs) {
        displayedMorphs = new ArrayList<>(morphs);
        for (MorphEntry availableMorph : availableMorphs) {
            availableMorph.selected = displayedMorphs.stream().anyMatch(availableMorph::matches);
        }
    }

    public void updateDisplayedMorphs() {
        displayedMorphs.clear();
        for (MorphEntry entry : availableMorphs) {
            if (entry.selected) {
                displayedMorphs.add(entry);
            }
        }
    }

    public void selectAll() {
        for (MorphEntry entry : availableMorphs) {
            entry.selected = true;
        }
        updateDisplayedMorphs();
    }

    public void clearAll() {
        for (MorphEntry entry : availableMorphs) {
            entry.selected = false;
        }
        updateDisplayedMorphs();
    }

    public void toggleMorph(String morphName) {
        for (MorphEntry entry : availableMorphs) {
            if (entry.morphName.equals(morphName)) {
                entry.selected = !entry.selected;
                break;
            }
        }
        updateDisplayedMorphs();
    }

    public void load() {
        File configFile = PathConstants.getMorphWheelConfigFile();
        if (!configFile.exists()) {
            scanAvailableMorphs();
            selectAll();
            save();
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<MorphEntry>>() {}.getType();
            List<MorphEntry> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) {
                displayedMorphs = new ArrayList<>(loaded);
            }
        } catch (Exception e) {
            LOGGER.error("加载表情配置失败", e);
        }

        scanAvailableMorphs();
    }

    public void save() {
        File configFile = PathConstants.getMorphWheelConfigFile();
        PathConstants.ensureDirectoryExists(configFile.getParentFile());

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            GSON.toJson(displayedMorphs, writer);
        } catch (Exception e) {
            LOGGER.error("保存表情配置失败", e);
        }
    }

    public void reload() {
        load();
    }
}
