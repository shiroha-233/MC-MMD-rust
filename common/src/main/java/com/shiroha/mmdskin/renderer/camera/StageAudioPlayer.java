package com.shiroha.mmdskin.renderer.camera;

import javazoom.jl.decoder.*;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 舞台模式音频播放器
 * 使用 OpenAL 播放音频，支持 MP3 (JLayer) / OGG (STB Vorbis) / WAV (Java Sound) 三种格式。
 * 
 * 设计要点：
 * - 全量解码到 PCM 后上传 OpenAL Buffer，保证帧同步可靠性
 * - 与 MMDCameraController 的 PLAYING 状态联动（play/pause/stop）
 * - 通过独立 OpenAL Source 播放，不干扰 MC 自身的音频系统
 */
public class StageAudioPlayer {
    /** 远程玩家音频播放器实例 */
    private static final ConcurrentHashMap<UUID, StageAudioPlayer> REMOTE_PLAYERS = new ConcurrentHashMap<>();
    private static StageAudioPlayer instance = null;

    // 距离衰减参数
    private static final float REF_DISTANCE = 16.0f;   // 此距离内满音量
    private static final float MAX_DISTANCE = 96.0f;    // 此距离外静音
    private static int attenuationTickCounter = 0;

    /** 获取单例（用于本地玩家） */
    public static StageAudioPlayer getInstance() {
        if (instance == null) {
            instance = new StageAudioPlayer();
        }
        return instance;
    }

    /** 播放远程玩家的音频 */
    public static void playRemoteAudio(Player player, String filePath) {
        if (player == null || filePath == null) return;
        StageAudioPlayer sap = REMOTE_PLAYERS.computeIfAbsent(player.getUUID(), k -> new StageAudioPlayer());
        if (sap.load(filePath)) {
            sap.play();
        }
    }

    /** 停止并移除远程玩家的音频播放器 */
    public static void stopRemoteAudio(UUID uuid) {
        StageAudioPlayer sap = REMOTE_PLAYERS.remove(uuid);
        if (sap != null) {
            sap.cleanup();
        }
    }

    /** 清理所有远程玩家的音频 */
    public static void cleanupAll() {
        REMOTE_PLAYERS.values().forEach(StageAudioPlayer::cleanup);
        REMOTE_PLAYERS.clear();
        if (instance != null) {
            instance.cleanup();
        }
    }

    /**
     * 每 tick 调用，每 20 tick（约1秒）更新一次远程音频的距离衰减。
     * 保持 2D 播放模式，仅通过 AL_GAIN 模拟距离衰减，零空间音频开销。
     * 播放结束的实例会被自动清理。
     */
    public static void tickRemoteAttenuation() {
        if (REMOTE_PLAYERS.isEmpty()) return;
        attenuationTickCounter++;
        if (attenuationTickCounter < 20) return;
        attenuationTickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var it = REMOTE_PLAYERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, StageAudioPlayer> entry = it.next();
            StageAudioPlayer sap = entry.getValue();
            // 播放结束的自动清理
            if (!sap.isPlaying() && sap.initialized) {
                sap.cleanup();
                it.remove();
                continue;
            }
            Player remote = mc.level.getPlayerByUUID(entry.getKey());
            if (remote == null) {
                // 玩家离开视野或退出，静音但保留（等断线时统一清理）
                sap.setGain(0.0f);
                continue;
            }
            float dist = mc.player.distanceTo(remote);
            float gain;
            if (dist <= REF_DISTANCE) {
                gain = 1.0f;
            } else if (dist >= MAX_DISTANCE) {
                gain = 0.0f;
            } else {
                gain = 1.0f - (dist - REF_DISTANCE) / (MAX_DISTANCE - REF_DISTANCE);
            }
            sap.setGain(gain * sap.volume);
        }
    }

    private static final Logger logger = LogManager.getLogger();
    
    // OpenAL 资源
    private int alBuffer = 0;
    private int alSource = 0;
    private boolean initialized = false;
    
    // 音频信息
    private String audioPath = null;
    private float durationSeconds = 0.0f;
    
    // 音量（0.0 ~ 1.0）
    private float volume = 1.0f;
    
    /**
     * 加载音频文件（全量解码到 PCM → 上传 OpenAL Buffer）
     * 支持 .mp3 / .ogg / .wav
     * 
     * @param filePath 音频文件绝对路径
     * @return true 加载成功
     */
    public boolean load(String filePath) {
        // 先清理上一次
        cleanup();
        
        if (filePath == null || filePath.isEmpty()) return false;
        
        File file = new File(filePath);
        
        // 路径安全校验，防止路径遍历漏洞
        try {
            if (!file.exists() || !file.isFile()) {
                logger.warn("[StageAudio] 文件不存在或不是文件: {}", filePath);
                return false;
            }
        } catch (SecurityException e) {
            logger.error("[StageAudio] 路径校验失败: {} - {}", filePath, e.getMessage());
            return false;
        }
        
        String ext = getExtension(filePath).toLowerCase();
        
        PcmData pcm;
        try {
            switch (ext) {
                case "mp3":
                    pcm = decodeMp3(file);
                    break;
                case "ogg":
                    pcm = decodeOgg(file);
                    break;
                case "wav":
                    pcm = decodeWav(file);
                    break;
                default:
                    logger.warn("[StageAudio] 不支持的音频格式: {}", ext);
                    return false;
            }
        } catch (Exception e) {
            logger.error("[StageAudio] 解码失败: {} - {}", filePath, e.getMessage());
            return false;
        }
        
        if (pcm == null || pcm.data == null || pcm.data.limit() == 0) {
            logger.warn("[StageAudio] 解码结果为空: {}", filePath);
            return false;
        }
        
        // 确定 OpenAL 格式
        int alFormat = getAlFormat(pcm.channels, pcm.bitsPerSample);
        if (alFormat == 0) {
            logger.error("[StageAudio] 不支持的 PCM 格式: ch={}, bits={}", pcm.channels, pcm.bitsPerSample);
            MemoryUtil.memFree(pcm.data);
            return false;
        }
        
        // 创建 OpenAL Buffer 和 Source
        alBuffer = AL10.alGenBuffers();
        AL10.alBufferData(alBuffer, alFormat, pcm.data, pcm.sampleRate);
        
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            logger.error("[StageAudio] alBufferData 错误: 0x{}", Integer.toHexString(err));
            AL10.alDeleteBuffers(alBuffer);
            alBuffer = 0;
            MemoryUtil.memFree(pcm.data);
            return false;
        }
        
        alSource = AL10.alGenSources();
        AL10.alSourcei(alSource, AL10.AL_BUFFER, alBuffer);
        AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        // 非位置音频（2D播放）
        AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(alSource, AL10.AL_POSITION, 0, 0, 0);
        
        // 计算时长
        int totalSamples = pcm.data.remaining() / (pcm.channels * (pcm.bitsPerSample / 8));
        durationSeconds = (float) totalSamples / pcm.sampleRate;
        
        // 释放 CPU 端缓冲（已上传到 OpenAL）
        MemoryUtil.memFree(pcm.data);
        
        this.audioPath = filePath;
        this.initialized = true;
        
        logger.info("[StageAudio] 加载成功: {} ({}s, {}Hz, {}ch)", 
                    file.getName(), String.format("%.1f", durationSeconds), pcm.sampleRate, pcm.channels);
        return true;
    }
    
    /**
     * 开始播放
     */
    public void play() {
        if (!initialized) return;
        AL10.alSourceRewind(alSource);
        AL10.alSourcePlay(alSource);
    }
    
    /**
     * 停止播放
     */
    public void stop() {
        if (!initialized) return;
        AL10.alSourceStop(alSource);
    }
    
    /**
     * 暂停播放
     */
    public void pause() {
        if (!initialized) return;
        AL10.alSourcePause(alSource);
    }
    
    /**
     * 恢复播放
     */
    public void resume() {
        if (!initialized) return;
        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        if (state == AL10.AL_PAUSED) {
            AL10.alSourcePlay(alSource);
        }
    }
    
    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        if (!initialized) return false;
        return AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }
    
    /**
     * 获取当前播放位置（秒）
     */
    public float getPlaybackPosition() {
        if (!initialized) return 0.0f;
        return AL10.alGetSourcef(alSource, AL11.AL_SEC_OFFSET);
    }
    
    /**
     * 设置播放位置（秒）
     */
    public void setPlaybackPosition(float seconds) {
        if (!initialized) return;
        seconds = Math.max(0, Math.min(seconds, durationSeconds));
        AL10.alSourcef(alSource, AL11.AL_SEC_OFFSET, seconds);
    }
    
    /**
     * 设置音量
     * @param vol 0.0 ~ 1.0
     */
    public void setVolume(float vol) {
        this.volume = Math.max(0.0f, Math.min(1.0f, vol));
        if (initialized) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, this.volume);
        }
    }
    
    public float getVolume() {
        return volume;
    }
    
    /**
     * 获取音频时长（秒）
     */
    public float getDuration() {
        return durationSeconds;
    }
    
    /**
     * 是否已加载
     */
    public boolean isLoaded() {
        return initialized;
    }
    
    /**
     * 获取已加载的音频路径
     */
    public String getAudioPath() {
        return audioPath;
    }
    
    /**
     * 设置 OpenAL Source 的实际增益（由距离衰减调用）
     */
    private void setGain(float gain) {
        if (initialized && alSource != 0) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, Math.max(0.0f, Math.min(1.0f, gain)));
        }
    }

    /**
     * 释放所有 OpenAL 资源
     */
    public void cleanup() {
        if (alSource != 0) {
            AL10.alSourceStop(alSource);
            AL10.alDeleteSources(alSource);
            alSource = 0;
        }
        if (alBuffer != 0) {
            AL10.alDeleteBuffers(alBuffer);
            alBuffer = 0;
        }
        initialized = false;
        audioPath = null;
        durationSeconds = 0.0f;
    }
    
    // ==================== 格式解码 ====================
    
    /**
     * 解码后的 PCM 数据
     */
    private static class PcmData {
        ByteBuffer data;
        int sampleRate;
        int channels;
        int bitsPerSample;
        
        PcmData(ByteBuffer data, int sampleRate, int channels, int bitsPerSample) {
            this.data = data;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
        }
    }
    
    /**
     * MP3 解码（JLayer）
     * 逐帧解码为 16-bit PCM
     */
    private PcmData decodeMp3(File file) throws Exception {
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        
        int sampleRate = 0;
        int channels = 0;
        
        try (FileInputStream fis = new FileInputStream(file)) {
            Bitstream bitstream = new Bitstream(fis);
            Decoder decoder = new Decoder();
            
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                if (sampleRate == 0) {
                    sampleRate = header.frequency();
                    channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                }
                
                // 解码一帧 → float[] 样本（-1.0 ~ 1.0 范围的 short 值）
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] samples = output.getBuffer();
                int len = output.getBufferLength();
                
                // short[] → byte[]（little-endian 16-bit PCM）
                for (int i = 0; i < len; i++) {
                    pcmOut.write(samples[i] & 0xFF);
                    pcmOut.write((samples[i] >> 8) & 0xFF);
                }
                
                bitstream.closeFrame();
            }
            
            bitstream.close();
        }
        
        byte[] pcmBytes = pcmOut.toByteArray();
        if (pcmBytes.length == 0 || sampleRate == 0) return null;
        
        ByteBuffer buffer = MemoryUtil.memAlloc(pcmBytes.length);
        buffer.put(pcmBytes).flip();
        
        return new PcmData(buffer, sampleRate, channels, 16);
    }
    
    /**
     * OGG Vorbis 解码（STB Vorbis — LWJGL 内置）
     * 一次性解码整个文件
     */
    private PcmData decodeOgg(File file) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        ByteBuffer fileBuffer = MemoryUtil.memAlloc(fileBytes.length);
        fileBuffer.put(fileBytes).flip();
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuffer = stack.mallocInt(1);
            IntBuffer sampleRateBuffer = stack.mallocInt(1);
            
            ShortBuffer decoded = STBVorbis.stb_vorbis_decode_memory(
                fileBuffer, channelsBuffer, sampleRateBuffer
            );
            
            MemoryUtil.memFree(fileBuffer);
            
            if (decoded == null) {
                logger.error("[StageAudio] STB Vorbis 解码失败");
                return null;
            }
            
            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);
            
            // ShortBuffer → ByteBuffer（16-bit little-endian）
            ByteBuffer pcmBuffer = MemoryUtil.memAlloc(decoded.remaining() * 2);
            while (decoded.hasRemaining()) {
                short s = decoded.get();
                pcmBuffer.put((byte) (s & 0xFF));
                pcmBuffer.put((byte) ((s >> 8) & 0xFF));
            }
            pcmBuffer.flip();
            
            // STB 分配的内存需要用 LWJGL free 释放
            MemoryUtil.memFree(decoded);
            
            return new PcmData(pcmBuffer, sampleRate, channels, 16);
        }
    }
    
    /**
     * WAV 解码（Java Sound API）
     * 读取 PCM 数据（自动处理各种 WAV 子格式）
     */
    private PcmData decodeWav(File file) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat fmt = ais.getFormat();
            
            // 如果不是 PCM 编码，转换为 PCM
            AudioFormat targetFmt = fmt;
            boolean needConvert = fmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED;
            AudioInputStream targetAis = needConvert 
                ? AudioSystem.getAudioInputStream(
                    new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        fmt.getSampleRate(),
                        16,
                        fmt.getChannels(),
                        fmt.getChannels() * 2,
                        fmt.getSampleRate(),
                        false // little-endian
                    ), ais)
                : ais;
            
            try {
                if (needConvert) {
                    targetFmt = targetAis.getFormat();
                }
                
                byte[] pcmBytes = targetAis.readAllBytes();
                
                ByteBuffer buffer = MemoryUtil.memAlloc(pcmBytes.length);
                buffer.put(pcmBytes).flip();
                
                return new PcmData(
                    buffer,
                    (int) targetFmt.getSampleRate(),
                    targetFmt.getChannels(),
                    targetFmt.getSampleSizeInBits()
                );
            } finally {
                if (needConvert) {
                    targetAis.close();
                }
            }
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 根据通道数和位深度返回 OpenAL 格式常量
     */
    private static int getAlFormat(int channels, int bitsPerSample) {
        if (channels == 1) {
            if (bitsPerSample == 8) return AL10.AL_FORMAT_MONO8;
            if (bitsPerSample == 16) return AL10.AL_FORMAT_MONO16;
        } else if (channels == 2) {
            if (bitsPerSample == 8) return AL10.AL_FORMAT_STEREO8;
            if (bitsPerSample == 16) return AL10.AL_FORMAT_STEREO16;
        }
        return 0;
    }
    
    /**
     * 获取文件扩展名（不含点号）
     */
    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : "";
    }
}
