package com.shiroha.mmdskin.voice.pack;

import com.shiroha.mmdskin.voice.VoiceEventType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class VoicePackDefinition {
    private final String id;
    private final String displayName;
    private final String folderPath;
    private final VoicePackManifest manifest;
    private final Map<VoiceEventType, VoiceEventBinding> eventBindings;

    public VoicePackDefinition(String id,
                               String displayName,
                               String folderPath,
                               VoicePackManifest manifest,
                               Map<VoiceEventType, VoiceEventBinding> eventBindings) {
        this.id = id;
        this.displayName = displayName;
        this.folderPath = folderPath;
        this.manifest = manifest;
        this.eventBindings = Collections.unmodifiableMap(new EnumMap<>(eventBindings));
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public VoicePackManifest getManifest() {
        return manifest;
    }

    public Map<VoiceEventType, VoiceEventBinding> getEventBindings() {
        return eventBindings;
    }

    public VoiceEventBinding getBinding(VoiceEventType eventType) {
        return eventBindings.get(eventType);
    }

    public List<VoiceClipDefinition> resolveClips(VoiceEventType eventType, List<String> detailKeys) {
        VoiceEventBinding binding = getBinding(eventType);
        if (binding == null) {
            return List.of();
        }
        if (detailKeys != null && binding.detailClips() != null) {
            for (String detailKey : detailKeys) {
                if (detailKey == null || detailKey.isBlank()) {
                    continue;
                }
                List<VoiceClipDefinition> clips = binding.detailClips().get(detailKey);
                if (clips != null && !clips.isEmpty()) {
                    return clips;
                }
            }
        }
        return binding.clips();
    }
}
