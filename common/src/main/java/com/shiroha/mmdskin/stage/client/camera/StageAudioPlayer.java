package com.shiroha.mmdskin.stage.client.camera;

import javazoom.jl.decoder.*;
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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;

/** 负责舞台模式音频的解码、加载与 OpenAL 播放控制。 */
public class StageAudioPlayer {
    private static final Logger logger = LogManager.getLogger();

    private int alBuffer = 0;
    private int alSource = 0;
    private boolean initialized = false;

    private String audioPath = null;
    private float durationSeconds = 0.0f;

    private float volume = 1.0f;

    public boolean load(String filePath) {
        cleanup();
        
        if (filePath == null || filePath.isEmpty()) return false;
        
        File file = new File(filePath);
        
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
        
        int alFormat = getAlFormat(pcm.channels, pcm.bitsPerSample);
        if (alFormat == 0) {
            logger.error("[StageAudio] 不支持的 PCM 格式: ch={}, bits={}", pcm.channels, pcm.bitsPerSample);
            MemoryUtil.memFree(pcm.data);
            return false;
        }
        
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
        AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(alSource, AL10.AL_POSITION, 0, 0, 0);

        int totalSamples = pcm.data.remaining() / (pcm.channels * (pcm.bitsPerSample / 8));
        durationSeconds = (float) totalSamples / pcm.sampleRate;

        MemoryUtil.memFree(pcm.data);
        
        this.audioPath = filePath;
        this.initialized = true;
        
        return true;
    }
    
    public void play() {
        if (!initialized) return;
        AL10.alSourceRewind(alSource);
        AL10.alSourcePlay(alSource);
    }
    
    public void stop() {
        if (!initialized) return;
        AL10.alSourceStop(alSource);
    }
    
    public void pause() {
        if (!initialized) return;
        AL10.alSourcePause(alSource);
    }
    
    public void resume() {
        if (!initialized) return;
        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        if (state == AL10.AL_PAUSED) {
            AL10.alSourcePlay(alSource);
        }
    }
    
    public boolean isPlaying() {
        if (!initialized) return false;
        return AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }
    
    public float getPlaybackPosition() {
        if (!initialized) return 0.0f;
        return AL10.alGetSourcef(alSource, AL11.AL_SEC_OFFSET);
    }
    
    public void setPlaybackPosition(float seconds) {
        if (!initialized) return;
        seconds = Math.max(0, Math.min(seconds, durationSeconds));
        AL10.alSourcef(alSource, AL11.AL_SEC_OFFSET, seconds);
    }
    
    public void setVolume(float vol) {
        this.volume = Math.max(0.0f, Math.min(1.0f, vol));
        if (initialized) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, this.volume);
        }
    }
    
    public float getVolume() {
        return volume;
    }
    
    public float getDuration() {
        return durationSeconds;
    }
    
    public boolean isLoaded() {
        return initialized;
    }
    
    public String getAudioPath() {
        return audioPath;
    }
    
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
                
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] samples = output.getBuffer();
                int len = output.getBufferLength();

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
            
            ByteBuffer pcmBuffer = MemoryUtil.memAlloc(decoded.remaining() * 2);
            while (decoded.hasRemaining()) {
                short s = decoded.get();
                pcmBuffer.put((byte) (s & 0xFF));
                pcmBuffer.put((byte) ((s >> 8) & 0xFF));
            }
            pcmBuffer.flip();
            
            MemoryUtil.memFree(decoded);
            
            return new PcmData(pcmBuffer, sampleRate, channels, 16);
        }
    }
    
    private PcmData decodeWav(File file) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat fmt = ais.getFormat();
            
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
                        false
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
    
    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : "";
    }
}
