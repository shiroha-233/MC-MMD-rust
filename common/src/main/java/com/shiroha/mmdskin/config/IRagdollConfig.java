/* 文件职责：定义死亡布娃娃与尸体保留策略的全局配置读取入口。 */
package com.shiroha.mmdskin.config;

/**
 * 死亡布娃娃配置子接口。
 *
 * <p>语义约定：尸体是"每次死亡一条独立记录"，与实体身份解耦（玩家复活后 UUID 不变，
 * 不能用 UUID 做尸体键）。保留策略三选一，且始终受 {@link #getMaxCorpses()} 上限约束。
 */
public interface IRagdollConfig {

    /** 总开关：死亡时启用物理布娃娃。 */
    default boolean isRagdollEnabled() {
        return true;
    }

    /** 尸体保留模式：NEARBY（玩家在半径内保留）/ SECONDS（定时）/ FOREVER（永久，仍受数量上限）。 */
    default String getCorpseKeepMode() {
        return "NEARBY";
    }

    /** NEARBY 模式保留半径（方块）。 */
    default double getCorpseKeepRadius() {
        return 48.0;
    }

    /** SECONDS 模式保留时长（秒）。 */
    default long getCorpseKeepSeconds() {
        return 60;
    }

    /** 尸体最大并发数，超限按死亡时间从早到晚逐出。 */
    default int getMaxCorpses() {
        return 2;
    }

    /** 关节限位强度（1.35 = 标准手感；越大越硬，影响 Bullet 限位 ERP/CFM）。 */
    default float getRagdollJointStrength() {
        return 1.35f;
    }

    /** 有 MMD 尸体时隐藏原版死亡白烟。 */
    default boolean isHideDeathPoof() {
        return true;
    }

    /** 劫持死亡界面：透明死亡屏 + 自动第三人称（观战相机接线在渲染层 mixin）。 */
    default boolean isHijackDeathScreen() {
        return true;
    }

    /** 死亡动画播放时长（秒，之后布娃娃从当前姿势接手；0 = 立即布娃娃）。 */
    default float getRagdollDeathAnimSeconds() {
        return 0.0f;
    }

    /**
     * 尸体物理保持时长（秒）：启物理后经过该时长冻结物理（姿势保持、跳过步进，
     * 性能优化）；0 = 永久模拟直到尸体被移除。
     */
    default int getRagdollPhysicsKeepSeconds() {
        return 60;
    }
}
