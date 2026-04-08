package com.shiroha.mmdskin.voice.pack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VoicePackManifest {
    public int schemaVersion = 1;
    public String id;
    public String displayName;
    public String author = "";
    public String description = "";
    public String version = "1.0.0";
    public List<String> supportedTargets = List.of("player", "maid", "mob");
    public float defaultVolume = 1.0f;
    public Playback playback = new Playback();
    public Map<String, EventSpec> events = new LinkedHashMap<>();

    public static class Playback {
        public int globalCooldownTicks = 8;
        public int maxSimultaneousPerSpeaker = 1;
        public String interruptPolicy = "higher_priority_only";
    }

    public static class EventSpec {
        public String folder;
        public int cooldownTicks = 0;
        public int priority = 0;
        public String mode = "weighted_random";
    }
}
