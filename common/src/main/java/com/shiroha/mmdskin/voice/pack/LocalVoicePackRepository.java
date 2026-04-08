package com.shiroha.mmdskin.voice.pack;

import com.shiroha.mmdskin.config.PathConstants;

import java.util.List;
import java.util.Optional;

public final class LocalVoicePackRepository {
    private static final LocalVoicePackRepository INSTANCE = new LocalVoicePackRepository();

    private volatile List<VoicePackDefinition> cachedPacks = List.of();

    private LocalVoicePackRepository() {
    }

    public static LocalVoicePackRepository getInstance() {
        return INSTANCE;
    }

    public synchronized List<VoicePackDefinition> refresh() {
        PathConstants.ensureVoicePackDir();
        cachedPacks = List.copyOf(VoicePackScanner.scan(PathConstants.getVoicePackDir()));
        return cachedPacks;
    }

    public List<VoicePackDefinition> loadVoicePacks() {
        if (cachedPacks.isEmpty()) {
            return refresh();
        }
        return cachedPacks;
    }

    public Optional<VoicePackDefinition> findById(String packId) {
        if (packId == null || packId.isBlank()) {
            return Optional.empty();
        }
        return loadVoicePacks().stream()
                .filter(pack -> packId.equals(pack.getId()))
                .findFirst();
    }
}
