package com.chk.voicecut;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavFile {
    public static final int HEADER_SIZE = 44;
    private WavFile() {}

    public static void writeHeader(RandomAccessFile raf, int sampleRate, int channels, int bitsPerSample, long pcmBytes) throws IOException {
        long byteRate = (long) sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        raf.seek(0);
        raf.writeBytes("RIFF");
        writeIntLE(raf, (int) (36 + pcmBytes));
        raf.writeBytes("WAVE");
        raf.writeBytes("fmt ");
        writeIntLE(raf, 16);
        writeShortLE(raf, (short) 1);
        writeShortLE(raf, (short) channels);
        writeIntLE(raf, sampleRate);
        writeIntLE(raf, (int) byteRate);
        writeShortLE(raf, (short) blockAlign);
        writeShortLE(raf, (short) bitsPerSample);
        raf.writeBytes("data");
        writeIntLE(raf, (int) pcmBytes);
    }

    public static AudioData read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            byte[] header = new byte[HEADER_SIZE];
            in.readFully(header);
            ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') throw new IOException("Fichier WAV invalide");
            int channels = bb.getShort(22) & 0xffff;
            int sampleRate = bb.getInt(24);
            int bits = bb.getShort(34) & 0xffff;
            int dataSize = bb.getInt(40);
            if (bits != 16 || channels != 1) throw new IOException("VoiceCut attend un WAV mono 16 bits");
            int sampleCount = dataSize / 2;
            short[] samples = new short[sampleCount];
            byte[] raw = new byte[dataSize];
            in.readFully(raw);
            ByteBuffer pcm = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < sampleCount; i++) samples[i] = pcm.getShort();
            return new AudioData(sampleRate, samples);
        }
    }

    public static File write(File file, int sampleRate, short[] samples) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(0);
            writeHeader(raf, sampleRate, 1, 16, samples.length * 2L);
            raf.seek(HEADER_SIZE);
            byte[] buf = new byte[8192];
            int p = 0;
            for (short sample : samples) {
                buf[p++] = (byte) (sample & 0xff);
                buf[p++] = (byte) ((sample >> 8) & 0xff);
                if (p >= buf.length - 1) { raf.write(buf, 0, p); p = 0; }
            }
            if (p > 0) raf.write(buf, 0, p);
        }
        return file;
    }

    private static void writeIntLE(RandomAccessFile raf, int value) throws IOException {
        raf.write(value & 0xff); raf.write((value >> 8) & 0xff); raf.write((value >> 16) & 0xff); raf.write((value >> 24) & 0xff);
    }

    private static void writeShortLE(RandomAccessFile raf, short value) throws IOException {
        raf.write(value & 0xff); raf.write((value >> 8) & 0xff);
    }

    public static final class AudioData {
        public final int sampleRate;
        public final short[] samples;
        public AudioData(int sampleRate, short[] samples) { this.sampleRate = sampleRate; this.samples = samples; }
        public long durationMs() { return Math.round(samples.length * 1000.0 / sampleRate); }
    }
}
