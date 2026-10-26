/* 文件职责：承载全局客户端配置并负责加载、保存与归一化。 */
package com.shiroha.mmdskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigData {
    public static final int CURRENT_SCHEMA_VERSION = 3;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public boolean lightingEnabled = true;
    public boolean gpuSkinningEnabled = true;
    public int modelPoolMaxCount = 20;
    /** 自动下载附近玩家正在使用的模型（仅限房主授权分发的模型）。 */
    public boolean autoDownloadNearbyModels = false;

    /** 自动下载的半径（格）。 */
    public int autoDownloadRadius = 48;

    public boolean performanceProfilingEnabled = false;
    public int performanceLogIntervalSeconds = 5;
    public int maxVisibleModelsPerFrame = 20;
    /**
     * 加载前距离闸门（格）。
     *
     * <p>超过这个距离且模型<b>尚未加载</b>时不触发加载（PMX 解析 + 独立 Bullet 世界 +
     * 4 个 GPU buffer 全部省掉）；已加载的模型继续正常渲染，走近时不会闪回原版皮肤。
     * 0 = 不限制（闸门关闭，等价于旧行为）。
     */
    public float modelLoadMaxDistance = 64.0f;
    public float animationLodMediumDistance = 24.0f;
    public float animationLodFarDistance = 48.0f;
    public int animationLodMediumUpdateInterval = 2;
    public int animationLodFarUpdateInterval = 4;

    public boolean toonRenderingEnabled = false;
    public int toonLevels = 4;
    public float toonRimPower = 5.6f;
    public float toonRimIntensity = 0.02f;
    public float toonShadowR = 0.78f;
    public float toonShadowG = 0.84f;
    public float toonShadowB = 0.94f;
    public float toonSpecularPower = 96.0f;
    public float toonSpecularIntensity = 0.015f;
    public boolean toonOutlineEnabled = true;
    public float toonOutlineWidth = 0.0022f;
    public float toonOutlineR = 0.06f;
    public float toonOutlineG = 0.08f;
    public float toonOutlineB = 0.12f;

    public boolean physicsEnabled = true;
    public float physicsGravityY = -98.0f;
    public float physicsFps = 60.0f;
    public int physicsMaxSubstepCount = 5;
    public float physicsInertiaStrength = 0.5f;
    public float physicsMaxLinearVelocity = 20.0f;
    public float physicsMaxAngularVelocity = 20.0f;
    public boolean physicsJointsEnabled = true;
    public boolean physicsKinematicFilter = true;
    public boolean physicsDebugLog = false;
    public int maxPhysicsModelsPerFrame = 10;
    public float physicsLodMaxDistance = 24.0f;

    public boolean firstPersonModelEnabled = false;
    public float firstPersonCameraForwardOffset = 0.0f;
    public float firstPersonCameraVerticalOffset = 0.0f;

    public boolean ragdollEnabled = true;
    public String corpseKeepMode = "NEARBY";
    public double corpseKeepRadius = 48.0;
    public long corpseKeepSeconds = 60;
    public int maxCorpses = 2;
    public float ragdollJointStrength = 1.35f;
    public boolean hideDeathPoof = true;
    public boolean hijackDeathScreen = true;
    public float ragdollDeathAnimSeconds = 0.0f;
    public int ragdollPhysicsKeepSeconds = 60;

    public int textureCacheBudgetMB = 256;
    public boolean debugHudEnabled = false;

    public boolean vrEnabled = false;
    public float vrArmIKStrength = 1.0f;

    public Map<String, String> mobModelReplacements = new LinkedHashMap<>();

    public static ConfigData load(Path configPath) {
        Path configFile = configPath.resolve("config.json");
        if (!Files.exists(configFile)) {
            ConfigData defaultConfig = new ConfigData();
            defaultConfig.save(configPath);
            return defaultConfig;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("配置根节点必须是 JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            boolean migrated = migrate(root);
            ConfigData config = GSON.fromJson(root, ConfigData.class);
            if (config == null) {
                LOGGER.warn("配置文件为空，使用默认配置");
                return new ConfigData();
            }
            config.normalize();
            if (migrated) {
                config.save(configPath);
            }
            return config;
        } catch (Exception e) {
            LOGGER.error("配置加载失败，使用默认配置: {}", e.getMessage());
            return new ConfigData();
        }
    }

    public void save(Path configPath) {
        Path temporaryFile = null;
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
            }

            Path configFile = configPath.resolve("config.json");
            temporaryFile = Files.createTempFile(configPath, "config.json.", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            moveAtomically(temporaryFile, configFile);
        } catch (IOException e) {
            LOGGER.error("保存配置失败", e);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException e) {
                    LOGGER.debug("清理配置临时文件失败: {}", temporaryFile, e);
                }
            }
        }
    }

    public void copyTo(ConfigData other) {
        ConfigData copy = GSON.fromJson(GSON.toJson(this), ConfigData.class);
        try {
            for (var field : ConfigData.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.set(other, field.get(copy));
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("配置复制失败", e);
        }
    }

    private void normalize() {
        schemaVersion = CURRENT_SCHEMA_VERSION;
        if (mobModelReplacements == null) {
            mobModelReplacements = new LinkedHashMap<>();
        }

        modelPoolMaxCount = Math.max(1, modelPoolMaxCount);
        performanceLogIntervalSeconds = Math.max(1, performanceLogIntervalSeconds);
        maxVisibleModelsPerFrame = Math.max(1, maxVisibleModelsPerFrame);
        // 0 = 不限制（闸门关闭）；NaN 必须单独挡掉，否则距离比较恒不成立（等于永不加载）。
        modelLoadMaxDistance = Float.isFinite(modelLoadMaxDistance)
                ? Math.max(0.0f, modelLoadMaxDistance)
                : 64.0f;
        animationLodMediumDistance = Math.max(0.0f, animationLodMediumDistance);
        animationLodFarDistance = Math.max(animationLodMediumDistance, animationLodFarDistance);
        animationLodMediumUpdateInterval = Math.max(1, animationLodMediumUpdateInterval);
        animationLodFarUpdateInterval = Math.max(animationLodMediumUpdateInterval, animationLodFarUpdateInterval);
        toonLevels = Math.max(2, Math.min(5, toonLevels));
        toonRimPower = clamp(toonRimPower, 0.1f, 10.0f);
        toonRimIntensity = clamp(toonRimIntensity, 0.0f, 1.0f);
        toonShadowR = clamp(toonShadowR, 0.0f, 1.0f);
        toonShadowG = clamp(toonShadowG, 0.0f, 1.0f);
        toonShadowB = clamp(toonShadowB, 0.0f, 1.0f);
        toonSpecularPower = clamp(toonSpecularPower, 1.0f, 128.0f);
        toonSpecularIntensity = clamp(toonSpecularIntensity, 0.0f, 1.0f);
        toonOutlineWidth = clamp(toonOutlineWidth, 0.001f, 0.02f);
        toonOutlineR = clamp(toonOutlineR, 0.0f, 1.0f);
        toonOutlineG = clamp(toonOutlineG, 0.0f, 1.0f);
        toonOutlineB = clamp(toonOutlineB, 0.0f, 1.0f);
        maxPhysicsModelsPerFrame = Math.max(1, maxPhysicsModelsPerFrame);
        physicsLodMaxDistance = Math.max(0.0f, physicsLodMaxDistance);
        corpseKeepMode = normalizeCorpseKeepMode(corpseKeepMode);
        // 上下限与 1.20.1 的 ConfigData 归一化保持一致。26.3 移植时这些上限被放开了：
        // 每具尸体 = 1 个 native 模型实例 + 1 个 Bullet world（16 刚体 + 15 约束）
        // + 持续的渲染与物理步进，没有上限时用户一个手改的配置文件就能吃满内存/CPU。
        //
        // NaN 要单独挡掉：clamp 是 Math.max(min, Math.min(max, v))，Math.min(256, NaN) = NaN，
        // NaN 会原样穿过去 —— corpseKeepRadius = NaN 时距离逐出恒不成立（尸体永不逐出）。
        corpseKeepRadius = Double.isFinite(corpseKeepRadius)
                ? clamp((float) corpseKeepRadius, 8.0f, 256.0f)
                : 48.0;
        corpseKeepSeconds = Math.max(5L, corpseKeepSeconds);
        maxCorpses = Math.max(1, Math.min(64, maxCorpses));
        ragdollJointStrength = Float.isFinite(ragdollJointStrength)
                ? clamp(ragdollJointStrength, 0.3f, 4.0f)
                : 1.35f;
        ragdollDeathAnimSeconds = Float.isFinite(ragdollDeathAnimSeconds)
                ? clamp(ragdollDeathAnimSeconds, 0.0f, 5.0f)
                : 0.0f;
        ragdollPhysicsKeepSeconds = Math.max(0, Math.min(3600, ragdollPhysicsKeepSeconds));
        vrArmIKStrength = !Float.isFinite(vrArmIKStrength) ? 1.0f : clamp(vrArmIKStrength, 0.0f, 1.0f);
    }

    /** 尸体保留模式只接受三种取值，未知值回退到 NEARBY。 */
    private static String normalizeCorpseKeepMode(String mode) {
        if (mode == null) {
            return "NEARBY";
        }
        String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "NEARBY", "SECONDS", "FOREVER" -> normalized;
            default -> "NEARBY";
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean migrate(JsonObject root) {
        int previousVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
        boolean changed = previousVersion != CURRENT_SCHEMA_VERSION;
        if (!root.has("lightingEnabled") && root.has("openGLEnableLighting")) {
            root.add("lightingEnabled", root.get("openGLEnableLighting"));
            changed = true;
        }
        changed |= root.remove("openGLEnableLighting") != null;
        changed |= root.remove("mmdShaderEnabled") != null;
        if (!root.has("gpuSkinningEnabled")) {
            root.addProperty("gpuSkinningEnabled", true);
            changed = true;
        }
        if (!root.has("modelLoadMaxDistance")) {
            // 26.3 性能优化新增的"加载前距离闸门"。旧配置补一个默认值：
            // 取值与代码里的默认值一致（行为不变），只是让这个开关在配置文件里看得见、可调。
            root.addProperty("modelLoadMaxDistance", 64.0);
            changed = true;
        }
        if (!root.has("autoDownloadNearbyModels")) {
            // 1.20.1 的「模型自动下载」配置项在 26.3 落地。旧配置补默认值：
            // 默认关闭（与 1.20.1 一致），只是让这两个键在配置文件里看得见、可调。
            root.addProperty("autoDownloadNearbyModels", false);
            changed = true;
        }
        if (!root.has("autoDownloadRadius")) {
            root.addProperty("autoDownloadRadius", 48);
            changed = true;
        }
        changed |= root.remove("gpuMorphEnabled") != null;
        changed |= root.remove("maxBones") != null;
        root.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
        return changed;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(atomicFailure);
                throw fallbackFailure;
            }
        }
    }
}
