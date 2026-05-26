package com.shiroha.mmdskin.neoforge.register;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge 数据附件注册
 * 用于在实体上存储 MMD 模型信息，支持自动同步和持久化
 */
public class MmdSkinAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "mmdskin");

    /** 存储女仆实体的 MMD 模型名称 */
    public static final Supplier<AttachmentType<String>> MAID_MMD_MODEL = ATTACHMENT_TYPES.register(
            "maid_mmd_model", () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build()
    );

    /** 存储玩家实体的 MMD 模型名称（用于服务端持久化和同步） */
    public static final Supplier<AttachmentType<String>> PLAYER_MMD_MODEL = ATTACHMENT_TYPES.register(
            "player_mmd_model", () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build()
    );
}
