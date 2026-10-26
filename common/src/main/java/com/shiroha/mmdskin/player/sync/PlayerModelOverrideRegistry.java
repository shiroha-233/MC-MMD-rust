/* 文件职责：本地「指定玩家模型」表（只影响自己的客户端渲染）。 */
package com.shiroha.mmdskin.player.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地覆盖表：<b>我想让某个玩家在我自己的屏幕上穿成某个模型</b>。
 *
 * <p>刻意做成纯本地：
 * <ul>
 *   <li>不写进服务器、不广播、别人也不需要同意；</li>
 *   <li>服务器该怎么渲染怎么渲染、该怎么管怎么管，跟这个功能没关系；</li>
 *   <li>只改我自己客户端上看到的画面。</li>
 * </ul>
 *
 * <p>键设计见 {@link PlayerModelOverride}：名字为主，UUID 为辅。
 *
 * <p>持久化在客户端自己的 {@code config/mmdskin/model_overrides.json}。
 */
public final class PlayerModelOverrideRegistry {

    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "model_overrides.json";
    private static final int FORMAT_VERSION = 1;

    /** 主表：归一化玩家名 → 覆盖记录。 */
    private static final Map<String, PlayerModelOverride> overrides = new ConcurrentHashMap<>();

    /** 辅助索引：玩家 UUID → 归一化玩家名（玩家改名后仍能认出是同一个人）。 */
    private static final Map<UUID, String> keyByUuid = new ConcurrentHashMap<>();

    private static volatile boolean loaded;

    private PlayerModelOverrideRegistry() {
    }

    // ========================================================================
    // 读写
    // ========================================================================

    /**
     * 设置某个玩家的本地覆盖。
     *
     * @param playerName 玩家名（主键，大小写不敏感）
     * @param uuid       玩家 UUID；不知道时传 {@code null}
     * @param modelName  模型名；空串 / 空白等于取消覆盖
     */
    public static void setOverride(String playerName, UUID uuid, String modelName) {
        ensureLoaded();
        String key = PlayerModelOverride.normalizeName(playerName);
        if (key.isEmpty()) {
            return;
        }
        if (modelName == null || modelName.isBlank()) {
            removeOverride(playerName);
            return;
        }
        PlayerModelOverride previous = overrides.put(key,
                new PlayerModelOverride(playerName.trim(), uuid, modelName));
        if (previous != null && previous.uuid() != null && !previous.uuid().equals(uuid)) {
            keyByUuid.remove(previous.uuid());
        }
        if (uuid != null) {
            keyByUuid.put(uuid, key);
        }
        logger.info("本地指定模型: {} → {}", playerName.trim(), modelName);
        save();
    }

    /** 取消某个玩家的本地覆盖。 */
    public static void removeOverride(String playerName) {
        ensureLoaded();
        String key = PlayerModelOverride.normalizeName(playerName);
        PlayerModelOverride previous = overrides.remove(key);
        if (previous == null) {
            return;
        }
        if (previous.uuid() != null) {
            keyByUuid.remove(previous.uuid());
        }
        logger.info("取消本地指定模型: {}", previous.name());
        save();
    }

    /**
     * 查某个玩家的本地覆盖：<b>名字优先</b>，名字没命中才按 UUID 找。
     *
     * <p>名字相同但 UUID 不同（离线模式 UUID 漂移、重名）时，按名字认——名字是主判断依据。
     *
     * @return 命中的记录；没有命中时返回 {@code null}
     */
    public static PlayerModelOverride findOverride(String playerName, UUID uuid) {
        ensureLoaded();
        String key = PlayerModelOverride.normalizeName(playerName);
        if (!key.isEmpty()) {
            PlayerModelOverride byName = overrides.get(key);
            if (byName != null) {
                return byName;
            }
        }
        if (uuid != null) {
            String nameKey = keyByUuid.get(uuid);
            if (nameKey != null) {
                return overrides.get(nameKey);
            }
        }
        return null;
    }

    /** 某个玩家当前是否被我本地指定过模型。 */
    public static boolean isOverridden(String playerName, UUID uuid) {
        return findOverride(playerName, uuid) != null;
    }

    /** 当前全部本地覆盖（副本，按名字排序）。 */
    public static List<PlayerModelOverride> getAllOverrides() {
        ensureLoaded();
        List<PlayerModelOverride> result = new ArrayList<>(overrides.values());
        result.sort((a, b) -> a.key().compareTo(b.key()));
        return result;
    }

    // ========================================================================
    // 持久化
    // ========================================================================

    public static File configFile() {
        return new File(PathConstants.getConfigRootDir(), FILE_NAME);
    }

    /** 读入本地覆盖表（首次使用时自动调用）。 */
    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = configFile();
        if (!file.exists()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            for (PlayerModelOverride override : parse(JsonParser.parseReader(reader))) {
                if (!override.valid()) {
                    continue;
                }
                overrides.put(override.key(), override);
                if (override.uuid() != null) {
                    keyByUuid.put(override.uuid(), override.key());
                }
            }
        } catch (Exception e) {
            logger.warn("读取本地指定模型配置失败: {}", e.getMessage());
        }
        logger.info("本地指定模型配置: {} 条", overrides.size());
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    /** 把当前表写回本地文件。 */
    private static void save() {
        File file = configFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warn("创建本地指定模型配置目录失败: {}", parent);
                return;
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(toJsonObject(), writer);
            }
        } catch (Exception e) {
            logger.error("保存本地指定模型配置失败", e);
        }
    }

    private static JsonObject toJsonObject() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonArray array = new JsonArray();
        for (PlayerModelOverride override : getAllOverrides()) {
            if (!override.valid()) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("name", override.name());
            if (override.uuid() != null) {
                item.addProperty("uuid", override.uuid().toString());
            }
            item.addProperty("model", override.modelName());
            array.add(item);
        }
        root.add("overrides", array);
        return root;
    }

    private static List<PlayerModelOverride> parse(JsonElement root) {
        List<PlayerModelOverride> result = new ArrayList<>();
        if (root == null || !root.isJsonObject()) {
            return result;
        }
        JsonElement array = root.getAsJsonObject().get("overrides");
        if (array == null || !array.isJsonArray()) {
            return result;
        }
        for (JsonElement element : array.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String name = readString(item, "name");
            String model = readString(item, "model");
            UUID uuid = parseUuid(readString(item, "uuid"));
            if (name == null || name.isBlank() || model == null || model.isBlank()) {
                continue;
            }
            result.add(new PlayerModelOverride(name.trim(), uuid, model.trim()));
        }
        return result;
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 仅供测试清理用。 */
    static void resetForTests() {
        overrides.clear();
        keyByUuid.clear();
        loaded = false;
    }
}
