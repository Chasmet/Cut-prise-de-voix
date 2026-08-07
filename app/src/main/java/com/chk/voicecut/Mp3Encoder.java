package com.chk.voicecut;

import com.naman14.androidlame.AndroidLame;
import com.naman14.androidlame.LameBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class Mp3Encoder {
    private Mp3Encoder() {}

    public static void encodeMono(short[] samples, int inputSampleRate, int bitrateKbps, File output) throws IOException {
        AndroidLame lame = null;
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output), 32 * 1024)) {
            lame = new LameBuilder()
                    .setInSampleRate(inputSampleRate)
                    .setOutChannels(1)
                    .setOutBitrate(bitrateKbps)
                    .setOutSampleRate(44100)
                    .setQuality(2)
                    .build();
            final int chunk = 8192;
            byte[] mp3 = new byte[(int) (7200 + chunk * 2 * 1.25)];
            int offset = 0;
            while (offset < samples.length) {
                int count = Math.min(chunk, samples.length - offset);
                short[] pcm = Arrays.copyOfRange(samples, offset, offset + count);
                int encoded = lame.encode(pcm, pcm, count, mp3);
                if (encoded < 0) throw new IOException("Erreur LAME : " + encoded);
                if (encoded > 0) out.write(mp3, 0, encoded);
                offset += count;
            }
            int flushed = lame.flush(mp3);
            if (flushed < 0) throw new IOException("Erreur de finalisation LAME : " + flushed);
            if (flushed > 0) out.write(mp3, 0, flushed);
        } finally {
            if (lame != null) lame.close();
        }
    }
}
