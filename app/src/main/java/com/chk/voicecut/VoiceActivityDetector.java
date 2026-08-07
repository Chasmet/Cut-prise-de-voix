package com.chk.voicecut;

import java.util.Arrays;

public final class VoiceActivityDetector {
    private VoiceActivityDetector() {}

    public static Result detect(short[] samples, int sampleRate, int preMarginMs, int postMarginMs) {
        if (samples.length == 0) return new Result(0, 0, -90, -60);
        int frameMs = 10;
        int frameSize = Math.max(1, sampleRate * frameMs / 1000);
        int frames = (samples.length + frameSize - 1) / frameSize;
        double[] db = new double[frames];
        for (int f = 0; f < frames; f++) {
            int from = f * frameSize;
            int to = Math.min(samples.length, from + frameSize);
            double sum = 0;
            for (int i = from; i < to; i++) { double v = samples[i] / 32768.0; sum += v * v; }
            double rms = Math.sqrt(sum / Math.max(1, to - from));
            db[f] = rms <= 1e-9 ? -100.0 : 20.0 * Math.log10(rms);
        }
        double[] sorted = db.clone();
        Arrays.sort(sorted);
        double noiseFloor = sorted[Math.min(sorted.length - 1, Math.max(0, (int) (sorted.length * 0.25)))];
        double threshold = clamp(noiseFloor + 10.0, -48.0, -24.0);
        double releaseThreshold = threshold - 4.0;
        boolean[] active = new boolean[frames];
        boolean inSpeech = false;
        int hotRun = 0, coldRun = 0;
        for (int i = 0; i < frames; i++) {
            if (!inSpeech) {
                hotRun = db[i] >= threshold ? hotRun + 1 : 0;
                if (hotRun >= 2) {
                    inSpeech = true;
                    for (int j = Math.max(0, i - hotRun + 1); j <= i; j++) active[j] = true;
                }
            } else {
                active[i] = true;
                if (db[i] < releaseThreshold) coldRun++; else coldRun = 0;
                if (coldRun >= 10) {
                    inSpeech = false;
                    int clearFrom = Math.max(0, i - coldRun + 1);
                    for (int j = clearFrom; j <= i; j++) active[j] = false;
                    hotRun = 0; coldRun = 0;
                }
            }
        }
        for (int i = 1; i < frames - 1; i++) if (!active[i] && active[i - 1] && active[i + 1]) active[i] = true;
        int first = -1, last = -1;
        for (int i = 0; i < frames; i++) if (active[i]) { if (first < 0) first = i; last = i; }
        if (first < 0) return new Result(0, samples.length, noiseFloor, threshold);
        first = Math.max(0, first - Math.max(0, preMarginMs / frameMs));
        last = Math.min(frames - 1, last + Math.max(0, postMarginMs / frameMs));
        int startSample = Math.min(samples.length, first * frameSize);
        int endSample = Math.min(samples.length, (last + 1) * frameSize);
        return new Result(startSample, Math.max(startSample + 1, endSample), noiseFloor, threshold);
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    public static final class Result {
        public final int startSample, endSample;
        public final double noiseFloorDb, thresholdDb;
        public Result(int startSample, int endSample, double noiseFloorDb, double thresholdDb) {
            this.startSample = startSample; this.endSample = endSample; this.noiseFloorDb = noiseFloorDb; this.thresholdDb = thresholdDb;
        }
        public long startMs(int sampleRate) { return Math.round(startSample * 1000.0 / sampleRate); }
        public long endMs(int sampleRate) { return Math.round(endSample * 1000.0 / sampleRate); }
    }
}
