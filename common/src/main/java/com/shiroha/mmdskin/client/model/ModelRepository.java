// 负责模型异步解析、失败退避、租约计数和确定性回收。
package com.shiroha.mmdskin.client.model;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelLoadPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelMatrixPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.client.metrics.ModelLoadMetricsPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModelRepository implements ModelLoadMetricsPort, AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int ANIMATION_LAYERS = 3;

    private final NativeModelLoadPort loader;
    private final NativeAnimationPort animations;
    private final NativeModelPort modelAccess;
    private final NativeModelMatrixPort modelMatrices;
    private final Executor loadExecutor;
    private final Clock clock;
    private final long retryDelayMillis;
    private final ModelDisposalListener disposalListener;
    private final ModelBoundsLoader boundsLoader;
    private final Map<ModelKey, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, ModelBounds> boundsByModelName = new ConcurrentHashMap<>();

    public ModelRepository(NativeModelLoadPort loader, NativeAnimationPort animations,
                           NativeModelPort modelAccess, Executor loadExecutor,
                           Clock clock, Duration retryDelay) {
        this(loader, animations, modelAccess, com.shiroha.mmdskin.bridge.NativePortAdapters.modelMatrix(),
                loadExecutor, clock, retryDelay,
                ModelDisposalListener.NOOP, ModelBoundsLoader.UNAVAILABLE);
    }

    public ModelRepository(NativeModelLoadPort loader, NativeAnimationPort animations,
                           NativeModelPort modelAccess, Executor loadExecutor,
                           Clock clock, Duration retryDelay,
                           ModelDisposalListener disposalListener) {
        this(loader, animations, modelAccess, com.shiroha.mmdskin.bridge.NativePortAdapters.modelMatrix(),
                loadExecutor, clock, retryDelay,
                disposalListener, ModelBoundsLoader.UNAVAILABLE);
    }

    public ModelRepository(NativeModelLoadPort loader, NativeAnimationPort animations,
                           NativeModelPort modelAccess, NativeModelMatrixPort modelMatrices,
                           Executor loadExecutor,
                           Clock clock, Duration retryDelay,
                           ModelDisposalListener disposalListener,
                           ModelBoundsLoader boundsLoader) {
        this.loader = loader;
        this.animations = animations;
        this.modelAccess = modelAccess;
        this.modelMatrices = modelMatrices;
        this.loadExecutor = loadExecutor;
        this.clock = clock;
        this.retryDelayMillis = retryDelay.toMillis();
        this.disposalListener = java.util.Objects.requireNonNull(disposalListener, "disposalListener");
        this.boundsLoader = java.util.Objects.requireNonNull(boundsLoader, "boundsLoader");
    }

    public Optional<ModelLease> acquire(ModelKey key) {
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
        MmdModelInstance instance = entry.instance;
        if (instance != null) {
            entry.leases.incrementAndGet();
            entry.lastAccessMillis = clock.millis();
            return Optional.of(new ModelLease(instance, () -> release(entry)));
        }

        startLoadIfAllowed(key, entry);
        completeLoadIfReady(key, entry);
        instance = entry.instance;
        if (instance == null) {
            return Optional.empty();
        }
        entry.leases.incrementAndGet();
        entry.lastAccessMillis = clock.millis();
        return Optional.of(new ModelLease(instance, () -> release(entry)));
    }

    public void tick() {
        entries.forEach(this::completeLoadIfReady);
    }

    public boolean isLoading(ModelKey key) {
        Entry entry = entries.get(key);
        return entry != null && entry.pending != null && !entry.pending.isDone();
    }

    @Override
    public int loadedCount() {
        return (int) entries.values().stream().filter(entry -> entry.instance != null).count();
    }

    @Override
    public int pendingCount() {
        return (int) entries.values().stream()
                .filter(entry -> entry.pending != null && !entry.pending.isDone())
                .count();
    }

    /** Returns a stable snapshot of loaded instances for diagnostic UI only. */
    public List<MmdModelInstance> loadedInstances() {
        return entries.values().stream()
                .map(entry -> entry.instance)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public ModelBounds loadedBounds(String modelName) {
        return boundsByModelName.get(modelName);
    }

    public void invalidateOwner(String ownerId) {
        entries.entrySet().removeIf(entry -> {
            if (!entry.getKey().ownerId().equals(ownerId)) {
                return false;
            }
            disposeEntry(entry.getValue());
            return true;
        });
    }

    public void invalidateModel(String modelName) {
        boundsByModelName.remove(modelName);
        entries.entrySet().removeIf(entry -> {
            if (!entry.getKey().modelName().equals(modelName)) {
                return false;
            }
            disposeEntry(entry.getValue());
            return true;
        });
    }

    public void evictUnused(int maximumLoaded) {
        int overflow = loadedCount() - Math.max(0, maximumLoaded);
        if (overflow <= 0) {
            return;
        }
        ArrayList<Map.Entry<ModelKey, Entry>> candidates = new ArrayList<>(entries.entrySet());
        candidates.removeIf(candidate -> candidate.getValue().instance == null || candidate.getValue().leases.get() != 0);
        candidates.sort(Comparator.comparingLong(candidate -> candidate.getValue().lastAccessMillis));
        for (int i = 0; i < Math.min(overflow, candidates.size()); i++) {
            Map.Entry<ModelKey, Entry> candidate = candidates.get(i);
            if (entries.remove(candidate.getKey(), candidate.getValue())) {
                disposeEntry(candidate.getValue());
            }
        }
    }

    private void startLoadIfAllowed(ModelKey key, Entry entry) {
        synchronized (entry) {
            if (entry.instance != null || entry.pending != null) {
                return;
            }
            long now = clock.millis();
            if (now < entry.retryAfterMillis) {
                return;
            }
            ModelSource source = ModelSource.find(key.modelName());
            if (source == null) {
                entry.retryAfterMillis = now + retryDelayMillis;
                return;
            }
            entry.pending = CompletableFuture.supplyAsync(() -> load(key, source), loadExecutor);
        }
    }

    private LoadedModel load(ModelKey key, ModelSource source) {
        long handle = loader.loadModel(source.file(), source.directory(), source.format(), ANIMATION_LAYERS);
        if (handle == 0L) {
            return null;
        }
        return new LoadedModel(key, source, handle, loadBounds(key.modelName(), handle));
    }

    private ModelBounds loadBounds(String modelName, long handle) {
        ModelBounds cached = boundsByModelName.get(modelName);
        if (cached != null) {
            return cached;
        }
        try {
            return boundsLoader.load(handle);
        } catch (RuntimeException exception) {
            LOGGER.warn("模型 bounds 提取失败，将使用实体原始剔除盒: {}", modelName, exception);
            return null;
        }
    }

    private void completeLoadIfReady(ModelKey key, Entry entry) {
        CompletableFuture<LoadedModel> pending = entry.pending;
        if (pending == null || !pending.isDone()) {
            return;
        }
        synchronized (entry) {
            if (entry.pending != pending) {
                return;
            }
            entry.pending = null;
            try {
                LoadedModel loaded = pending.join();
                if (loaded == null) {
                    entry.retryAfterMillis = clock.millis() + retryDelayMillis;
                    return;
                }
                entry.instance = new MmdModelInstance(key, loaded.source(), loaded.handle(),
                        loader, animations, modelAccess, modelMatrices);
                if (loaded.bounds() != null) {
                    boundsByModelName.putIfAbsent(key.modelName(), loaded.bounds());
                }
                entry.lastAccessMillis = clock.millis();
            } catch (RuntimeException exception) {
                entry.retryAfterMillis = clock.millis() + retryDelayMillis;
                LOGGER.warn("模型异步加载失败: {}", key, exception);
            }
        }
    }

    private void release(Entry entry) {
        entry.leases.updateAndGet(value -> Math.max(0, value - 1));
        entry.lastAccessMillis = clock.millis();
    }

    private void disposeEntry(Entry entry) {
        CompletableFuture<LoadedModel> pending = entry.pending;
        entry.pending = null;
        if (pending != null) {
            pending.whenComplete((loaded, error) -> {
                if (loaded != null) {
                    modelAccess.deleteModel(loaded.handle());
                }
            });
        }
        MmdModelInstance instance = entry.instance;
        entry.instance = null;
        if (instance != null) {
            disposalListener.onDisposed(instance.modelInstanceId());
            instance.close();
        }
    }

    @Override
    public void close() {
        entries.values().forEach(this::disposeEntry);
        entries.clear();
        boundsByModelName.clear();
    }

    private static final class Entry {
        private final AtomicInteger leases = new AtomicInteger();
        private volatile CompletableFuture<LoadedModel> pending;
        private volatile MmdModelInstance instance;
        private volatile long retryAfterMillis;
        private volatile long lastAccessMillis;
    }

    private record LoadedModel(ModelKey key, ModelSource source, long handle, ModelBounds bounds) {
    }
}
