package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceTargetType;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 文件职责：在主线程采集玩家语音事件所需的世界与角色快照。 */
final class PlayerVoiceSnapshotCollector {
    PlayerVoiceSnapshot collect(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        LocalPlayer player = minecraft.player;
        String speakerKey = VoiceRuntimeSupport.buildSpeakerKey(VoiceTargetType.PLAYER, player.getUUID().toString());
        String modelName = VoiceRuntimeSupport.resolvePlayerModelName(player, true);
        Map<String, Integer> effectAmplifiers = collectEffects(player);
        WeatherState weatherState = WeatherState.resolve(player);
        DayPhase dayPhase = DayPhase.resolve(player);
        String biomeId = resolveBiomeId(player);
        String dimensionId = player.level().dimension().location().toString();
        return new PlayerVoiceSnapshot(
                speakerKey,
                modelName,
                player.hurtTime > 0,
                player.swinging,
                player.getHealth() <= 0.0f,
                buildDeathDetailKeys(player),
                player.getFoodData().getFoodLevel(),
                effectAmplifiers,
                weatherState.configKey(),
                dayPhase.configKey(),
                biomeId,
                buildLocationDetailKeys(biomeId),
                dimensionId,
                buildLocationDetailKeys(dimensionId),
                resolveContainerType(minecraft.screen),
                minecraft.screen == null && isIdleCandidate(player)
        );
    }

    private static Map<String, Integer> collectEffects(LocalPlayer player) {
        Map<String, Integer> effects = new HashMap<>();
        for (MobEffectInstance effectInstance : player.getActiveEffects()) {
            ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect());
            if (effectId != null) {
                effects.put(effectId.toString(), effectInstance.getAmplifier());
            }
        }
        return effects;
    }

    private static String resolveBiomeId(LocalPlayer player) {
        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        return biomeHolder.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse(null);
    }

    private static List<String> buildLocationDetailKeys(String locationId) {
        ResourceLocation location = ResourceLocation.tryParse(locationId);
        if (location == null) {
            return List.of();
        }
        return VoiceRuntimeSupport.normalizeDetailKeys(List.of(
                VoiceRuntimeSupport.toDetailPath(location),
                location.getPath()
        ));
    }

    private static List<String> buildDeathDetailKeys(Player player) {
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
                detailKeys.add(VoiceRuntimeSupport.toDetailPath(damageTypeId));
                detailKeys.add(damageTypeId.getPath());
            }
            Entity directEntity = damageSource.getDirectEntity();
            if (directEntity != null) {
                ResourceLocation directTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(directEntity.getType());
                if (directTypeId != null) {
                    detailKeys.add("direct/" + VoiceRuntimeSupport.toDetailPath(directTypeId));
                }
            }
        }
        return VoiceRuntimeSupport.normalizeDetailKeys(detailKeys);
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
