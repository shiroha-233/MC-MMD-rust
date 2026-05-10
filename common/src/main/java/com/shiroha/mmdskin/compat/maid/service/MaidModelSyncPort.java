/** 文件职责：定义女仆模型选择结果的网络同步端口。 */
package com.shiroha.mmdskin.compat.maid.service;

public interface MaidModelSyncPort {
    void syncMaidModel(int entityId, String modelName);
}
