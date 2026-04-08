package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.voice.VoiceEventContext;
import com.shiroha.mmdskin.voice.VoiceEventType;
import com.shiroha.mmdskin.voice.VoiceTargetType;
import com.shiroha.mmdskin.voice.VoiceUsageMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.shiroha.mmdskin.config.UIConstants.DEFAULT_MODEL_NAME;

public final class PlayerVoiceSceneObserver {
    private static final PlayerVoiceSceneObserver INSTANCE = new PlayerVoiceSceneObserver();
    private static final int HUNGER_LOW_THRESHOLD = 14;
    private static final int HUNGER_CRITICAL_THRESHOLD = 6;
    private static final int IDLE_START_TICKS = 20 * 15;
    private static final int IDLE_REPEAT_TICKS = 20 * 45;

    private int previousFoodLevel = -1;
    private HungerTier previousHungerTier = HungerTier.NONE;
    private final Map<String, Integer> activeEffectAmplifiers = new HashMap<>();
    private WeatherState previousWeather = WeatherState.UNKNOWN;
    private DayPhase previousDayPhase = DayPhase.UNKNOWN;
    private String previousBiomeId;
    private String previousDimensionId;
    private String previousContainerType;
    private boolean previousSwinging;
    private boolean previousHurt;
    private boolean previousDead;
    private int idleTicks;
    private int lastIdleEmitTick = -IDLE_REPEAT_TICKS;
    private int tickCounter;

    private PlayerVoiceSceneObserver() {
    }

    public static PlayerVoiceSceneObserver getInstance() {
        return INSTANCE;
    }

    public void tick(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset();
            return;
        }
        tickCounter++;

        VoicePlaybackManager playbackManager = VoicePlaybackManager.getInstance();
        playbackManager.refreshVoicePacks();

        String speakerKey = buildSpeakerKey(player);
        String modelName = resolveModelName(player);

        observeCombat(player, playbackManager, speakerKey, modelName);
        observeHunger(player, playbackManager, speakerKey, modelName);
        observeEffects(player, playbackManager, speakerKey, modelName);
        observeWeather(player, playbackManager, speakerKey, modelName);
        observeDayPhase(player, playbackManager, speakerKey, modelName);
        observeBiome(player, playbackManager, speakerKey, modelName);
        observeDimension(player, playbackManager, speakerKey, modelName);
        observeContainer(minecraft.screen, playbackManager, speakerKey, modelName);
        observeIdle(player, minecraft.screen, playbackManager, speakerKey, modelName);
    }

    public void onDisconnect() {
        reset();
    }

    private void observeCombat(LocalPlayer player, VoicePlaybackManager manager, String speakerKey, String modelName) {
        boolean hurt = player.hurtTime > 0;
        if (hurt && !previousHurt) {
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.HURT, List.of()));
        }

        boolean swinging = player.swinging;
        if (swinging && !previousSwinging) {
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.ATTACK, List.of()));
        }

        boolean dead = player.getHealth() <= 0.0f;
        if (dead && !previousDead) {
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.DEATH, buildDeathDetailKeys(player)));
        }

        previousHurt = hurt;
        previousSwinging = swinging;
        previousDead = dead;
    }

    private void observeHunger(LocalPlayer player, VoicePlaybackManager manager, String speakerKey, String modelName) {
        FoodData foodData = player.getFoodData();
        int currentFood = foodData.getFoodLevel();
        HungerTier currentTier = HungerTier.fromFoodLevel(currentFood);
        if (previousFoodLevel >= 0 && currentFood < previousFoodLevel && currentTier != previousHungerTier && currentTier != HungerTier.NONE) {
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.HUNGER, List.of(currentTier.configKey())));
        }
        previousFoodLevel = currentFood;
        previousHungerTier = currentTier;
    }

    private void observeEffects(LocalPlayer player, VoicePlaybackManager manager, String speakerKey, String modelName) {
        Map<String, Integer> currentEffects = new HashMap<>();
        for (MobEffectInstance effectInstance : player.getActiveEffects()) {
            ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect());
            if (effectId == null) {
                continue;
            }
            String key = effectId.toString();
            currentEffects.put(key, effectInstance.getAmplifier());
            Integer previousAmplifier = activeEffectAmplifiers.get(key);
            if (previousAmplifier == null || previousAmplifier != effectInstance.getAmplifier()) {
                List<String> detailKeys = new ArrayList<>();
                detailKeys.add(toDetailPath(effectId));
                detailKeys.add(effectId.getPath());
                detailKeys.add(toDetailPath(effectId) + "/amp_" + effectInstance.getAmplifier());
                manager.emit(baseContext(speakerKey, modelName, VoiceEventType.EFFECT_GAINED, detailKeys));
            }
        }
        activeEffectAmplifiers.clear();
        activeEffectAmplifiers.putAll(currentEffects);
    }

    private void observeWeather(LocalPlayer player, VoicePlaybackManager manager, String speakerKey, String modelName) {
        WeatherState currentWeather = WeatherState.resolve(player);
        if (currentWeather != previousWeather && currentWeather != WeatherState.UNKNOWN) {
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.WEATHER, List.of(currentWeather.configKey())));
        }
        previousWeather = currentWeather;
    }

    private void observeDayPhase(LocalPlayer player, VoicePlaybackManager manager, String speakerKey, String modelName) {
        DayPhase currentDayPhase = DayPhase.resolve(player);
        if (currentDayPhase != previousDayPhase && currentDayPhase != DayPhase.UNKNOWN) {
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.DAY_PHASE, List.of(currentDayPhase.configKey())));
        }
        previousDayPhase = currentDayPhase;
    }

    private void observeBiome(LocalPlayer player, VoicePlaybackManager manager, String speakerKey, String modelName) {
        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        String biomeId = biomeHolder.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse(null);
        if (biomeId != null && !biomeId.equals(previousBiomeId)) {
            List<String> detailKeys = new ArrayList<>();
            detailKeys.add(toDetailPath(ResourceLocation.tryParse(biomeId)));
            detailKeys.add(ResourceLocation.tryParse(biomeId) != null ? ResourceLocation.tryParse(biomeId).getPath() : biomeId);
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.BIOME_ENTER, detailKeys));
        }
        previousBiomeId = biomeId;
    }

    private void observeDimension(LocalPlayer player, VoicePlaybackManager manager, String speakerKey, String modelName) {
        String dimensionId = player.level().dimension().location().toString();
        if (!dimensionId.equals(previousDimensionId)) {
            ResourceLocation location = ResourceLocation.tryParse(dimensionId);
            List<String> detailKeys = new ArrayList<>();
            if (location != null) {
                detailKeys.add(toDetailPath(location));
                detailKeys.add(location.getPath());
            }
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.DIMENSION_ENTER, detailKeys));
        }
        previousDimensionId = dimensionId;
    }

    private void observeContainer(Screen screen, VoicePlaybackManager manager, String speakerKey, String modelName) {
        String containerType = resolveContainerType(screen);
        if (containerType != null && !containerType.equals(previousContainerType)) {
            manager.emit(baseContext(speakerKey, modelName, VoiceEventType.CONTAINER_OPEN, List.of(containerType)));
        }
        previousContainerType = containerType;
    }

    private void observeIdle(LocalPlayer player, Screen screen, VoicePlaybackManager manager, String speakerKey, String modelName) {
        if (screen == null && isIdleCandidate(player)) {
            idleTicks++;
            if (idleTicks >= IDLE_START_TICKS && tickCounter - lastIdleEmitTick >= IDLE_REPEAT_TICKS) {
                manager.emit(baseContext(speakerKey, modelName, VoiceEventType.IDLE,
                        List.of(previousWeather.configKey(), previousDayPhase.configKey())));
                lastIdleEmitTick = tickCounter;
            }
            return;
        }
        idleTicks = 0;
    }

    private VoiceEventContext baseContext(String speakerKey, String modelName, VoiceEventType eventType, List<String> detailKeys) {
        return new VoiceEventContext(speakerKey, VoiceTargetType.PLAYER, eventType, VoiceUsageMode.NORMAL,
                modelName, null, normalizeDetailKeys(detailKeys));
    }

    private List<String> buildDeathDetailKeys(Player player) {
        List<String> detailKeys = new ArrayList<>();
        DamageSource damageSource = player.getLastDamageSource();
        if (damageSource != null) {
            Component deathMessage = damageSource.getLocalizedDeathMessage(player);
            if (deathMessage.getContents() instanceof TranslatableContents translatableContents) {
                detailKeys.add(translatableContents.getKey());
            }
            ResourceLocation damageTypeId = damageSource.typeHolder().unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(null);
            if (damageTypeId != null) {
                detailKeys.add(toDetailPath(damageTypeId));
                detailKeys.add(damageTypeId.getPath());
            }
            Entity directEntity = damageSource.getDirectEntity();
            if (directEntity != null) {
                ResourceLocation directTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(directEntity.getType());
                if (directTypeId != null) {
                    detailKeys.add("direct/" + toDetailPath(directTypeId));
                }
            }
        }
        return normalizeDetailKeys(detailKeys);
    }

    private static List<String> normalizeDetailKeys(List<String> detailKeys) {
        if (detailKeys == null || detailKeys.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String detailKey : detailKeys) {
            if (detailKey == null || detailKey.isBlank()) {
                continue;
            }
            String normalized = detailKey.trim().replace('\\', '/').replace(':', '/');
            if (seen.add(normalized)) {
                results.add(normalized);
            }
            int slash = normalized.lastIndexOf('/');
            if (slash > 0) {
                String leaf = normalized.substring(slash + 1);
                if (seen.add(leaf)) {
                    results.add(leaf);
                }
            }
            int dot = normalized.lastIndexOf('.');
            while (dot > 0) {
                String parent = normalized.substring(0, dot);
                if (seen.add(parent)) {
                    results.add(parent);
                }
                dot = parent.lastIndexOf('.');
            }
        }
        return List.copyOf(results);
    }

    private static String resolveContainerType(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return null;
        }
        AbstractContainerMenu menu = containerScreen.getMenu();
        if (menu instanceof ChestMenu) {
            return "chest";
        }
        if (menu instanceof HopperMenu) {
            return "hopper";
        }
        if (menu instanceof DispenserMenu) {
            return "dispenser";
        }
        if (menu instanceof FurnaceMenu) {
            return "furnace";
        }
        return "generic_container";
    }

    private static boolean isIdleCandidate(LocalPlayer player) {
        return !player.swinging
                && player.hurtTime <= 0
                && !player.isUsingItem()
                && !player.isPassenger()
                && !player.isSwimming()
                && !player.onClimbable()
                && !player.isSleeping()
                && Math.abs(player.getX() - player.xo) < 0.001
                && Math.abs(player.getZ() - player.zo) < 0.001;
    }

    private static String resolveModelName(LocalPlayer player) {
        String modelName = PlayerModelSyncManager.getPlayerModel(player.getUUID(), player.getName().getString(), true);
        if (modelName == null || modelName.isBlank() || DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }
        return modelName;
    }

    private static String buildSpeakerKey(Player player) {
        return "player:" + player.getUUID();
    }

    private static String toDetailPath(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return id.getNamespace() + "/" + id.getPath();
    }

    private void reset() {
        previousFoodLevel = -1;
        previousHungerTier = HungerTier.NONE;
        activeEffectAmplifiers.clear();
        previousWeather = WeatherState.UNKNOWN;
        previousDayPhase = DayPhase.UNKNOWN;
        previousBiomeId = null;
        previousDimensionId = null;
        previousContainerType = null;
        previousSwinging = false;
        previousHurt = false;
        previousDead = false;
        idleTicks = 0;
        lastIdleEmitTick = -IDLE_REPEAT_TICKS;
        tickCounter = 0;
    }

    private enum HungerTier {
        NONE,
        LOW,
        CRITICAL;

        private static HungerTier fromFoodLevel(int foodLevel) {
            if (foodLevel <= HUNGER_CRITICAL_THRESHOLD) {
                return CRITICAL;
            }
            if (foodLevel <= HUNGER_LOW_THRESHOLD) {
                return LOW;
            }
            return NONE;
        }

        private String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private enum WeatherState {
        UNKNOWN,
        CLEAR,
        RAIN,
        THUNDER,
        SNOW;

        private static WeatherState resolve(LocalPlayer player) {
            BlockPos feet = player.blockPosition();
            BlockPos head = BlockPos.containing(feet.getX(), player.getBoundingBox().maxY, feet.getZ());
            if (!player.level().isRaining()) {
                return CLEAR;
            }
            if (player.level().isThundering()) {
                return THUNDER;
            }
            Biome.Precipitation precipitation = player.level().getBiome(feet).value().getPrecipitationAt(feet);
            if ((player.level().isRainingAt(feet) || player.level().isRainingAt(head)) && precipitation == Biome.Precipitation.SNOW) {
                return SNOW;
            }
            return RAIN;
        }

        private String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private enum DayPhase {
        UNKNOWN,
        FIXED_TIME,
        DAWN,
        DAY,
        DUSK,
        NIGHT;

        private static DayPhase resolve(LocalPlayer player) {
            if (player.level().dimensionType().fixedTime().isPresent()) {
                return FIXED_TIME;
            }
            long timeOfDay = player.level().getDayTime() % 24000L;
            if (timeOfDay < 1000L) {
                return DAWN;
            }
            if (timeOfDay < 12000L) {
                return DAY;
            }
            if (timeOfDay < 13000L) {
                return DUSK;
            }
            return NIGHT;
        }

        private String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
