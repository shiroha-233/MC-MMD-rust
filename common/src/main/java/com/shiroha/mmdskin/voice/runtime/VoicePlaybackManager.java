package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.voice.VoiceEventContext;
import com.shiroha.mmdskin.voice.VoiceEventType;
import com.shiroha.mmdskin.voice.VoiceTargetType;
import com.shiroha.mmdskin.voice.VoiceUsageMode;
import com.shiroha.mmdskin.voice.config.VoicePackBindingsConfig;
import com.shiroha.mmdskin.voice.pack.LocalVoicePackRepository;
import com.shiroha.mmdskin.voice.pack.VoiceClipDefinition;
import com.shiroha.mmdskin.voice.pack.VoiceEventBinding;
import com.shiroha.mmdskin.voice.pack.VoicePackDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class VoicePlaybackManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final VoicePlaybackManager INSTANCE = new VoicePlaybackManager();

    private final LocalVoicePackRepository repository = LocalVoicePackRepository.getInstance();
    private final Map<String, ActiveVoice> activeVoices = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, SpeakerFlags> speakerFlags = new HashMap<>();

    private VoicePlaybackManager() {
    }

    public static VoicePlaybackManager getInstance() {
        return INSTANCE;
    }

    public void refreshVoicePacks() {
        if (!isClientRuntimeReady()) {
            return;
        }
        repository.refresh();
    }

    public void tick() {
        List<String> finished = new ArrayList<>();
        for (Map.Entry<String, ActiveVoice> entry : activeVoices.entrySet()) {
            if (!entry.getValue().player.isPlaying()) {
                entry.getValue().player.cleanup();
                finished.add(entry.getKey());
            }
        }
        for (String key : finished) {
            activeVoices.remove(key);
        }
    }

    public void onDisconnect() {
        for (ActiveVoice activeVoice : activeVoices.values()) {
            activeVoice.player.cleanup();
        }
        activeVoices.clear();
        cooldowns.clear();
        speakerFlags.clear();
    }

    public void onMaidFrame(LivingEntity entity, String maidId, String modelName) {
        if (entity == null || maidId == null) {
            return;
        }
        String speakerKey = buildSpeakerKey(VoiceTargetType.MAID, maidId);
        SpeakerFlags flags = speakerFlags.computeIfAbsent(speakerKey, key -> new SpeakerFlags());
        if (entity.hurtTime > 0 && !flags.hurtActive) {
            emit(new VoiceEventContext(speakerKey, VoiceTargetType.MAID, VoiceEventType.HURT,
                    VoiceUsageMode.NORMAL, normalizeModelName(modelName), null, List.of()));
        }
        if (entity.getHealth() <= 0.0f && !flags.deadActive) {
            emit(new VoiceEventContext(speakerKey, VoiceTargetType.MAID, VoiceEventType.DEATH,
                    VoiceUsageMode.NORMAL, normalizeModelName(modelName), null, List.of()));
        }
        flags.hurtActive = entity.hurtTime > 0;
        flags.deadActive = entity.getHealth() <= 0.0f;
    }

    public void onMobFrame(LivingEntity entity, String entityTypeId, String modelName) {
        if (entity == null || entityTypeId == null || entityTypeId.isBlank()) {
            return;
        }
        String speakerKey = buildSpeakerKey(VoiceTargetType.MOB, entity.getUUID().toString());
        SpeakerFlags flags = speakerFlags.computeIfAbsent(speakerKey, key -> new SpeakerFlags());
        if (entity.hurtTime > 0 && !flags.hurtActive) {
            emit(new VoiceEventContext(speakerKey, VoiceTargetType.MOB, VoiceEventType.HURT,
                    VoiceUsageMode.NORMAL, normalizeModelName(modelName), entityTypeId, List.of()));
        }
        if (entity.getHealth() <= 0.0f && !flags.deadActive) {
            emit(new VoiceEventContext(speakerKey, VoiceTargetType.MOB, VoiceEventType.DEATH,
                    VoiceUsageMode.NORMAL, normalizeModelName(modelName), entityTypeId, List.of()));
        }
        flags.hurtActive = entity.hurtTime > 0;
        flags.deadActive = entity.getHealth() <= 0.0f;
    }

    public void onLocalPlayerModelSwitched(String modelName) {
        if (!isClientRuntimeReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        emit(new VoiceEventContext(buildSpeakerKey(VoiceTargetType.PLAYER, minecraft.player.getUUID().toString()),
                VoiceTargetType.PLAYER, VoiceEventType.MODEL_SWITCH, VoiceUsageMode.NORMAL,
                normalizeModelName(modelName), null, List.of()));
    }

    public void onLocalPlayerCustomAction(String animId) {
        if (!isClientRuntimeReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        emit(new VoiceEventContext(buildSpeakerKey(VoiceTargetType.PLAYER, minecraft.player.getUUID().toString()),
                VoiceTargetType.PLAYER, VoiceEventType.CUSTOM_ACTION, VoiceUsageMode.CUSTOM_ACTION,
                resolvePlayerModelName(minecraft.player), null, List.of()));
    }

    public void onMaidModelSwitched(String maidId, String modelName) {
        if (maidId == null) {
            return;
        }
        emit(new VoiceEventContext(buildSpeakerKey(VoiceTargetType.MAID, maidId), VoiceTargetType.MAID,
                VoiceEventType.MODEL_SWITCH, VoiceUsageMode.NORMAL, normalizeModelName(modelName), null, List.of()));
    }

    public void onMaidCustomAction(String maidId) {
        if (maidId == null) {
            return;
        }
        emit(new VoiceEventContext(buildSpeakerKey(VoiceTargetType.MAID, maidId), VoiceTargetType.MAID,
                VoiceEventType.CUSTOM_ACTION, VoiceUsageMode.CUSTOM_ACTION,
                normalizeModelName(MaidMMDModelManager.getBindingModelName(java.util.UUID.fromString(maidId))), null, List.of()));
    }

    public void onLocalPlayerStageStart(String modelName) {
        if (!isClientRuntimeReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        emit(new VoiceEventContext(buildSpeakerKey(VoiceTargetType.PLAYER, minecraft.player.getUUID().toString()),
                VoiceTargetType.PLAYER, VoiceEventType.STAGE_START, VoiceUsageMode.STAGE,
                normalizeModelName(modelName), null, List.of()));
    }

    public void onLocalPlayerStageEnd(String modelName) {
        if (!isClientRuntimeReady()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        emit(new VoiceEventContext(buildSpeakerKey(VoiceTargetType.PLAYER, minecraft.player.getUUID().toString()),
                VoiceTargetType.PLAYER, VoiceEventType.STAGE_END, VoiceUsageMode.STAGE,
                normalizeModelName(modelName), null, List.of()));
    }

    public void emit(VoiceEventContext context) {
        if (context == null) {
            return;
        }
        if (!isClientRuntimeReady()) {
            return;
        }
        VoiceBindingResolver bindingResolver = createBindingResolver();
        if (bindingResolver == null) {
            return;
        }
        String packId = bindingResolver.resolvePackId(context.targetType(), context.modelName(), context.entityTypeId(), context.usageMode());
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
        if (!tryAcquireCooldown(context.speakerKey(), context.eventType(), binding.cooldownTicks(), pack.getManifest().playback.globalCooldownTicks)) {
            return;
        }
        VoiceClipDefinition clip = selectClip(candidateClips);
        if (clip == null) {
            return;
        }
        ActiveVoice current = activeVoices.get(context.speakerKey());
        if (current != null && current.player.isPlaying() && current.priority > binding.priority()) {
            return;
        }
        if (current != null) {
            current.player.cleanup();
        }
        VoiceAudioPlayer player = new VoiceAudioPlayer();
        float volume = pack.getManifest().defaultVolume;
        if (!player.load(clip.filePath(), volume)) {
            player.cleanup();
            return;
        }
        player.play();
        activeVoices.put(context.speakerKey(), new ActiveVoice(player, binding.priority()));
    }

    private boolean tryAcquireCooldown(String speakerKey, VoiceEventType eventType, int eventCooldownTicks, int globalCooldownTicks) {
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(eventCooldownTicks, globalCooldownTicks) * 50L;
        String key = speakerKey + "#" + eventType.name();
        Long lastPlayed = cooldowns.get(key);
        if (lastPlayed != null && now - lastPlayed < cooldownMillis) {
            return false;
        }
        cooldowns.put(key, now);
        return true;
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

    private static String resolvePlayerModelName(AbstractClientPlayer player) {
        boolean local = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(player.getUUID());
        return normalizeModelName(PlayerModelSyncManager.getPlayerModel(player.getUUID(), player.getName().getString(), local));
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

    private VoiceBindingResolver createBindingResolver() {
        try {
            return new VoiceBindingResolver(VoicePackBindingsConfig.getInstance());
        } catch (Throwable throwable) {
            LOGGER.debug("语音包绑定尚未初始化，跳过本次语音事件", throwable);
            return null;
        }
    }

    private static boolean isClientRuntimeReady() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft != null && minecraft.gameDirectory != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }
        return modelName;
    }

    private static String buildSpeakerKey(VoiceTargetType type, String identity) {
        return type.name().toLowerCase() + ':' + identity;
    }

    private record ActiveVoice(VoiceAudioPlayer player, int priority) {
    }

    private static final class SpeakerFlags {
        private boolean hurtActive;
        private boolean deadActive;
        private boolean attackActive;
    }
}
