package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.voice.VoiceEventContext;
import com.shiroha.mmdskin.voice.VoiceEventType;
import com.shiroha.mmdskin.voice.VoiceTargetType;
import com.shiroha.mmdskin.voice.VoiceUsageMode;
import com.shiroha.mmdskin.voice.config.VoicePackBindingsConfig;
import com.shiroha.mmdskin.voice.pack.LocalVoicePackRepository;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/** 文件职责：作为语音系统对外门面，协调事件入口与串行播放运行时。 */
public final class VoicePlaybackManager {
    private static final long SPEAKER_STATE_RETENTION_MILLIS = 60_000L;

    private static final Logger LOGGER = LogManager.getLogger();
    private static final VoicePlaybackManager INSTANCE = new VoicePlaybackManager();

    private final LocalVoicePackRepository repository;
    private final VoicePlaybackController playbackController;
    private final SpeakerEdgeStateBook speakerEdgeStateBook;

    private VoicePlaybackManager() {
        this.repository = LocalVoicePackRepository.getInstance();
        VoiceCooldownBook cooldownBook = new VoiceCooldownBook();
        ActiveVoiceRegistry activeVoiceRegistry = new ActiveVoiceRegistry();
        this.playbackController = new VoicePlaybackController(
                repository,
                cooldownBook,
                activeVoiceRegistry,
                this::createBindingResolver,
                VoiceAudioPlayer::new
        );
        this.speakerEdgeStateBook = new SpeakerEdgeStateBook();
    }

    public static VoicePlaybackManager getInstance() {
        return INSTANCE;
    }

    public void refreshVoicePacks() {
        if (!VoiceRuntimeSupport.isClientRuntimeReady()) {
            return;
        }
        repository.refresh();
    }

    public void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long nowMillis) {
        speakerEdgeStateBook.evictStaleSpeakers(nowMillis, SPEAKER_STATE_RETENTION_MILLIS);
        playbackController.tick(nowMillis);
    }

    public void onDisconnect() {
        playbackController.reset();
        speakerEdgeStateBook.clear();
    }

    public void onMaidFrame(LivingEntity entity, String maidId, String modelName) {
        if (entity == null || maidId == null) {
            return;
        }
        emitSpeakerEdgeEvents(
                VoiceTargetType.MAID,
                maidId,
                VoiceRuntimeSupport.normalizeModelName(modelName),
                null,
                entity.hurtTime > 0,
                entity.getHealth() <= 0.0f
        );
    }

    public void onMobFrame(LivingEntity entity, String entityTypeId, String modelName) {
        if (entity == null || entityTypeId == null || entityTypeId.isBlank()) {
            return;
        }
        emitSpeakerEdgeEvents(
                VoiceTargetType.MOB,
                entity.getUUID().toString(),
                VoiceRuntimeSupport.normalizeModelName(modelName),
                entityTypeId,
                entity.hurtTime > 0,
                entity.getHealth() <= 0.0f
        );
    }

    public void onLocalPlayerModelSwitched(String modelName) {
        emitLocalPlayerEvent(VoiceEventType.MODEL_SWITCH, VoiceUsageMode.NORMAL,
                VoiceRuntimeSupport.normalizeModelName(modelName), List.of());
    }

    public void onLocalPlayerCustomAction(String animId) {
        if (!VoiceRuntimeSupport.isClientRuntimeReady()) {
            return;
        }
        emitLocalPlayerEvent(VoiceEventType.CUSTOM_ACTION, VoiceUsageMode.CUSTOM_ACTION,
                VoiceRuntimeSupport.resolveLocalPlayerModelName(), List.of());
    }

    public void onMaidModelSwitched(String maidId, String modelName) {
        if (maidId == null) {
            return;
        }
        emit(new VoiceEventContext(
                VoiceRuntimeSupport.buildSpeakerKey(VoiceTargetType.MAID, maidId),
                VoiceTargetType.MAID,
                VoiceEventType.MODEL_SWITCH,
                VoiceUsageMode.NORMAL,
                VoiceRuntimeSupport.normalizeModelName(modelName),
                null,
                List.of()
        ));
    }

    public void onMaidCustomAction(String maidId) {
        if (maidId == null) {
            return;
        }
        emit(new VoiceEventContext(
                VoiceRuntimeSupport.buildSpeakerKey(VoiceTargetType.MAID, maidId),
                VoiceTargetType.MAID,
                VoiceEventType.CUSTOM_ACTION,
                VoiceUsageMode.CUSTOM_ACTION,
                VoiceRuntimeSupport.normalizeModelName(
                        MaidMMDModelManager.getBindingModelName(java.util.UUID.fromString(maidId))
                ),
                null,
                List.of()
        ));
    }

    public void onLocalPlayerStageStart(String modelName) {
        emitLocalPlayerEvent(VoiceEventType.STAGE_START, VoiceUsageMode.STAGE,
                VoiceRuntimeSupport.normalizeModelName(modelName), List.of());
    }

    public void onLocalPlayerStageEnd(String modelName) {
        emitLocalPlayerEvent(VoiceEventType.STAGE_END, VoiceUsageMode.STAGE,
                VoiceRuntimeSupport.normalizeModelName(modelName), List.of());
    }

    public void emit(VoiceEventContext context) {
        playbackController.emit(context);
    }

    private void emitSpeakerEdgeEvents(VoiceTargetType targetType,
                                       String identity,
                                       String modelName,
                                       String entityTypeId,
                                       boolean hurtActive,
                                       boolean deadActive) {
        String speakerKey = VoiceRuntimeSupport.buildSpeakerKey(targetType, identity);
        for (VoiceEventType eventType : speakerEdgeStateBook.update(speakerKey, hurtActive, deadActive)) {
            emit(new VoiceEventContext(
                    speakerKey,
                    targetType,
                    eventType,
                    VoiceUsageMode.NORMAL,
                    modelName,
                    entityTypeId,
                    List.of()
            ));
        }
    }

    private void emitLocalPlayerEvent(VoiceEventType eventType,
                                      VoiceUsageMode usageMode,
                                      String modelName,
                                      List<String> detailKeys) {
        if (!VoiceRuntimeSupport.isClientRuntimeReady()) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        emit(new VoiceEventContext(
                VoiceRuntimeSupport.buildSpeakerKey(VoiceTargetType.PLAYER, minecraft.player.getUUID().toString()),
                VoiceTargetType.PLAYER,
                eventType,
                usageMode,
                modelName,
                null,
                detailKeys
        ));
    }

    private VoiceBindingResolver createBindingResolver() {
        try {
            return new VoiceBindingResolver(VoicePackBindingsConfig.getInstance());
        } catch (Throwable throwable) {
            LOGGER.debug("语音包绑定尚未初始化，跳过本次语音事件", throwable);
            return null;
        }
    }
}
