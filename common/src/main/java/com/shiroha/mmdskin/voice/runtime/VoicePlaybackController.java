package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventContext;
import com.shiroha.mmdskin.voice.VoiceTargetType;
import com.shiroha.mmdskin.voice.pack.LocalVoicePackRepository;
import com.shiroha.mmdskin.voice.pack.VoiceClipDefinition;
import com.shiroha.mmdskin.voice.pack.VoiceEventBinding;
import com.shiroha.mmdskin.voice.pack.VoicePackDefinition;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** 文件职责：串行执行语音事件解析、冷却判定与播放切换。 */
final class VoicePlaybackController {
    private static final long SPEAKER_STATE_RETENTION_MILLIS = 60_000L;

    private final LocalVoicePackRepository repository;
    private final VoiceCooldownBook cooldownBook;
    private final ActiveVoiceRegistry activeVoiceRegistry;
    private final Supplier<VoiceBindingResolver> bindingResolverSupplier;
    private final Supplier<VoiceAudioPlayer> playerFactory;

    VoicePlaybackController(LocalVoicePackRepository repository,
                            VoiceCooldownBook cooldownBook,
                            ActiveVoiceRegistry activeVoiceRegistry,
                            Supplier<VoiceBindingResolver> bindingResolverSupplier,
                            Supplier<VoiceAudioPlayer> playerFactory) {
        this.repository = repository;
        this.cooldownBook = cooldownBook;
        this.activeVoiceRegistry = activeVoiceRegistry;
        this.bindingResolverSupplier = bindingResolverSupplier;
        this.playerFactory = playerFactory;
    }

    void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long nowMillis) {
        cooldownBook.evictStaleSpeakers(nowMillis, SPEAKER_STATE_RETENTION_MILLIS);
        activeVoiceRegistry.cleanupFinished();
    }

    void reset() {
        activeVoiceRegistry.stopAll();
        cooldownBook.clear();
    }

    void emit(VoiceEventContext context) {
        if (context == null || !VoiceRuntimeSupport.isClientRuntimeReady()) {
            return;
        }
        VoiceBindingResolver bindingResolver = bindingResolverSupplier.get();
        if (bindingResolver == null) {
            return;
        }
        String packId = bindingResolver.resolvePackId(
                context.targetType(),
                context.modelName(),
                context.entityTypeId(),
                context.usageMode()
        );
        if (packId == null) {
            return;
        }
        Optional<VoicePackDefinition> packOptional = repository.findById(packId);
        if (packOptional.isEmpty()) {
            return;
        }
        VoicePackDefinition pack = packOptional.get();
        if (!supportsTarget(pack, context.targetType())) {
            return;
        }
        VoiceEventBinding binding = pack.getBinding(context.eventType());
        if (binding == null) {
            return;
        }
        List<VoiceClipDefinition> candidateClips = pack.resolveClips(context.eventType(), context.detailKeys());
        if (candidateClips.isEmpty()) {
            return;
        }
        if (!cooldownBook.tryAcquire(
                context.speakerKey(),
                context.eventType(),
                binding.cooldownTicks(),
                pack.getManifest().playback.globalCooldownTicks
        )) {
            return;
        }
        VoiceClipDefinition clip = selectClip(candidateClips);
        if (clip == null) {
            return;
        }
        ActiveVoiceRegistry.ActiveVoice currentVoice = activeVoiceRegistry.get(context.speakerKey());
        if (currentVoice != null && currentVoice.player().isPlaying() && currentVoice.priority() > binding.priority()) {
            return;
        }
        if (currentVoice != null) {
            activeVoiceRegistry.removeAndCleanup(context.speakerKey());
        }
        VoiceAudioPlayer player = playerFactory.get();
        float volume = pack.getManifest().defaultVolume;
        if (!player.load(clip.filePath(), volume)) {
            player.cleanup();
            return;
        }
        player.play();
        activeVoiceRegistry.replace(context.speakerKey(), player, binding.priority());
    }

    private static boolean supportsTarget(VoicePackDefinition pack, VoiceTargetType targetType) {
        if (pack == null || targetType == null || pack.getManifest().supportedTargets == null || pack.getManifest().supportedTargets.isEmpty()) {
            return true;
        }
        String expected = targetType.name().toLowerCase();
        for (String target : pack.getManifest().supportedTargets) {
            if (target != null && expected.equals(target.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static VoiceClipDefinition selectClip(List<VoiceClipDefinition> clips) {
        if (clips.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (VoiceClipDefinition clip : clips) {
            totalWeight += Math.max(1, clip.weight());
        }
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (VoiceClipDefinition clip : clips) {
            cursor += Math.max(1, clip.weight());
            if (random < cursor) {
                return clip;
            }
        }
        return clips.get(0);
    }
}
