package com.shiroha.mmdskin.renderer.runtime.texture;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ConfigManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

/**
 * MMD 纹理管理器。
 */
public class MMDTextureManager {
    private static final Logger logger = LogManager.getLogger();
    private static NativeFunc nf;

    private static volatile Map<String, Texture> textures;

    private static final Map<String, Texture> pendingRelease = new ConcurrentHashMap<>();

    private static final Map<String, PredecodedTexture> predecodedTextures = new ConcurrentHashMap<>();

    private static final long TEXTURE_TTL_MS = 60_000;

    public static void Init() {
        nf = NativeFunc.GetInst();
        textures = new ConcurrentHashMap<>();
        pendingRelease.clear();
    }

    public static void preloadTexture(String filename) {

        Map<String, Texture> localTextures = textures;
        if (localTextures == null) return;
        if (localTextures.containsKey(filename) || pendingRelease.containsKey(filename)
                || predecodedTextures.containsKey(filename)) {
            return;
        }

        NativeFunc localNf = NativeFunc.GetInst();
        long nfTex = localNf.LoadTexture(filename);
        if (nfTex == 0) {
            return;
        }

        try {
            int x = localNf.GetTextureX(nfTex);
            int y = localNf.GetTextureY(nfTex);
            long texData = localNf.GetTextureData(nfTex);
            boolean hasAlpha = localNf.TextureHasAlpha(nfTex);

            int texSize = x * y * (hasAlpha ? 4 : 3);
            ByteBuffer pixelBuffer = MemoryUtil.memAlloc(texSize);
            localNf.CopyDataToByteBuffer(pixelBuffer, texData, texSize);
            pixelBuffer.rewind();

            PredecodedTexture predecoded = new PredecodedTexture();
            predecoded.pixelData = pixelBuffer;
            predecoded.width = x;
            predecoded.height = y;
            predecoded.hasAlpha = hasAlpha;

            PredecodedTexture existing = predecodedTextures.putIfAbsent(filename, predecoded);
            if (existing != null) {
                MemoryUtil.memFree(pixelBuffer);
            }
        } finally {
            localNf.DeleteTexture(nfTex);
        }
    }

    public static void clearPreloaded() {

        for (PredecodedTexture p : predecodedTextures.values()) {
            if (p.pixelData != null) {
                MemoryUtil.memFree(p.pixelData);
                p.pixelData = null;
            }
        }
        predecodedTextures.clear();
    }

    public static Texture GetTexture(String filename) {

        Texture result = textures.get(filename);
        if (result != null) {
            return result;
        }

        result = pendingRelease.remove(filename);
        if (result != null) {
            result.refCount.set(0);
            textures.put(filename, result);
            return result;
        }

        PredecodedTexture predecoded = predecodedTextures.remove(filename);
        if (predecoded != null) {
            result = uploadPredecodedTexture(predecoded);
            textures.put(filename, result);
            return result;
        }

        long nfTex = nf.LoadTexture(filename);
        if (nfTex == 0) {
            return null;
        }
        int x = nf.GetTextureX(nfTex);
        int y = nf.GetTextureY(nfTex);
        long texData = nf.GetTextureData(nfTex);
        boolean hasAlpha = nf.TextureHasAlpha(nfTex);

        int tex = GL46C.glGenTextures();
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, tex);
        int texSize = x * y * (hasAlpha ? 4 : 3);
        ByteBuffer texBuffer = MemoryUtil.memAlloc(texSize);
        try {
            nf.CopyDataToByteBuffer(texBuffer, texData, texSize);
            texBuffer.rewind();
            if (hasAlpha) {
                GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 4);
                GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGBA, x, y, 0, GL46C.GL_RGBA, GL46C.GL_UNSIGNED_BYTE, texBuffer);
            } else {
                GL46C.glPixelStorei(GL46C.GL_UNPACK_ALIGNMENT, 1);
                GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGB, x, y, 0, GL46C.GL_RGB, GL46C.GL_UNSIGNED_BYTE, texBuffer);
            }
        } finally {
            MemoryUtil.memFree(texBuffer);
        }
        nf.DeleteTexture(nfTex);

        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_LEVEL, 0);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, GL46C.GL_LINEAR);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, GL46C.GL_LINEAR);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);

        result = new Texture();
        result.tex = tex;
        result.hasAlpha = hasAlpha;
        result.vramSize = (long) x * y * (hasAlpha ? 4 : 3);
        textures.put(filename, result);
        return result;
    }

    private static Texture uploadPredecodedTexture(PredecodedTexture predecoded) {
        int tex = GL46C.glGenTextures();
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, tex);

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
        result.tex = tex;
        result.hasAlpha = predecoded.hasAlpha;
        result.vramSize = (long) predecoded.width * predecoded.height * (predecoded.hasAlpha ? 4 : 3);
        return result;
    }

    public static void addRef(String filename) {
        Texture tex = textures.get(filename);
        if (tex != null) {
            tex.refCount.incrementAndGet();
        }
    }

    public static void release(String filename) {
        if (filename == null || textures == null) return;
        textures.compute(filename, (key, tex) -> {
            if (tex == null) return null;
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
        if (filenames == null) return;
        for (String filename : filenames) {
            release(filename);
        }
    }

    public static void tick() {
        if (pendingRelease.isEmpty()) return;

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
        if (pendingRelease.isEmpty()) return;

        List<Map.Entry<String, Texture>> sorted = new ArrayList<>(pendingRelease.entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue().lastReleaseTime, b.getValue().lastReleaseTime));

        long remaining = currentVram;
        int evicted = 0;
        for (var entry : sorted) {
            if (remaining <= budgetBytes) break;
            Texture tex = pendingRelease.remove(entry.getKey());
            if (tex != null) {
                remaining -= tex.vramSize;
                deleteGlTexture(tex);
                evicted++;
            }
        }
        if (evicted > 0) {
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
            int count = textures.size();
            for (Texture tex : textures.values()) {
                deleteGlTexture(tex);
            }
            textures.clear();
        }
        int pendingCount = pendingRelease.size();
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
        if (textures == null) return 0;
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
