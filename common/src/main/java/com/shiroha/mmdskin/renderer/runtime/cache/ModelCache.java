package com.shiroha.mmdskin.renderer.runtime.cache;

import com.shiroha.mmdskin.config.ConfigManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 模型缓存管理器。
 */
public class ModelCache<T> {
    private static final Logger logger = LogManager.getLogger();

    private final Map<String, CacheEntry<T>> active;

    private final Map<String, CacheEntry<T>> pendingRelease;
    private final String cacheName;

    private static final long IDLE_TTL_MS = 60_000;

    private static final long PENDING_TTL_MS = 60_000;

    public ModelCache(String name) {
        this.cacheName = name;
        this.active = new ConcurrentHashMap<>();
        this.pendingRelease = new ConcurrentHashMap<>();
    }

    public CacheEntry<T> get(String key) {

        CacheEntry<T> entry = active.get(key);
        if (entry != null) {
            entry.updateAccessTime();
            return entry;
        }

        entry = pendingRelease.remove(key);
        if (entry != null) {
            entry.updateAccessTime();
            active.put(key, entry);
            return entry;
        }

        return null;
    }

    public void put(String key, T value) {
        active.put(key, new CacheEntry<>(value));
    }

    public CacheEntry<T> remove(String key) {
        CacheEntry<T> entry = active.remove(key);
        if (entry != null) return entry;
        return pendingRelease.remove(key);
    }

    public int size() {
        return active.size();
    }

    public int pendingSize() {
        return pendingRelease.size();
    }

    public void tick(Consumer<T> disposer) {
        long now = System.currentTimeMillis();

        List<String> idleKeys = null;
        for (var entry : active.entrySet()) {
            if (now - entry.getValue().lastAccessTime > IDLE_TTL_MS) {
                if (idleKeys == null) idleKeys = new ArrayList<>();
                idleKeys.add(entry.getKey());
            }
        }
        if (idleKeys != null) {
            for (String key : idleKeys) {
                CacheEntry<T> entry = active.remove(key);
                if (entry != null) {
                    entry.pendingSince = now;
                    pendingRelease.put(key, entry);
                }
            }
        }

        if (pendingRelease.isEmpty()) return;

        List<String> expired = null;
        for (var entry : pendingRelease.entrySet()) {
            if (now - entry.getValue().pendingSince > PENDING_TTL_MS) {
                if (expired == null) expired = new ArrayList<>();
                expired.add(entry.getKey());
            }
        }
        if (expired != null) {
            for (String key : expired) {
                CacheEntry<T> entry = pendingRelease.remove(key);
                if (entry != null) {
                    safeDispose(disposer, entry.value, key);
                }
            }
        }

        int maxSize = ConfigManager.getModelPoolMaxCount();
        int totalSize = active.size() + pendingRelease.size();
        if (totalSize > maxSize && !pendingRelease.isEmpty()) {
            evictPendingByLRU(totalSize - maxSize, disposer);
        }
    }

    private synchronized void evictPendingByLRU(int evictCount, Consumer<T> disposer) {
        if (pendingRelease.isEmpty() || evictCount <= 0) return;

        List<Map.Entry<String, CacheEntry<T>>> sorted = new ArrayList<>(pendingRelease.entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue().lastAccessTime, b.getValue().lastAccessTime));

        int evicted = 0;
        for (var entry : sorted) {
            if (evicted >= evictCount) break;
            CacheEntry<T> removed = pendingRelease.remove(entry.getKey());
            if (removed != null) {
                safeDispose(disposer, removed.value, entry.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
        }
    }

    public void removeMatching(Predicate<String> keyMatcher, Consumer<T> disposer) {
        removeMatchingFrom(active, keyMatcher, disposer);
        removeMatchingFrom(pendingRelease, keyMatcher, disposer);
    }

    private void removeMatchingFrom(Map<String, CacheEntry<T>> map,
                                     Predicate<String> keyMatcher,
                                     Consumer<T> disposer) {
        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (keyMatcher.test(entry.getKey())) {
                it.remove();
                safeDispose(disposer, entry.getValue().value, entry.getKey());
            }
        }
    }

    public synchronized void clear(Consumer<T> disposer) {
        int activeCount = active.size();
        for (CacheEntry<T> entry : active.values()) {
            safeDispose(disposer, entry.value, null);
        }
        active.clear();

        int pendingCount = pendingRelease.size();
        for (CacheEntry<T> entry : pendingRelease.values()) {
            safeDispose(disposer, entry.value, null);
        }
        pendingRelease.clear();
    }

    public void forEach(BiConsumer<String, CacheEntry<T>> action) {
        active.forEach(action);
    }

    private void safeDispose(Consumer<T> disposer, T value, String key) {
        try {
            if (disposer != null) {
                disposer.accept(value);
            }
        } catch (Exception e) {
            logger.error("[{}] 释放失败: {}", cacheName, key, e);
        }
    }

    public static class CacheEntry<T> {
        public final T value;

        public volatile long lastAccessTime;

        volatile long pendingSince;

        public CacheEntry(T value) {
            this.value = value;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
