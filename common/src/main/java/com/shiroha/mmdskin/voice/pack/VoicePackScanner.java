package com.shiroha.mmdskin.voice.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.shiroha.mmdskin.MmdSkinClient;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.voice.VoiceEventType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VoicePackScanner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_PACKS = 256;
    public static final int MAX_AUDIO_FILES_PER_EVENT = 32;
    public static final long MAX_MANIFEST_BYTES = 64 * 1024L;
    public static final long MAX_AUDIO_FILE_BYTES = 20L * 1024L * 1024L;

    private VoicePackScanner() {
    }

    public static List<VoicePackDefinition> scan(File voicePackRoot) {
        List<VoicePackDefinition> packs = new ArrayList<>();
        if (voicePackRoot == null || !voicePackRoot.exists() || !voicePackRoot.isDirectory()) {
            return packs;
        }

        File[] packDirs = voicePackRoot.listFiles(File::isDirectory);
        if (packDirs == null) {
            return packs;
        }

        Set<String> seenIds = new HashSet<>();
        for (File packDir : packDirs) {
            if (packs.size() >= MAX_PACKS) {
                LOGGER.warn("语音包数量超过上限，后续目录将被跳过: {}", voicePackRoot.getAbsolutePath());
                break;
            }
            VoicePackDefinition definition = scanSinglePack(packDir);
            if (definition == null) {
                continue;
            }
            if (!seenIds.add(definition.getId())) {
                LOGGER.warn("跳过重复语音包 ID: {} ({})", definition.getId(), packDir.getAbsolutePath());
                continue;
            }
            packs.add(definition);
        }

        packs.sort(Comparator.comparing(VoicePackDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return packs;
    }

    private static VoicePackDefinition scanSinglePack(File packDir) {
        if (!isSafeChildDirectory(packDir.getParentFile(), packDir)) {
            LOGGER.warn("跳过越界或非法语音包目录: {}", packDir.getAbsolutePath());
            return null;
        }
        File manifestFile = new File(packDir, PathConstants.VOICE_PACK_MANIFEST);
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            return null;
        }

        VoicePackManifest manifest = readManifest(manifestFile);
        if (manifest == null || manifest.id == null || manifest.id.isBlank()) {
            LOGGER.warn("语音包缺少有效 ID，已跳过: {}", packDir.getAbsolutePath());
            return null;
        }

        String displayName = manifest.displayName == null || manifest.displayName.isBlank()
                ? manifest.id
                : manifest.displayName;
        Map<VoiceEventType, VoiceEventBinding> bindings = new EnumMap<>(VoiceEventType.class);
        for (VoiceEventType eventType : VoiceEventType.values()) {
            VoicePackManifest.EventSpec spec = manifest.events.get(eventType.configKey());
            String folderName = spec != null && spec.folder != null && !spec.folder.isBlank()
                    ? spec.folder
                    : eventType.configKey();
            File eventDir = new File(packDir, folderName);
            DetailScanResult scanResult = scanEventDirectory(eventDir);
            if (scanResult.baseClips().isEmpty() && scanResult.detailClips().isEmpty()) {
                continue;
            }
            int cooldownTicks = spec != null ? Math.max(0, spec.cooldownTicks) : 0;
            int priority = spec != null ? spec.priority : 0;
            String mode = spec != null && spec.mode != null && !spec.mode.isBlank() ? spec.mode : "weighted_random";
            bindings.put(eventType, new VoiceEventBinding(folderName, cooldownTicks, priority, mode,
                    scanResult.baseClips(), scanResult.detailClips()));
        }

        return new VoicePackDefinition(manifest.id.trim(), displayName, packDir.getAbsolutePath(), manifest, bindings);
    }

    private static VoicePackManifest readManifest(File manifestFile) {
        if (!isSafeChildFile(manifestFile.getParentFile(), manifestFile)) {
            LOGGER.warn("语音包 manifest 路径非法，已跳过: {}", manifestFile.getAbsolutePath());
            return null;
        }
        if (manifestFile.length() <= 0 || manifestFile.length() > MAX_MANIFEST_BYTES) {
            LOGGER.warn("语音包 manifest 超出允许大小，已跳过: {}", manifestFile.getAbsolutePath());
            return null;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(manifestFile), StandardCharsets.UTF_8)) {
            VoicePackManifest manifest = GSON.fromJson(reader, VoicePackManifest.class);
            if (manifest == null) {
                LOGGER.warn("语音包 manifest 为空: {}", manifestFile.getAbsolutePath());
                return null;
            }
            if (manifest.events == null) {
                manifest.events = Map.of();
            }
            if (manifest.playback == null) {
                manifest.playback = new VoicePackManifest.Playback();
            }
            manifest.defaultVolume = clampVolume(manifest.defaultVolume);
            if (manifest.schemaVersion <= 0) {
                manifest.schemaVersion = 1;
            }
            normalizeManifest(manifest);
            return manifest;
        } catch (Exception e) {
            MmdSkinClient.logger.error("读取语音包 manifest 失败: {}", manifestFile.getAbsolutePath(), e);
            return null;
        }
    }

    private static float clampVolume(float volume) {
        if (volume <= 0.0f) {
            return 1.0f;
        }
        return Math.min(1.0f, volume);
    }

    private static List<VoiceClipDefinition> scanAudioFiles(File dir) {
        List<VoiceClipDefinition> results = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory() || !isSafeChildDirectory(dir.getParentFile(), dir)) {
            return results;
        }
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            for (String ext : PathConstants.AUDIO_EXTENSIONS) {
                if (lower.endsWith(ext)) {
                    return true;
                }
            }
            return false;
        });
        if (files == null) {
            return results;
        }
        for (File file : files) {
            if (results.size() >= MAX_AUDIO_FILES_PER_EVENT) {
                LOGGER.warn("事件音频数量超过上限，已截断: {}", dir.getAbsolutePath());
                break;
            }
            if (!isSafeChildFile(dir, file)) {
                LOGGER.warn("跳过越界或非法语音文件: {}", file.getAbsolutePath());
                continue;
            }
            if (file.length() <= 0 || file.length() > MAX_AUDIO_FILE_BYTES) {
                LOGGER.warn("跳过超限语音文件: {}", file.getAbsolutePath());
                continue;
            }
            results.add(new VoiceClipDefinition(file.getName(), file.getAbsolutePath(), 1));
        }
        results.sort(Comparator.comparing(VoiceClipDefinition::fileName, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    private static DetailScanResult scanEventDirectory(File eventDir) {
        List<VoiceClipDefinition> baseClips = scanAudioFiles(eventDir);
        Map<String, List<VoiceClipDefinition>> detailClips = new LinkedHashMap<>();
        if (!eventDir.exists() || !eventDir.isDirectory() || !isSafeChildDirectory(eventDir.getParentFile(), eventDir)) {
            return new DetailScanResult(baseClips, detailClips);
        }
        collectDetailDirectories(eventDir, eventDir, detailClips);
        return new DetailScanResult(baseClips, detailClips);
    }

    private static void collectDetailDirectories(File rootDir, File currentDir, Map<String, List<VoiceClipDefinition>> detailClips) {
        File[] children = currentDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isDirectory() || !isSafeChildDirectory(currentDir, child)) {
                continue;
            }
            String relativePath = rootDir.toPath().relativize(child.toPath()).toString().replace('\\', '/');
            List<VoiceClipDefinition> clips = scanAudioFiles(child);
            if (!clips.isEmpty()) {
                detailClips.put(relativePath, clips);
            }
            collectDetailDirectories(rootDir, child, detailClips);
        }
    }

    private static void normalizeManifest(VoicePackManifest manifest) {
        if (manifest.id != null) {
            manifest.id = manifest.id.trim();
        }
        if (manifest.displayName != null) {
            manifest.displayName = manifest.displayName.trim();
        }
        if (manifest.author != null && manifest.author.length() > 120) {
            manifest.author = manifest.author.substring(0, 120);
        }
        if (manifest.description != null && manifest.description.length() > 512) {
            manifest.description = manifest.description.substring(0, 512);
        }
        if (manifest.version != null && manifest.version.length() > 32) {
            manifest.version = manifest.version.substring(0, 32);
        }
    }

    private static boolean isSafeChildDirectory(File parent, File child) {
        return child != null && child.isDirectory() && isSafeChild(parent, child);
    }

    private static boolean isSafeChildFile(File parent, File child) {
        return child != null && child.isFile() && isSafeChild(parent, child);
    }

    private static boolean isSafeChild(File parent, File child) {
        if (parent == null || child == null) {
            return false;
        }
        try {
            if (Files.isSymbolicLink(child.toPath())) {
                return false;
            }
            String parentPath = parent.getCanonicalFile().toPath().normalize().toString();
            String childPath = child.getCanonicalFile().toPath().normalize().toString();
            return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
        } catch (Exception e) {
            LOGGER.warn("校验语音包路径失败: {}", child.getAbsolutePath(), e);
            return false;
        }
    }

    private record DetailScanResult(List<VoiceClipDefinition> baseClips,
                                    Map<String, List<VoiceClipDefinition>> detailClips) {
        private DetailScanResult {
            baseClips = baseClips == null ? List.of() : List.copyOf(baseClips);
            detailClips = detailClips == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detailClips));
        }
    }
}
