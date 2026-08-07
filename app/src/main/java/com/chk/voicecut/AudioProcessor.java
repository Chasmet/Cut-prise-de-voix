package com.chk.voicecut;

import java.util.Arrays;

public final class AudioProcessor {
    private AudioProcessor() {}

    public static short[] slice(short[] source, int sampleRate, long startMs, long endMs) {
        int from = (int) Math.max(0, Math.min(source.length, Math.round(startMs * sampleRate / 1000.0)));
        int to = (int) Math.max(from + 1, Math.min(source.length, Math.round(endMs * sampleRate / 1000.0)));
        return Arrays.copyOfRange(source, from, to);
    }

    public static short[] process(short[] input, int sampleRate, boolean noiseReduction, boolean normalize, boolean smartGain) {
        if (input.length == 0) return input;
        double[] x = new double[input.length];
        double mean = 0;
        for (short s : input) mean += s;
        mean /= input.length;
        for (int i = 0; i < input.length; i++) x[i] = input[i] - mean;

        if (noiseReduction) {
            int frame = Math.max(1, sampleRate / 100);
            int frames = (x.length + frame - 1) / frame;
            double[] rms = new double[frames];
            for (int f = 0; f < frames; f++) {
                int from = f * frame, to = Math.min(x.length, from + frame);
                double sum = 0;
                for (int i = from; i < to; i++) sum += x[i] * x[i];
                rms[f] = Math.sqrt(sum / Math.max(1, to - from));
            }
            double[] sorted = rms.clone();
            Arrays.sort(sorted);
            double noise = sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.25))];
            double gate = Math.max(120.0, noise * 1.65);
            for (int f = 0; f < frames; f++) {
                double ratio = rms[f] / Math.max(1.0, gate);
                double gain = ratio >= 1.0 ? 1.0 : 0.28 + 0.72 * ratio;
                int from = f * frame, to = Math.min(x.length, from + frame);
                for (int i = from; i < to; i++) x[i] *= gain;
            }
        }

        double peak = 1.0, sumSq = 0;
        for (double v : x) { peak = Math.max(peak, Math.abs(v)); sumSq += v * v; }
        double rmsAll = Math.sqrt(sumSq / x.length);
        double gain = 1.0;
        if (smartGain && rmsAll > 1) gain = Math.min(Math.pow(10.0, 8.0 / 20.0), 6000.0 / rmsAll);
        if (normalize) {
            double peakTarget = 32768.0 * Math.pow(10.0, -1.0 / 20.0);
            gain = smartGain ? Math.min(gain, peakTarget / peak) : peakTarget / peak;
        }

        int fade = Math.max(1, sampleRate * 6 / 1000);
        short[] out = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            double v = x[i] * gain;
            double limit = 0.985 * 32767.0;
            if (Math.abs(v) > limit) {
                double sign = Math.signum(v), over = Math.abs(v) - limit;
                v = sign * (limit + over / (1.0 + over / 1500.0));
            }
            double fadeGain = 1.0;
            if (i < fade) fadeGain = Math.min(fadeGain, i / (double) fade);
            int remain = x.length - 1 - i;
            if (remain < fade) fadeGain = Math.min(fadeGain, remain / (double) fade);
            int s = (int) Math.round(v * fadeGain);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
        }
        return out;
    }
}
