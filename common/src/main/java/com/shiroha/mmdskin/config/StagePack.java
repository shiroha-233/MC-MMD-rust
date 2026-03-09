package com.shiroha.mmdskin.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 舞台包数据类
 */

public class StagePack {
    private static final Logger logger = LogManager.getLogger();

    @FunctionalInterface
    public interface VmdFileInspector {

        boolean[] inspect(String filePath);
    }

    private final String name;
    private final String folderPath;
    private final List<VmdFileInfo> vmdFiles;
    private final List<AudioFileInfo> audioFiles;

    public StagePack(String name, String folderPath, List<VmdFileInfo> vmdFiles, List<AudioFileInfo> audioFiles) {
        this.name = name;
        this.folderPath = folderPath;
        this.vmdFiles = Collections.unmodifiableList(vmdFiles);
        this.audioFiles = Collections.unmodifiableList(audioFiles);
    }

    public String getName() { return name; }
    public String getFolderPath() { return folderPath; }
    public List<VmdFileInfo> getVmdFiles() { return vmdFiles; }
    public List<AudioFileInfo> getAudioFiles() { return audioFiles; }

    public boolean hasMotionVmd() {
        for (VmdFileInfo info : vmdFiles) {
            if (info.hasBones || info.hasMorphs) return true;
        }
        return false;
    }

    public boolean hasCameraVmd() {
        for (VmdFileInfo info : vmdFiles) {
            if (info.hasCamera) return true;
        }
        return false;
    }

    public boolean hasAudio() {
        return !audioFiles.isEmpty();
    }

    public String getFirstAudioPath() {
        return audioFiles.isEmpty() ? null : audioFiles.get(0).path;
    }

    public static List<StagePack> scan(File stageAnimDir, VmdFileInspector inspector) {
        List<StagePack> packs = new ArrayList<>();
        if (!stageAnimDir.exists() || !stageAnimDir.isDirectory()) return packs;

        File[] subDirs = stageAnimDir.listFiles(File::isDirectory);
        if (subDirs == null) return packs;

        for (File dir : subDirs) {
            List<VmdFileInfo> files = scanVmdFiles(dir, inspector);
            List<AudioFileInfo> audios = scanAudioFiles(dir);
            if (!files.isEmpty()) {
                packs.add(new StagePack(dir.getName(), dir.getAbsolutePath(), files, audios));
            }
        }

        packs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        return packs;
    }

    private static List<VmdFileInfo> scanVmdFiles(File dir, VmdFileInspector inspector) {
        List<VmdFileInfo> results = new ArrayList<>();

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(PathConstants.VMD_EXTENSION));
        if (files == null) return results;

        for (File file : files) {
            boolean[] info = inspector.inspect(file.getAbsolutePath());
            if (info == null) continue;

            results.add(new VmdFileInfo(file.getName(), file.getAbsolutePath(), info[0], info[1], info[2]));
        }

        results.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return results;
    }

    private static List<AudioFileInfo> scanAudioFiles(File dir) {
        List<AudioFileInfo> results = new ArrayList<>();

        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            for (String ext : PathConstants.AUDIO_EXTENSIONS) {
                if (lower.endsWith(ext)) return true;
            }
            return false;
        });
        if (files == null) return results;

        for (File file : files) {
            String ext = "";
            int dot = file.getName().lastIndexOf('.');
            if (dot >= 0) ext = file.getName().substring(dot + 1).toUpperCase();
            results.add(new AudioFileInfo(file.getName(), file.getAbsolutePath(), ext));
        }

        results.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return results;
    }

    public static class AudioFileInfo {
        public final String name;
        public final String path;
        public final String format;

        public AudioFileInfo(String name, String path, String format) {
            this.name = name;
            this.path = path;
            this.format = format;
        }
    }

    public static class VmdFileInfo {
        public final String name;
        public final String path;
        public final boolean hasCamera;
        public final boolean hasBones;
        public final boolean hasMorphs;

        public VmdFileInfo(String name, String path, boolean hasCamera, boolean hasBones, boolean hasMorphs) {
            this.name = name;
            this.path = path;
            this.hasCamera = hasCamera;
            this.hasBones = hasBones;
            this.hasMorphs = hasMorphs;
        }

        public String getTypeTag() {
            StringBuilder sb = new StringBuilder();
            if (hasCamera) sb.append("\uD83D\uDCF7");
            if (hasBones) sb.append("\uD83E\uDDB4");
            if (hasMorphs) sb.append("\uD83D\uDE0A");
            return sb.toString();
        }
    }
}
