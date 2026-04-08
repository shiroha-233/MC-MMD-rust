package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceTargetType;
import com.shiroha.mmdskin.voice.VoiceUsageMode;
import com.shiroha.mmdskin.voice.config.VoicePackBindingsConfig;

public final class VoiceBindingResolver {
    private final VoicePackBindingsConfig config;

    public VoiceBindingResolver(VoicePackBindingsConfig config) {
        this.config = config;
    }

    public String resolvePackId(VoiceTargetType targetType,
                                String modelName,
                                String entityTypeId,
                                VoiceUsageMode usageMode) {
        if (targetType == null) {
            return null;
        }
        return switch (targetType) {
            case PLAYER -> resolvePlayerPackId(modelName, usageMode);
            case MAID -> resolveMaidPackId(modelName, usageMode);
            case MOB -> resolveMobPackId(entityTypeId);
        };
    }

    private String resolvePlayerPackId(String modelName, VoiceUsageMode usageMode) {
        String usage = config.getPlayerUsagePackId(usageMode);
        if (usage != null) {
            return usage;
        }
        String model = config.getPlayerModelPackId(modelName);
        if (model != null) {
            return model;
        }
        return config.getPlayerDefaultPackId();
    }

    private String resolveMaidPackId(String modelName, VoiceUsageMode usageMode) {
        String usage = config.getMaidUsagePackId(usageMode);
        if (usage != null) {
            return usage;
        }
        String model = config.getMaidModelPackId(modelName);
        if (model != null) {
            return model;
        }
        return config.getMaidDefaultPackId();
    }

    private String resolveMobPackId(String entityTypeId) {
        String entityTypePack = config.getMobEntityTypePackId(entityTypeId);
        if (entityTypePack != null) {
            return entityTypePack;
        }
        return config.getMobDefaultPackId();
    }
}
