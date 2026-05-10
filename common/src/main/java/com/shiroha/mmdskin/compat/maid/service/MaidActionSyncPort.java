/** 文件职责：定义女仆动作选择结果的网络同步端口。 */
package com.shiroha.mmdskin.compat.maid.service;

public interface MaidActionSyncPort {
    void syncMaidAction(int entityId, String animId);
}
