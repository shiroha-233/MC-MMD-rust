/* 文件职责：本地「指定玩家模型」的一条记录（玩家名 + UUID + 模型名）。 */
package com.shiroha.mmdskin.player.sync;

import java.util.Locale;
import java.util.UUID;

/**
 * 一条本地覆盖记录：我在自己的屏幕上，把某个玩家渲染成某个模型。
 *
 * <p><b>完全本地</b>：这条记录只存在于你自己的客户端里，既不写进服务器，
 * 也不会影响对方（或者别的任何人）的客户端。别人怎么渲染，由别人自己决定。
 *
 * <p><b>键设计</b>：主键是玩家名（归一化：去首尾空白 + 转小写），UUID 是辅助信息。
 * <ul>
 *   <li>玩家名做主键：Minecraft 同一时刻一个服务器内玩家名唯一，名字是最稳的业务标识；
 *       而 UUID 在离线模式 / 破译服务器上会随会话变化（同一个名字，每次进来 UUID 都不同）。</li>
 *   <li>UUID 仍然要存：它是"同一个人"的强证据。匹配时<b>名字优先</b>；名字没命中、
 *       UUID 命中时视为同一个人改了名，把记录里的名字更新成新名字（续接而不是重新指定）。</li>
 * </ul>
 *
 * @param name      玩家名（展示用，保留原始大小写）
 * @param uuid      最近一次见到的玩家 UUID；不知道时为 {@code null}
 * @param modelName 我希望他在我屏幕上被渲染成的模型名
 */
public record PlayerModelOverride(String name, UUID uuid, String modelName) {

    /** 名字归一化后的键：去首尾空白 + 转小写（用 ROOT locale，避免土耳其语 I 问题）。 */
    public static String normalizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().toLowerCase(Locale.ROOT);
    }

    /** 归一化后的键。 */
    public String key() {
        return normalizeName(name);
    }

    /** 是否是一条有效记录。 */
    public boolean valid() {
        return !key().isEmpty() && modelName != null && !modelName.isBlank();
    }
}
