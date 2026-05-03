package com.shiroha.mmdskin.texture.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证纹理仓库的待回收票据状态机与预解码释放一致性。 */
class TextureRepositoryTest {

    @AfterEach
    void tearDown() {
        TextureRepository.Init();
    }

    @Test
    void shouldIgnoreStalePendingTicketAfterTextureIsReactivated() throws Exception {
        TextureRepository.Init();

        TextureRepository.TextureSlot slot = new TextureRepository.TextureSlot();
        TextureRepository.Texture texture = new TextureRepository.Texture();
        texture.vramSize = 64L;
        slot.texture = texture;

        activeTextureCount().set(1);
        moveToPending().invoke(null, "first", slot, texture, 10L);
        long firstTicketId = slot.pendingTicketId;

        activatePendingTexture().invoke(null, slot, texture);
        moveToPending().invoke(null, "second", slot, texture, 20L);
        long secondTicketId = slot.pendingTicketId;

        boolean staleEvicted = (boolean) evictIfCurrent().invoke(null, new TextureRepository.PendingTicket("first", slot, firstTicketId, 10L));
        assertFalse(staleEvicted);
        assertTrue(slot.pending);
        assertEquals(texture, slot.texture);

        boolean currentEvicted = (boolean) evictIfCurrent().invoke(null, new TextureRepository.PendingTicket("second", slot, secondTicketId, 20L));
        assertTrue(currentEvicted);
        assertFalse(slot.pending);
        assertNull(slot.texture);
        assertEquals(0, pendingTextureCount().get());
        assertEquals(0L, pendingReleaseVram().get());
    }

    @Test
    void shouldReleasePredecodedBufferOnlyOnce() {
        ByteBuffer buffer = MemoryUtil.memAlloc(4);
        TextureRepository.PredecodedTexture predecoded = new TextureRepository.PredecodedTexture(buffer, 1, 1, true);

        predecoded.release();
        predecoded.release();

        assertNull(predecoded.pixelData());
    }

    @Test
    void shouldClearPendingTextureAndPredecodedBufferTogether() throws Exception {
        TextureRepository.Init();

        TextureRepository.TextureSlot slot = new TextureRepository.TextureSlot();
        TextureRepository.Texture texture = new TextureRepository.Texture();
        texture.vramSize = 32L;
        slot.texture = texture;
        slot.predecoded = new TextureRepository.PredecodedTexture(MemoryUtil.memAlloc(4), 1, 1, true);

        activeTextureCount().set(1);
        moveToPending().invoke(null, "clear", slot, texture, 30L);
        clearSlot().invoke(null, slot);

        assertFalse(slot.pending);
        assertNull(slot.texture);
        assertNull(slot.predecoded);
        assertEquals(0, activeTextureCount().get());
        assertEquals(0, pendingTextureCount().get());
        assertEquals(0L, pendingReleaseVram().get());
    }

    private static Method moveToPending() throws Exception {
        Method method = TextureRepository.class.getDeclaredMethod("moveToPending", String.class, TextureRepository.TextureSlot.class, TextureRepository.Texture.class, long.class);
        method.setAccessible(true);
        return method;
    }

    private static Method activatePendingTexture() throws Exception {
        Method method = TextureRepository.class.getDeclaredMethod("activatePendingTexture", TextureRepository.TextureSlot.class, TextureRepository.Texture.class);
        method.setAccessible(true);
        return method;
    }

    private static Method evictIfCurrent() throws Exception {
        Method method = TextureRepository.class.getDeclaredMethod("evictIfCurrent", TextureRepository.PendingTicket.class);
        method.setAccessible(true);
        return method;
    }

    private static Method clearSlot() throws Exception {
        Method method = TextureRepository.class.getDeclaredMethod("clearSlot", TextureRepository.TextureSlot.class);
        method.setAccessible(true);
        return method;
    }

    private static AtomicInteger activeTextureCount() throws Exception {
        Field field = TextureRepository.class.getDeclaredField("activeTextureCount");
        field.setAccessible(true);
        return (AtomicInteger) field.get(null);
    }

    private static AtomicInteger pendingTextureCount() throws Exception {
        Field field = TextureRepository.class.getDeclaredField("pendingTextureCount");
        field.setAccessible(true);
        return (AtomicInteger) field.get(null);
    }

    private static AtomicLong pendingReleaseVram() throws Exception {
        Field field = TextureRepository.class.getDeclaredField("pendingReleaseVram");
        field.setAccessible(true);
        return (AtomicLong) field.get(null);
    }
}
