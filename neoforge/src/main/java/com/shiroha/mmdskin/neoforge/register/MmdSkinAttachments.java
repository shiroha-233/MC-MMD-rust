package com.shiroha.mmdskin.neoforge.register;

import java.util.function.Supplier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge 数据附件注册
 * 用于在实体上存储 MMD 模型信息，支持自动同步和持久化
 *
 * <p>26.3 适配：{@code AttachmentType.Builder#serialize} 不再接受
 * {@code com.mojang.serialization.Codec}，只接受 {@link IAttachmentSerializer}
 * 或 {@code MapCodec}，所以这里自己实现一个 String 附件的读写器。
 */
public class MmdSkinAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "mmdskin");

    /** 存储玩家实体的 MMD 模型名称（用于服务端持久化和同步） */
    public static final Supplier<AttachmentType<String>> PLAYER_MMD_MODEL = ATTACHMENT_TYPES.register(
            "player_mmd_model",
            () -> AttachmentType.builder(() -> "")
                    .serialize(new IAttachmentSerializer<String>() {
                        @Override
                        public String read(IAttachmentHolder holder, ValueInput input) {
                            return input.getStringOr("model", "");
                        }

                        @Override
                        public boolean write(String attachment, ValueOutput output) {
                            output.putString("model", attachment == null ? "" : attachment);
                            return true;
                        }
                    })
                    .build()
    );
}
