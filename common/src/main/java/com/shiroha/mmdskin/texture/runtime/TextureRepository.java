package com.shiroha.mmdskin.texture.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeTextureBridgeHolder;
import com.shiroha.mmdskin.bridge.runtime.NativeTexturePort;
import com.shiroha.mmdskin.config.ConfigManager;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

/** 文件职责：维护纹理预解码、引用计数与 TTL/LRU 回收。 */
public class TextureRepository {

    private static volatile Map<String, Texture> textures;

    private static final Map<String, Texture> pendingRelease = new ConcurrentHashMap<>();

    private static final Map<String, PredecodedTexture> predecodedTextures = new ConcurrentHashMap<>();

    private static final long TEXTURE_TTL_MS = 60_000;

    public static void Init() {
        textures = new ConcurrentHashMap<>();
        pendingRelease.clear();
    }

    public static void preloadTexture(String filename) {
        Map<String, Texture> localTextures = textures;
        if (localTextures == null) {
            return;
        }
        if (localTextures.containsKey(filename)
                || pendingRelease.containsKey(filename)
                || predecodedTextures.containsKey(filename)) {
            return;
        }

        NativeTexturePort textureBridge = NativeTextureBridgeHolder.get();
        long textureHandle = textureBridge.loadTexture(filename);
        if (textureHandle == 0) {
            return;
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

            PredecodedTexture predecoded = new PredecodedTexture();
            predecoded.pixelData = pixelBuffer;
            predecoded.width = width;
            predecoded.height = height;
            predecoded.hasAlpha = hasAlpha;

            PredecodedTexture existing = predecodedTextures.putIfAbsent(filename, predecoded);
            if (existing != null) {
                MemoryUtil.memFree(pixelBuffer);
            }
        } finally {
            textureBridge.deleteTexture(textureHandle);
        }
    }

    public static void clearPreloaded() {
        for (PredecodedTexture predecoded : predecodedTextures.values()) {
            if (predecoded.pixelData != null) {
                MemoryUtil.memFree(predecoded.pixelData);
                predecoded.pixelData = null;
            }
        }
        predecodedTextures.clear();
    }

    public static Texture GetTexture(String filename) {
        Map<String, Texture> localTextures = textures;
        if (localTextures == null) {
            return null;
        }

        Texture result = localTextures.get(filename);
        if (result != null) {
            return result;
        }

        result = pendingRelease.remove(filename);
        if (result != null) {
            result.refCount.set(0);
            localTextures.put(filename, result);
            return result;
        }

        PredecodedTexture predecoded = predecodedTextures.remove(filename);
        if (predecoded != null) {
            result = uploadPredecodedTexture(predecoded);
            localTextures.put(filename, result);
            return result;
        }

        NativeTexturePort textureBridge = NativeTextureBridgeHolder.get();
        long textureHandle = textureBridge.loadTexture(filename);
        if (textureHandle == 0) {
            return null;
        }

        int width = textureBridge.textureWidth(textureHandle);
        int height = textureBridge.textureHeight(textureHandle);
        long textureData = textureBridge.textureData(textureHandle);
        boolean hasAlpha = textureBridge.textureHasAlpha(textureHandle);

        int textureId = GL46C.glGenTextures();
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);
        int textureSize = width * height * (hasAlpha ? 4 : 3);
        ByteBuffer textureBuffer = MemoryUtil.memAlloc(textureSize);
        try {
            textureBridge.copyTextureData(textureBuffer, textureData, textureSize);
            textureBuffer.rewind();
            if (hasAlpha) {
                GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 4);
                GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGBA, width, height, 0, GL46C.GL_RGBA, GL46C.GL_UNSIGNED_BYTE, textureBuffer);
            } else {
                GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 1);
                GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGB, width, height, 0, GL46C.GL_RGB, GL46C.GL_UNSIGNED_BYTE, textureBuffer);
            }
        } finally {
            MemoryUtil.memFree(textureBuffer);
            textureBridge.deleteTexture(textureHandle);
        }

        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_LEVEL, 0);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, GL46C.GL_LINEAR);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, GL46C.GL_LINEAR);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);

        result = new Texture();
        result.tex = textureId;
        result.hasAlpha = hasAlpha;
        result.vramSize = (long) width * height * (hasAlpha ? 4 : 3);
        localTextures.put(filename, result);
        return result;
    }

    private static Texture uploadPredecodedTexture(PredecodedTexture predecoded) {
        int textureId = GL46C.glGenTextures();
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, textureId);

        if (predecoded.hasAlpha) {
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 4);
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGBA,
                    predecoded.width, predecoded.height, 0,
                    GL46C.GL_RGBA, GL46C.GL_UNSIGNED_BYTE, predecoded.pixelData);
        } else {
            GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 1);
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGB,
                    predecoded.width, predecoded.height, 0,
                    GL46C.GL_RGB, GL46C.GL_UNSIGNED_BYTE, predecoded.pixelData);
        }

        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_LEVEL, 0);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, GL46C.GL_LINEAR);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, GL46C.GL_LINEAR);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);

        if (predecoded.pixelData != null) {
            MemoryUtil.memFree(predecoded.pixelData);
            predecoded.pixelData = null;
        }

        Texture result = new Texture();
        result.tex = textureId;
        result.hasAlpha = predecoded.hasAlpha;
        result.vramSize = (long) predecoded.width * predecoded.height * (predecoded.hasAlpha ? 4 : 3);
        return result;
    }

    public static void addRef(String filename) {
        Map<String, Texture> localTextures = textures;
        if (localTextures == null) {
            return;
        }

        Texture tex = localTextures.get(filename);
        if (tex != null) {
            tex.refCount.incrementAndGet();
        }
    }

    public static void release(String filename) {
        if (filename == null || textures == null) {
            return;
        }
        textures.compute(filename, (key, tex) -> {
            if (tex == null) {
                return null;
            }
            int remaining = tex.refCount.decrementAndGet();
            if (remaining <= 0) {
                tex.refCount.set(0);
                tex.lastReleaseTime = System.currentTimeMillis();
                pendingRelease.put(key, tex);
                return null;
            }
            return tex;
        });
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
        if (pendingRelease.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long budgetBytes = ConfigManager.getTextureCacheBudgetMB() * 1024L * 1024L;

        List<String> expired = new ArrayList<>();
        for (var entry : pendingRelease.entrySet()) {
            if (now - entry.getValue().lastReleaseTime > TEXTURE_TTL_MS) {
                expired.add(entry.getKey());
            }
        }
        for (String key : expired) {
            Texture tex = pendingRelease.remove(key);
            if (tex != null) {
                deleteGlTexture(tex);
            }
        }

        long pendingVram = getPendingReleaseVram();
        if (pendingVram > budgetBytes && !pendingRelease.isEmpty()) {
            evictByLRU(pendingVram, budgetBytes);
        }
    }

    private static synchronized void evictByLRU(long currentVram, long budgetBytes) {
        if (pendingRelease.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Texture>> sorted = new ArrayList<>(pendingRelease.entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue().lastReleaseTime, b.getValue().lastReleaseTime));

        long remaining = currentVram;
        for (var entry : sorted) {
            if (remaining <= budgetBytes) {
                break;
            }
            Texture tex = pendingRelease.remove(entry.getKey());
            if (tex != null) {
                remaining -= tex.vramSize;
                deleteGlTexture(tex);
            }
        }
    }

    private static void deleteGlTexture(Texture tex) {
        if (tex != null && tex.tex > 0) {
            GL46C.glDeleteTextures(tex.tex);
            tex.tex = 0;
        }
    }

    public static void Cleanup() {
        if (textures != null) {
            for (Texture tex : textures.values()) {
                deleteGlTexture(tex);
            }
            textures.clear();
        }
        for (Texture tex : pendingRelease.values()) {
            deleteGlTexture(tex);
        }
        pendingRelease.clear();
    }

    public static void DeleteTexture(String filename) {
        if (textures != null) {
            Texture tex = textures.remove(filename);
            deleteGlTexture(tex);
        }
        Texture pending = pendingRelease.remove(filename);
        deleteGlTexture(pending);
    }

    public static class Texture {
        public int tex;
        public boolean hasAlpha;
        public long vramSize;
        final AtomicInteger refCount = new AtomicInteger(0);
        volatile long lastReleaseTime;
    }

    public static long getTotalTextureVram() {
        if (textures == null) {
            return 0;
        }
        long total = 0;
        for (Texture tex : textures.values()) {
            total += tex.vramSize;
        }
        return total;
    }

    public static int getTextureCount() {
        return textures != null ? textures.size() : 0;
    }

    public static int getPendingReleaseCount() {
        return pendingRelease.size();
    }

    public static long getPendingReleaseVram() {
        long total = 0;
        for (Texture tex : pendingRelease.values()) {
            total += tex.vramSize;
        }
        return total;
    }

    static class PredecodedTexture {
        ByteBuffer pixelData;
        int width;
        int height;
        boolean hasAlpha;
    }
}
