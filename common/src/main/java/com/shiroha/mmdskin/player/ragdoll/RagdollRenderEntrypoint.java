/* 文件职责：把尸体渲染与布娃娃状态推进从主渲染/主 tick 循环里隔离出去。 */
package com.shiroha.mmdskin.player.ragdoll;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 尸体布娃娃统一入口。
 *
 * <p>26.3 的尸体不再由 {@code LevelRenderer} 的 TAIL 注入点直接绘制，而是和其它 MMD 模型
 * 一样走 {@code Frame Queue → MmdGpuDrawDevice}；本类负责把"状态推进"与"渲染贡献"两件事
 * 从主循环里隔离出来，任何异常都不允许波及主渲染循环，因此这里兜底 try-catch
 * （只告警前几次，防刷屏）。
 */
public final class RagdollRenderEntrypoint {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_FAILURE_LOGS = 5;
    private static int failureCount;

    private RagdollRenderEntrypoint() {
    }

    /** 客户端 tick：模型接管、保留策略逐出、物理启停与冻结。 */
    public static void tick(Minecraft minecraft) {
        try {
            RagdollManager.getInstance().tick(minecraft);
        } catch (Throwable error) {
            report("尸体状态推进异常", error);
        }
    }

    /**
     * 尸体渲染贡献：把已就绪的尸体转换成 26.3 的 Frame Queue 请求。
     *
     * @param cameraX/cameraY/cameraZ 相机世界坐标（26.3 的模型矩阵是相机相对的）
     */
    public static void contribute(double cameraX, double cameraY, double cameraZ,
                                  long frameId, float deltaSeconds) {
        if (RagdollManager.getInstance().corpseCount() == 0) {
            return;
        }
        try {
            RagdollManager.getInstance().contribute(cameraX, cameraY, cameraZ, frameId, deltaSeconds);
        } catch (Throwable error) {
            report("尸体渲染异常", error);
        }
    }

    private static void report(String message, Throwable error) {
        if (failureCount < MAX_FAILURE_LOGS) {
            failureCount++;
            LOGGER.warn("{}（累计 {} 次）", message, failureCount, error);
        }
    }
}
