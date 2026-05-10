package com.shiroha.mmdskin.texture.runtime;

import com.shiroha.mmdskin.config.ConfigManager;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 文件职责：管理纹理待释放队列的 VRAM 预算与 TTL 驱逐。 */
final class VramBudgetManager {
    private static final long TEXTURE_TTL_MS = 60_000;

    private final ConcurrentLinkedDeque<PendingTicket> pendingTickets = new ConcurrentLinkedDeque<>();
    private final AtomicLong nextPendingTicketId = new AtomicLong();
    private final AtomicLong pendingReleaseVram = new AtomicLong();
    private final AtomicInteger pendingTextureCount = new AtomicInteger();

    void reset() {
        pendingTickets.clear();
        nextPendingTicketId.set(0L);
        pendingReleaseVram.set(0L);
        pendingTextureCount.set(0);
    }

    void tick(Map<String, TextureRepository.TextureSlot> slots, AtomicInteger activeCount) {
        if (pendingTextureCount.get() <= 0) return;
        long now = System.currentTimeMillis();
        evictExpired(slots, now);
        long budgetBytes = ConfigManager.getTextureCacheBudgetMB() * 1024L * 1024L;
        while (pendingReleaseVram.get() > budgetBytes && pendingTextureCount.get() > 0) {
            if (!evictOldestPending(slots)) break;
        }
    }

    void moveToPending(String filename, TextureRepository.TextureSlot slot,
                       TextureRepository.Texture texture, long now, AtomicInteger activeCount) {
        slot.pending = true;
        slot.pendingTicketId = nextPendingTicketId.incrementAndGet();
        slot.retired = false;
        texture.lastReleaseTime = now;
        pendingReleaseVram.addAndGet(texture.vramSize);
        pendingTextureCount.incrementAndGet();
        decrementIfPositive(activeCount);
        pendingTickets.addLast(new PendingTicket(filename, slot, slot.pendingTicketId, now));
    }

    void activatePendingTexture(TextureRepository.TextureSlot slot,
                                TextureRepository.Texture texture, AtomicInteger activeCount) {
        clearPendingState(slot, texture);
        slot.retired = false;
        activeCount.incrementAndGet();
    }

    void clearPendingState(TextureRepository.TextureSlot slot, TextureRepository.Texture texture) {
        if (!slot.pending) return;
        slot.pending = false;
        slot.pendingTicketId = 0L;
        decrementIfPositive(pendingTextureCount);
        if (texture != null) {
            pendingReleaseVram.updateAndGet(v -> Math.max(0L, v - texture.vramSize));
        }
    }

    void clearAll() {
        pendingTickets.clear();
        pendingReleaseVram.set(0L);
        pendingTextureCount.set(0);
    }

    long getPendingReleaseVram() {
        return pendingReleaseVram.get();
    }

    int getPendingCount() {
        return pendingTextureCount.get();
    }

    private void evictExpired(Map<String, TextureRepository.TextureSlot> slots, long now) {
        while (true) {
            PendingTicket ticket = pendingTickets.peekFirst();
            if (ticket == null || now - ticket.releaseTimeMs <= TEXTURE_TTL_MS) return;
            pendingTickets.pollFirst();
            evictIfCurrent(slots, ticket);
        }
    }

    private boolean evictOldestPending(Map<String, TextureRepository.TextureSlot> slots) {
        while (true) {
            PendingTicket ticket = pendingTickets.pollFirst();
            if (ticket == null) return false;
            if (evictIfCurrent(slots, ticket)) return true;
        }
    }

    private boolean evictIfCurrent(Map<String, TextureRepository.TextureSlot> slots, PendingTicket ticket) {
        boolean removeRetired;
        TextureRepository.Texture texture;
        synchronized (ticket.slot) {
            if (!ticket.slot.pending || ticket.slot.pendingTicketId != ticket.ticketId) return false;
            texture = ticket.slot.texture;
            clearPendingState(ticket.slot, texture);
            ticket.slot.texture = null;
            removeRetired = retiredIfEmpty(ticket.slot);
        }
        TextureGpuLoader.deleteGlTexture(texture);
        if (removeRetired && slots != null) {
            slots.remove(ticket.filename, ticket.slot);
        }
        return texture != null;
    }

    private static boolean retiredIfEmpty(TextureRepository.TextureSlot slot) {
        boolean empty = slot.texture == null && slot.predecoded == null && !slot.pending;
        slot.retired = empty;
        return empty;
    }

    private static void decrementIfPositive(AtomicInteger counter) {
        counter.getAndUpdate(v -> v > 0 ? v - 1 : 0);
    }

    static final class PendingTicket {
        final String filename;
        final TextureRepository.TextureSlot slot;
        final long ticketId;
        final long releaseTimeMs;

        PendingTicket(String filename, TextureRepository.TextureSlot slot, long ticketId, long releaseTimeMs) {
            this.filename = filename;
            this.slot = slot;
            this.ticketId = ticketId;
            this.releaseTimeMs = releaseTimeMs;
        }
    }
}
