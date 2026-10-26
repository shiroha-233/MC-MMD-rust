package com.shiroha.mmdskin.neoforge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.config.GameDirectoryProvider;
import com.shiroha.mmdskin.neoforge.register.MmdSkinAttachments;
import com.shiroha.mmdskin.neoforge.register.MmdSkinRegisterCommon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge 平台主入口。
 *
 * <p>26.3 适配要点：
 * <ul>
 *   <li>不再使用 Architectury 的 {@code @ExpectPlatform} / 事件转发，
 *       全部改成 NeoForge 原生的 {@code @Mod} + 事件总线。</li>
 *   <li>游戏目录用 {@code FMLPaths.GAMEDIR} 注入：
 *       房主（客户端兼服务端）与服主（专用服务器）都能拿到正确的游戏根目录。</li>
 * </ul>
 */
@Mod(MmdSkin.MOD_ID)
public class MmdSkinNeoForge {
    public static final Logger logger = LogManager.getLogger();

    public MmdSkinNeoForge(IEventBus modEventBus) {
        // 游戏目录注入：房主（客户端兼服务端）与服主（专用服务器）都从这里拿，
        // 专用服务器上 Minecraft.getInstance() 是 null，不能用客户端那套取法。
        GameDirectoryProvider.setProvider(() -> FMLPaths.GAMEDIR.get().toFile());
        modEventBus.addListener(MmdSkinRegisterCommon::onRegisterPayloadHandlers);
        MmdSkinAttachments.ATTACHMENT_TYPES.register(modEventBus);
        MmdSkinRegisterCommon.Register();
    }
}
