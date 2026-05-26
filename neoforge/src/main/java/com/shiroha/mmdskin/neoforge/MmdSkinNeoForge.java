package com.shiroha.mmdskin.neoforge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.neoforge.register.MmdSkinAttachments;
import com.shiroha.mmdskin.neoforge.register.MmdSkinRegisterCommon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(MmdSkin.MOD_ID)
public class MmdSkinNeoForge {
    public static final Logger logger = LogManager.getLogger();

    public MmdSkinNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::preInit);
        modEventBus.addListener(MmdSkinRegisterCommon::onRegisterPayloadHandlers);
        MmdSkinAttachments.ATTACHMENT_TYPES.register(modEventBus);
    }

    public void preInit(FMLCommonSetupEvent event) {
        MmdSkinRegisterCommon.Register();
    }
}
