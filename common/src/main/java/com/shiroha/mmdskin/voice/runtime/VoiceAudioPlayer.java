package com.shiroha.mmdskin.voice.runtime;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;

import static com.shiroha.mmdskin.voice.pack.VoicePackScanner.MAX_AUDIO_FILE_BYTES;

public class VoiceAudioPlayer {
    private static final Logger LOGGER = LogManager.getLogger();

    private int alBuffer;
    private int alSource;
    private boolean loaded;

    public boolean load(String filePath, float volume) {
        cleanup();
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        if (file.length() <= 0 || file.length() > MAX_AUDIO_FILE_BYTES) {
            return false;
        }
        PcmData pcm;
        try {
            String ext = getExtension(filePath).toLowerCase();
            if (!hasExpectedSignature(file, ext)) {
                return false;
            }
            pcm = switch (ext) {
                case "mp3" -> decodeMp3(file);
                case "ogg" -> decodeOgg(file);
                case "wav" -> decodeWav(file);
                default -> null;
            };
        } catch (Exception e) {
            LOGGER.error("读取语音音频失败: {}", filePath, e);
            return false;
        }
        if (pcm == null || pcm.data == null || pcm.data.remaining() == 0) {
            return false;
        }
        int format = getAlFormat(pcm.channels, pcm.bitsPerSample);
        if (format == 0) {
            MemoryUtil.memFree(pcm.data);
            return false;
        }
        alBuffer = AL10.alGenBuffers();
        AL10.alBufferData(alBuffer, format, pcm.data, pcm.sampleRate);
        int error = AL10.alGetError();
        MemoryUtil.memFree(pcm.data);
        if (error != AL10.AL_NO_ERROR) {
            cleanup();
            return false;
        }
        alSource = AL10.alGenSources();
        AL10.alSourcei(alSource, AL10.AL_BUFFER, alBuffer);
        AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(alSource, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        loaded = true;
        return true;
    }

    public void play() {
        if (!loaded) {
            return;
        }
        AL10.alSourceRewind(alSource);
        AL10.alSourcePlay(alSource);
    }

    public boolean isPlaying() {
        return loaded && AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
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
        loaded = false;
    }

    private record PcmData(ByteBuffer data, int sampleRate, int channels, int bitsPerSample) {
    }

    private static PcmData decodeMp3(File file) throws Exception {
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
        int sampleRate = 0;
        int channels = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            Bitstream bitstream = new Bitstream(input);
            Decoder decoder = new Decoder();
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                if (sampleRate == 0) {
                    sampleRate = header.frequency();
                    channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                }
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                short[] samples = output.getBuffer();
                int length = output.getBufferLength();
                for (int index = 0; index < length; index++) {
                    pcmOut.write(samples[index] & 0xFF);
                    pcmOut.write((samples[index] >> 8) & 0xFF);
                }
                bitstream.closeFrame();
            }
            bitstream.close();
        }
        byte[] pcmBytes = pcmOut.toByteArray();
        if (pcmBytes.length == 0 || sampleRate == 0) {
            return null;
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(pcmBytes.length);
        buffer.put(pcmBytes).flip();
        return new PcmData(buffer, sampleRate, channels, 16);
    }

    private static PcmData decodeOgg(File file) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        ByteBuffer fileBuffer = MemoryUtil.memAlloc(fileBytes.length);
        fileBuffer.put(fileBytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuffer = stack.mallocInt(1);
            IntBuffer sampleRateBuffer = stack.mallocInt(1);
            ShortBuffer decoded = STBVorbis.stb_vorbis_decode_memory(fileBuffer, channelsBuffer, sampleRateBuffer);
            MemoryUtil.memFree(fileBuffer);
            if (decoded == null) {
                return null;
            }
            ByteBuffer pcmBuffer = MemoryUtil.memAlloc(decoded.remaining() * 2);
            while (decoded.hasRemaining()) {
                short sample = decoded.get();
                pcmBuffer.put((byte) (sample & 0xFF));
                pcmBuffer.put((byte) ((sample >> 8) & 0xFF));
            }
            pcmBuffer.flip();
            MemoryUtil.memFree(decoded);
            return new PcmData(pcmBuffer, sampleRateBuffer.get(0), channelsBuffer.get(0), 16);
        }
    }

    private static PcmData decodeWav(File file) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
            AudioFormat sourceFormat = input.getFormat();
            AudioFormat targetFormat = sourceFormat;
            boolean convert = sourceFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED;
            AudioInputStream targetStream = convert
                    ? AudioSystem.getAudioInputStream(new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false
            ), input)
                    : input;
            try {
                if (convert) {
                    targetFormat = targetStream.getFormat();
                }
                byte[] pcmBytes = targetStream.readAllBytes();
                ByteBuffer buffer = MemoryUtil.memAlloc(pcmBytes.length);
                buffer.put(pcmBytes).flip();
                return new PcmData(buffer,
                        (int) targetFormat.getSampleRate(),
                        targetFormat.getChannels(),
                        targetFormat.getSampleSizeInBits());
            } finally {
                if (convert) {
                    targetStream.close();
                }
            }
        }
    }

    private static int getAlFormat(int channels, int bitsPerSample) {
        if (channels == 1) {
            if (bitsPerSample == 8) {
                return AL10.AL_FORMAT_MONO8;
            }
            if (bitsPerSample == 16) {
                return AL10.AL_FORMAT_MONO16;
            }
        }
        if (channels == 2) {
            if (bitsPerSample == 8) {
                return AL10.AL_FORMAT_STEREO8;
            }
            if (bitsPerSample == 16) {
                return AL10.AL_FORMAT_STEREO16;
            }
        }
        return 0;
    }

    private static String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : "";
    }

    private static boolean hasExpectedSignature(File file, String ext) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 4) {
                return false;
            }
            byte[] header = new byte[12];
            int read = raf.read(header);
            if (read < 4) {
                return false;
            }
            return switch (ext) {
                case "ogg" -> header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S';
                case "wav" -> read >= 12
                        && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                        && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E';
                case "mp3" -> (header[0] == 'I' && header[1] == 'D' && header[2] == '3')
                        || (read >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0);
                default -> false;
            };
        } catch (Exception e) {
            LOGGER.warn("读取语音文件头失败: {}", file.getAbsolutePath(), e);
            return false;
        }
    }
}
