package com.shiroha.mmdskin.texture.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.bridge.runtime.NativeTexturePort;
import com.shiroha.mmdskin.config.ConfigManager;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

/** 文件职责：维护纹理解码、主线程上传与可回收缓存的生命周期状态机。 */
public class TextureRepository {

    private static final long TEXTURE_TTL_MS = 60_000;
    private static final NativeTexturePort NOOP_TEXTURE_PORT = new NativeTexturePort() {
        @Override
        public long loadTexture(String filename) {
            return 0L;
        }

        @Override
        public int textureWidth(long textureHandle) {
            return 0;
        }

        @Override
        public int textureHeight(long textureHandle) {
            return 0;
        }

        @Override
        public long textureData(long textureHandle) {
            return 0L;
        }

        @Override
        public boolean textureHasAlpha(long textureHandle) {
            return false;
        }

        @Override
        public void copyTextureData(ByteBuffer targetBuffer, long sourceAddress, int size) {
        }

        @Override
        public void deleteTexture(long textureHandle) {
        }
    };

    private static volatile Map<String, TextureSlot> textureSlots;
    private static volatile NativeTexturePort texturePort = NOOP_TEXTURE_PORT;

    private static final ConcurrentLinkedDeque<PendingTicket> pendingTickets = new ConcurrentLinkedDeque<>();

    private static final AtomicLong nextPendingTicketId = new AtomicLong();

    private static final AtomicLong pendingReleaseVram = new AtomicLong();

    private static final AtomicInteger activeTextureCount = new AtomicInteger();

    private static final AtomicInteger pendingTextureCount = new AtomicInteger();

    public static void Init() {
        textureSlots = new ConcurrentHashMap<>();
        pendingTickets.clear();
        nextPendingTicketId.set(0L);
        pendingReleaseVram.set(0L);
        activeTextureCount.set(0);
        pendingTextureCount.set(0);
    }

    public static void configureRuntimeCollaborators(NativeTexturePort texturePort) {
        TextureRepository.texturePort = texturePort != null ? texturePort : NOOP_TEXTURE_PORT;
    }

    public static void preloadTexture(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) {
            return;
        }

        while (true) {
            TextureSlot slot = localSlots.computeIfAbsent(filename, ignored -> new TextureSlot());
            boolean removeRetired = false;
            boolean retry = false;
            synchronized (slot) {
                if (slot.retired) {
                    removeRetired = true;
                    retry = true;
                } else if (slot.texture != null || slot.predecoded != null) {
                    return;
                } else {
                    slot.predecoded = decodeTexture(filename);
                    if (slot.predecoded != null) {
                        slot.retired = false;
                        return;
                    }
                    removeRetired = markRetiredIfEmpty(slot);
                }
            }

            if (!removeRetired) {
                return;
            }
            localSlots.remove(filename, slot);
            if (!retry) {
                return;
            }
        }
    }

    public static void clearPreloaded() {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) {
            return;
        }

        for (Map.Entry<String, TextureSlot> entry : localSlots.entrySet()) {
            TextureSlot slot = entry.getValue();
            boolean removeRetired;
            synchronized (slot) {
                slot.predecoded = freePredecoded(slot.predecoded);
                removeRetired = markRetiredIfEmpty(slot);
            }
            if (removeRetired) {
                localSlots.remove(entry.getKey(), slot);
            }
        }
    }

    public static Texture GetTexture(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) {
            return null;
        }

        while (true) {
            TextureSlot slot = localSlots.computeIfAbsent(filename, ignored -> new TextureSlot());
            boolean removeRetired = false;
            boolean retry = false;
            synchronized (slot) {
                if (slot.retired) {
                    removeRetired = true;
                    retry = true;
                } else if (slot.texture != null) {
                    if (slot.pending) {
                        activatePendingTexture(slot, slot.texture);
                    }
                    return slot.texture;
                }

                PredecodedTexture predecoded = slot.predecoded;
                if (predecoded != null) {
                    slot.predecoded = null;
                    Texture uploaded = uploadPredecodedTexture(predecoded);
                    slot.texture = uploaded;
                    slot.retired = false;
                    activeTextureCount.incrementAndGet();
                    return uploaded;
                }

                Texture loaded = loadTextureToGpu(filename);
                if (loaded != null) {
                    slot.texture = loaded;
                    slot.retired = false;
                    activeTextureCount.incrementAndGet();
                    return loaded;
                }
                removeRetired = markRetiredIfEmpty(slot);
            }

            if (!removeRetired) {
                return null;
            }
            localSlots.remove(filename, slot);
            if (!retry) {
                return null;
            }
        }
    }

    public static void addRef(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) {
            return;
        }

        TextureSlot slot = localSlots.get(filename);
        if (slot == null) {
            return;
        }

        synchronized (slot) {
            if (slot.retired) {
                return;
            }
            if (slot.texture != null && !slot.pending) {
                slot.texture.refCount.incrementAndGet();
            }
        }
    }

    public static void release(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (filename == null || localSlots == null) {
            return;
        }

        TextureSlot slot = localSlots.get(filename);
        if (slot == null) {
            return;
        }

        synchronized (slot) {
            if (slot.retired) {
                return;
            }
            Texture texture = slot.texture;
            if (texture == null || slot.pending) {
                return;
            }
            int remaining = texture.refCount.decrementAndGet();
            if (remaining <= 0) {
                texture.refCount.set(0);
                moveToPending(filename, slot, texture, System.currentTimeMillis());
            }
        }
    }

    public static void releaseAll(List<String> filenames) {
        if (filenames == null) {
            return;
        }
        for (String filename : filenames) {
            release(filename);
        }
    }

    public static void tick() {
        if (pendingTextureCount.get() <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        evictExpired(now);

        long budgetBytes = ConfigManager.getTextureCacheBudgetMB() * 1024L * 1024L;
        while (pendingReleaseVram.get() > budgetBytes && pendingTextureCount.get() > 0) {
            if (!evictOldestPending()) {
                break;
            }
        }
    }

    public static void Cleanup() {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) {
            return;
        }

        for (TextureSlot slot : localSlots.values()) {
            synchronized (slot) {
                clearSlot(slot);
            }
        }
        localSlots.clear();
        pendingTickets.clear();
        pendingReleaseVram.set(0L);
        activeTextureCount.set(0);
        pendingTextureCount.set(0);
    }

    public static void DeleteTexture(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null || filename == null) {
            return;
        }

        TextureSlot slot = localSlots.get(filename);
        if (slot == null) {
            return;
        }

        boolean removeRetired;
        synchronized (slot) {
            clearSlot(slot);
            removeRetired = markRetiredIfEmpty(slot);
        }
        if (removeRetired) {
            localSlots.remove(filename, slot);
        }
    }

    public static long getTotalTextureVram() {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) {
            return 0L;
        }

        long total = 0L;
        for (TextureSlot slot : localSlots.values()) {
            synchronized (slot) {
                if (slot.texture != null && !slot.pending) {
                    total += slot.texture.vramSize;
                }
            }
        }
        return total;
    }

    public static int getTextureCount() {
        return activeTextureCount.get();
    }

    public static int getPendingReleaseCount() {
        return pendingTextureCount.get();
    }

    public static long getPendingReleaseVram() {
        return pendingReleaseVram.get();
    }

    private static Texture loadTextureToGpu(String filename) {
        NativeTexturePort textureBridge = texturePort;
        long textureHandle = textureBridge.loadTexture(filename);
        if (textureHandle == 0) {
            return null;
        }

        int width = textureBridge.textureWidth(textureHandle);
        int height = textureBridge.textureHeight(textureHandle);
        long textureData = textureBridge.textureData(textureHandle);
        boolean hasAlpha = textureBridge.textureHasAlpha(textureHandle);

        int textureId = GL46C.glGenTextures();
        int textureSize = width * height * (hasAlpha ? 4 : 3);
        ByteBuffer textureBuffer = MemoryUtil.memAlloc(textureSize);
        try {
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);
            textureBridge.copyTextureData(textureBuffer, textureData, textureSize);
            textureBuffer.rewind();
            uploadPixels(width, height, hasAlpha, textureBuffer);
            configureTexture();
            return createTexture(textureId, width, height, hasAlpha);
        } catch (RuntimeException | Error e) {
            deleteGlTexture(textureId);
            throw e;
        } finally {
            MemoryUtil.memFree(textureBuffer);
            textureBridge.deleteTexture(textureHandle);
        }
    }

    private static PredecodedTexture decodeTexture(String filename) {
        NativeTexturePort textureBridge = texturePort;
        long textureHandle = textureBridge.loadTexture(filename);
        if (textureHandle == 0) {
            return null;
        }

        try {
            int width = textureBridge.textureWidth(textureHandle);
            int height = textureBridge.textureHeight(textureHandle);
            long textureData = textureBridge.textureData(textureHandle);
            boolean hasAlpha = textureBridge.textureHasAlpha(textureHandle);

            int textureSize = width * height * (hasAlpha ? 4 : 3);
            ByteBuffer pixelBuffer = MemoryUtil.memAlloc(textureSize);
            textureBridge.copyTextureData(pixelBuffer, textureData, textureSize);
            pixelBuffer.rewind();
            return new PredecodedTexture(pixelBuffer, width, height, hasAlpha);
        } finally {
            textureBridge.deleteTexture(textureHandle);
        }
    }

    private static Texture uploadPredecodedTexture(PredecodedTexture predecoded) {
        int textureId = GL46C.glGenTextures();
        try {
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);
            uploadPixels(predecoded.width, predecoded.height, predecoded.hasAlpha, predecoded.pixelData());
            configureTexture();
            return createTexture(textureId, predecoded.width, predecoded.height, predecoded.hasAlpha);
        } catch (RuntimeException | Error e) {
            deleteGlTexture(textureId);
            throw e;
        } finally {
            predecoded.release();
        }
    }

    private static void uploadPixels(int width, int height, boolean hasAlpha, ByteBuffer pixelBuffer) {
        if (hasAlpha) {
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 4);
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGBA, width, height, 0, GL46C.GL_RGBA, GL46C.GL_UNSIGNED_BYTE, pixelBuffer);
        } else {
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 1);
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGB, width, height, 0, GL46C.GL_RGB, GL46C.GL_UNSIGNED_BYTE, pixelBuffer);
        }
    }

    private static void configureTexture() {
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_LEVEL, 0);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, GL46C.GL_LINEAR);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, GL46C.GL_LINEAR);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
    }

    private static Texture createTexture(int textureId, int width, int height, boolean hasAlpha) {
        Texture result = new Texture();
        result.tex = textureId;
        result.hasAlpha = hasAlpha;
        result.vramSize = (long) width * height * (hasAlpha ? 4 : 3);
        return result;
    }

    private static void evictExpired(long now) {
        while (true) {
            PendingTicket ticket = pendingTickets.peekFirst();
            if (ticket == null || now - ticket.releaseTimeMs <= TEXTURE_TTL_MS) {
                return;
            }
            pendingTickets.pollFirst();
            evictIfCurrent(ticket);
        }
    }

    private static boolean evictOldestPending() {
        while (true) {
            PendingTicket ticket = pendingTickets.pollFirst();
            if (ticket == null) {
                return false;
            }
            if (evictIfCurrent(ticket)) {
                return true;
            }
        }
    }

    private static boolean evictIfCurrent(PendingTicket ticket) {
        boolean removeRetired = false;
        Texture texture;
        synchronized (ticket.slot) {
            if (!ticket.slot.pending || ticket.slot.pendingTicketId != ticket.ticketId) {
                return false;
            }
            texture = ticket.slot.texture;
            clearPendingState(ticket.slot, texture);
            ticket.slot.texture = null;
            removeRetired = markRetiredIfEmpty(ticket.slot);
        }
        deleteGlTexture(texture);
        if (removeRetired) {
            Map<String, TextureSlot> localSlots = textureSlots;
            if (localSlots != null) {
                localSlots.remove(ticket.filename, ticket.slot);
            }
        }
        return texture != null;
    }

    private static void moveToPending(String filename, TextureSlot slot, Texture texture, long now) {
        slot.pending = true;
        slot.pendingTicketId = nextPendingTicketId.incrementAndGet();
        slot.retired = false;
        texture.lastReleaseTime = now;
        pendingReleaseVram.addAndGet(texture.vramSize);
        pendingTextureCount.incrementAndGet();
        decrementIfPositive(activeTextureCount);
        pendingTickets.addLast(new PendingTicket(filename, slot, slot.pendingTicketId, now));
    }

    private static void activatePendingTexture(TextureSlot slot, Texture texture) {
        clearPendingState(slot, texture);
        slot.retired = false;
        activeTextureCount.incrementAndGet();
    }

    private static void clearSlot(TextureSlot slot) {
        Texture texture = slot.texture;
        if (slot.pending) {
            clearPendingState(slot, texture);
        } else if (texture != null) {
            decrementIfPositive(activeTextureCount);
        }

        deleteGlTexture(texture);
        slot.texture = null;
        slot.predecoded = freePredecoded(slot.predecoded);
    }

    private static void clearPendingState(TextureSlot slot, Texture texture) {
        if (!slot.pending) {
            return;
        }
        slot.pending = false;
        slot.pendingTicketId = 0L;
        decrementIfPositive(pendingTextureCount);
        if (texture != null) {
            subtractPendingVram(texture.vramSize);
        }
    }

    private static boolean markRetiredIfEmpty(TextureSlot slot) {
        boolean empty = slot.texture == null && slot.predecoded == null && !slot.pending;
        slot.retired = empty;
        return empty;
    }

    private static void decrementIfPositive(AtomicInteger counter) {
        counter.getAndUpdate(value -> value > 0 ? value - 1 : 0);
    }

    private static void subtractPendingVram(long vramSize) {
        if (vramSize <= 0L) {
            return;
        }
        pendingReleaseVram.updateAndGet(value -> Math.max(0L, value - vramSize));
    }

    private static PredecodedTexture freePredecoded(PredecodedTexture predecoded) {
        if (predecoded != null) {
            predecoded.release();
        }
        return null;
    }

    private static void deleteGlTexture(Texture tex) {
        if (tex != null) {
            deleteGlTexture(tex.tex);
            tex.tex = 0;
        }
    }

    private static void deleteGlTexture(int textureId) {
        if (textureId <= 0) {
            return;
        }
        if (RenderSystem.isOnRenderThreadOrInit()) {
            GL46C.glDeleteTextures(textureId);
            return;
        }
        RenderSystem.recordRenderCall(() -> GL46C.glDeleteTextures(textureId));
    }

    public static class Texture {
        public int tex;
        public boolean hasAlpha;
        public long vramSize;
        final AtomicInteger refCount = new AtomicInteger(0);
        volatile long lastReleaseTime;
    }

    static final class TextureSlot {
        Texture texture;
        PredecodedTexture predecoded;
        boolean pending;
        long pendingTicketId;
        boolean retired;
    }

    static final class PendingTicket {
        final String filename;
        final TextureSlot slot;
        final long ticketId;
        final long releaseTimeMs;

        PendingTicket(String filename, TextureSlot slot, long ticketId, long releaseTimeMs) {
            this.filename = filename;
            this.slot = slot;
            this.ticketId = ticketId;
            this.releaseTimeMs = releaseTimeMs;
        }
    }

    static final class PredecodedTexture {
        private ByteBuffer pixelData;
        final int width;
        final int height;
        final boolean hasAlpha;

        PredecodedTexture(ByteBuffer pixelData, int width, int height, boolean hasAlpha) {
            this.pixelData = pixelData;
            this.width = width;
            this.height = height;
            this.hasAlpha = hasAlpha;
        }

        ByteBuffer pixelData() {
            return pixelData;
        }

        void release() {
            if (pixelData != null) {
                MemoryUtil.memFree(pixelData);
                pixelData = null;
            }
        }
    }
}
