// 负责验证帧队列的材质顺序、透明排序和异常后清理语义。
package com.shiroha.mmdskin.client.draw;

import com.shiroha.mmdskin.client.frame.MmdFrameUpdaterTest;
import com.shiroha.mmdskin.config.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameRenderQueueTest {
    @Test
    void shouldDrawOpaqueFirstAndTransparentBackToFront() {
        FrameRenderQueue queue = new FrameRenderQueue();
        queue.enqueue(request(1L, false, 4.0D));
        queue.enqueue(request(2L, true, 9.0D));
        queue.enqueue(request(3L, true, 25.0D));
        List<Long> order = new ArrayList<>();

        queue.flush(requests -> requests.forEach(request -> order.add(request.model().modelInstanceId())));

        assertEquals(List.of(1L, 3L, 2L), order);
        assertEquals(0, queue.size());
    }

    @Test
    void shouldClearBeforeInvokingDevice() {
        FrameRenderQueue queue = new FrameRenderQueue();
        queue.enqueue(request(1L, false, 0.0D));

        assertThrows(IllegalStateException.class,
                () -> queue.flush(requests -> { throw new IllegalStateException("failed"); }));
        assertEquals(0, queue.size());
    }

    /**
     * 每帧可见模型数上限：超限时保留最近的 N 个。
     *
     * <p>关键是"最近"而不是"先入队"——按旧排序键直接截断会砍掉不透明模型，
     * 把画面里最该看见的近处模型挤掉。
     */
    @Test
    void shouldKeepNearestModelsWhenFrameLimitIsExceeded() {
        int limit = ConfigManager.getMaxVisibleModelsPerFrame();
        FrameRenderQueue queue = new FrameRenderQueue();
        for (int index = 1; index <= limit + 5; index++) {
            queue.enqueue(request(index, false, index * 1.0D));
        }
        List<Long> order = new ArrayList<>();

        queue.flush(requests -> requests.forEach(request -> order.add(request.model().modelInstanceId())));

        assertEquals(limit, order.size());
        List<Long> expected = new ArrayList<>();
        for (int index = 1; index <= limit; index++) {
            expected.add((long) index);
        }
        assertEquals(expected, order);
    }

    private static MmdDrawRequest request(long id, boolean transparent, double distanceSquared) {
        return new MmdDrawRequest(
                new TestDrawModel(id),
                MmdFrameUpdaterTest.snapshot(distanceSquared),
                transparent ? PipelineVariant.TRANSLUCENT : PipelineVariant.OPAQUE,
                transparent);
    }

    private record TestDrawModel(long modelInstanceId) implements DrawModelRef {
        @Override
        public long nativeHandle() {
            return modelInstanceId;
        }

        @Override
        public long nativeRevision() {
            return 0L;
        }
    }
}
