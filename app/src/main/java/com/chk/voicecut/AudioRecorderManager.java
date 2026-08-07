package com.chk.voicecut;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecorderManager {
    public interface Listener {
        void onLevel(double db, long elapsedMs);
        void onStopped(File wavFile, int sampleRate);
        void onError(Exception error);
    }

    private final Context context;
    private final Listener listener;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private Thread worker;
    private File outputFile;
    private int sampleRate = 44100;

    public AudioRecorderManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isRecording() { return recording.get(); }

    public void start(File wavFile) {
        if (recording.get()) return;
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError(new SecurityException("Permission microphone manquante"));
            return;
        }
        outputFile = wavFile;
        try {
            audioRecord = createRecorder();
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Impossible d'initialiser le microphone");
            recording.set(true);
            worker = new Thread(this::recordLoop, "VoiceCutRecorder");
            worker.start();
        } catch (Exception e) {
            releaseRecorder();
            listener.onError(e);
        }
    }

    public void stop() {
        recording.set(false);
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (IllegalStateException ignored) {}
        }
    }

    public void release() {
        stop();
        if (worker != null) {
            try { worker.join(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        releaseRecorder();
    }

    private AudioRecord createRecorder() throws IOException {
        int[] rates = {44100, 48000};
        for (int rate : rates) {
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) continue;
            int source = Build.VERSION.SDK_INT >= 24 ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.MIC;
            try {
                AudioRecord rec = new AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 4, rate));
                if (rec.getState() == AudioRecord.STATE_INITIALIZED) { sampleRate = rate; return rec; }
                rec.release();
            } catch (IllegalArgumentException ignored) {}
            try {
                AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 4, rate));
                if (rec.getState() == AudioRecord.STATE_INITIALIZED) { sampleRate = rate; return rec; }
                rec.release();
            } catch (IllegalArgumentException ignored) {}
        }
        throw new IOException("Aucun format micro PCM mono compatible trouvé");
    }

    private void recordLoop() {
        long pcmBytes = 0;
        long started = SystemClock.elapsedRealtime();
        short[] buffer = new short[Math.max(2048, sampleRate / 10)];
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            raf.setLength(0);
            WavFile.writeHeader(raf, sampleRate, 1, 16, 0);
            raf.seek(WavFile.HEADER_SIZE);
            audioRecord.startRecording();
            while (recording.get()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                double sum = 0.0;
                byte[] bytes = new byte[read * 2];
                for (int i = 0, p = 0; i < read; i++) {
                    short s = buffer[i];
                    sum += (double) s * s;
                    bytes[p++] = (byte) (s & 0xff);
                    bytes[p++] = (byte) ((s >> 8) & 0xff);
                }
                raf.write(bytes);
                pcmBytes += bytes.length;
                double rms = Math.sqrt(sum / Math.max(1, read));
                double db = rms <= 0.0 ? -100.0 : 20.0 * Math.log10(rms / 32768.0);
                listener.onLevel(db, SystemClock.elapsedRealtime() - started);
            }
            WavFile.writeHeader(raf, sampleRate, 1, 16, pcmBytes);
            listener.onStopped(outputFile, sampleRate);
        } catch (Exception e) {
            listener.onError(e);
        } finally {
            recording.set(false);
            releaseRecorder();
        }
    }

    private void releaseRecorder() {
        if (audioRecord != null) {
            try { audioRecord.release(); } catch (Exception ignored) {}
            audioRecord = null;
        }
    }
}
