package com.shiroha.mmdskin.texture.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeTexturePort;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.system.MemoryUtil;

/** 文件职责：维护纹理解码、主线程上传与可回收缓存的生命周期状态机。 */
public class TextureRepository {

    private static final NativeTexturePort NOOP_TEXTURE_PORT = new NativeTexturePort() {
        @Override public long loadTexture(String filename) { return 0L; }
        @Override public int textureWidth(long h) { return 0; }
        @Override public int textureHeight(long h) { return 0; }
        @Override public long textureData(long h) { return 0L; }
        @Override public boolean textureHasAlpha(long h) { return false; }
        @Override public void copyTextureData(ByteBuffer buf, long src, int size) {}
        @Override public void deleteTexture(long h) {}
    };

    private static volatile Map<String, TextureSlot> textureSlots;
    private static volatile NativeTexturePort texturePort = NOOP_TEXTURE_PORT;
    private static volatile VramBudgetManager budgetManager;
    private static final AtomicInteger activeTextureCount = new AtomicInteger();

    public static void Init() {
        textureSlots = new ConcurrentHashMap<>();
        budgetManager = new VramBudgetManager();
        activeTextureCount.set(0);
    }

    public static void configureRuntimeCollaborators(NativeTexturePort port) {
        TextureRepository.texturePort = port != null ? port : NOOP_TEXTURE_PORT;
    }

    public static void preloadTexture(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) return;

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
                    slot.predecoded = TextureGpuLoader.decode(filename, texturePort);
                    if (slot.predecoded != null) {
                        slot.retired = false;
                        return;
                    }
                    removeRetired = markRetiredIfEmpty(slot);
                }
            }
            if (!removeRetired) return;
            localSlots.remove(filename, slot);
            if (!retry) return;
        }
    }

    public static void clearPreloaded() {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) return;

        for (Map.Entry<String, TextureSlot> entry : localSlots.entrySet()) {
            TextureSlot slot = entry.getValue();
            boolean removeRetired;
            synchronized (slot) {
                slot.predecoded = freePredecoded(slot.predecoded);
                removeRetired = markRetiredIfEmpty(slot);
            }
            if (removeRetired) localSlots.remove(entry.getKey(), slot);
        }
    }

    public static Texture GetTexture(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) return null;

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
                        budgetManager.activatePendingTexture(slot, slot.texture, activeTextureCount);
                    }
                    return slot.texture;
                }

                PredecodedTexture predecoded = slot.predecoded;
                if (predecoded != null) {
                    slot.predecoded = null;
                    Texture uploaded = TextureGpuLoader.uploadPredecoded(predecoded);
                    slot.texture = uploaded;
                    slot.retired = false;
                    activeTextureCount.incrementAndGet();
                    return uploaded;
                }

                Texture loaded = TextureGpuLoader.loadToGpu(filename, texturePort);
                if (loaded != null) {
                    slot.texture = loaded;
                    slot.retired = false;
                    activeTextureCount.incrementAndGet();
                    return loaded;
                }
                removeRetired = markRetiredIfEmpty(slot);
            }
            if (!removeRetired) return null;
            localSlots.remove(filename, slot);
            if (!retry) return null;
        }
    }

    public static void addRef(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) return;

        TextureSlot slot = localSlots.get(filename);
        if (slot == null) return;

        synchronized (slot) {
            if (slot.retired || slot.texture == null || slot.pending) return;
            slot.texture.refCount.incrementAndGet();
        }
    }

    public static void release(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (filename == null || localSlots == null) return;

        TextureSlot slot = localSlots.get(filename);
        if (slot == null) return;

        synchronized (slot) {
            if (slot.retired || slot.texture == null || slot.pending) return;
            int remaining = slot.texture.refCount.decrementAndGet();
            if (remaining <= 0) {
                slot.texture.refCount.set(0);
                budgetManager.moveToPending(filename, slot, slot.texture, System.currentTimeMillis(), activeTextureCount);
            }
        }
    }

    public static void releaseAll(List<String> filenames) {
        if (filenames == null) return;
        for (String filename : filenames) release(filename);
    }

    public static void tick() {
        VramBudgetManager bm = budgetManager;
        if (bm != null) bm.tick(textureSlots, activeTextureCount);
    }

    public static void Cleanup() {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) return;

        for (TextureSlot slot : localSlots.values()) {
            synchronized (slot) {
                clearSlot(slot);
            }
        }
        localSlots.clear();
        VramBudgetManager bm = budgetManager;
        if (bm != null) bm.clearAll();
        activeTextureCount.set(0);
    }

    public static void DeleteTexture(String filename) {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null || filename == null) return;

        TextureSlot slot = localSlots.get(filename);
        if (slot == null) return;

        boolean removeRetired;
        synchronized (slot) {
            clearSlot(slot);
            removeRetired = markRetiredIfEmpty(slot);
        }
        if (removeRetired) localSlots.remove(filename, slot);
    }

    public static long getTotalTextureVram() {
        Map<String, TextureSlot> localSlots = textureSlots;
        if (localSlots == null) return 0L;

        long total = 0L;
        for (TextureSlot slot : localSlots.values()) {
            synchronized (slot) {
                if (slot.texture != null && !slot.pending) total += slot.texture.vramSize;
            }
        }
        return total;
    }

    public static int getTextureCount() {
        return activeTextureCount.get();
    }

    public static int getPendingReleaseCount() {
        VramBudgetManager bm = budgetManager;
        return bm != null ? bm.getPendingCount() : 0;
    }

    public static long getPendingReleaseVram() {
        VramBudgetManager bm = budgetManager;
        return bm != null ? bm.getPendingReleaseVram() : 0L;
    }

    private static void clearSlot(TextureSlot slot) {
        Texture texture = slot.texture;
        VramBudgetManager bm = budgetManager;
        if (slot.pending && bm != null) {
            bm.clearPendingState(slot, texture);
        } else if (texture != null) {
            activeTextureCount.getAndUpdate(v -> v > 0 ? v - 1 : 0);
        }
        TextureGpuLoader.deleteGlTexture(texture);
        slot.texture = null;
        slot.predecoded = freePredecoded(slot.predecoded);
    }

    private static boolean markRetiredIfEmpty(TextureSlot slot) {
        boolean empty = slot.texture == null && slot.predecoded == null && !slot.pending;
        slot.retired = empty;
        return empty;
    }

    private static PredecodedTexture freePredecoded(PredecodedTexture predecoded) {
        if (predecoded != null) predecoded.release();
        return null;
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

        ByteBuffer pixelData() { return pixelData; }

        void release() {
            if (pixelData != null) {
                MemoryUtil.memFree(pixelData);
                pixelData = null;
            }
        }
    }
}
