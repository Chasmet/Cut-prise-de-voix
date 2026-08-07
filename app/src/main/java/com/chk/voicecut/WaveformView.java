package com.chk.voicecut;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class WaveformView extends View {
    public interface SelectionListener {
        void onSelectionChanged(long startMs, long endMs, boolean fromUser);
        void onSeek(long positionMs);
    }

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private short[] samples = new short[0];
    private int sampleRate = 44100;
    private long durationMs = 0, selectionStartMs = 0, selectionEndMs = 0, playheadMs = -1, visibleStartMs = 0;
    private float zoom = 1f, lastX;
    private int dragMode = 0;
    private SelectionListener listener;

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        wavePaint.setColor(ContextCompat.getColor(context, R.color.green));
        wavePaint.setStrokeWidth(dp(1.2f));
        centerPaint.setColor(ContextCompat.getColor(context, R.color.stroke));
        centerPaint.setStrokeWidth(dp(1f));
        selectionPaint.setColor(ContextCompat.getColor(context, R.color.selection));
        markerPaint.setColor(ContextCompat.getColor(context, R.color.accent));
        markerPaint.setStrokeWidth(dp(3f));
        playheadPaint.setColor(ContextCompat.getColor(context, R.color.warning));
        playheadPaint.setStrokeWidth(dp(2f));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (durationMs <= 0) return false;
                long before = xToTime(detector.getFocusX());
                zoom = clamp(zoom * detector.getScaleFactor(), 1f, 20f);
                long visible = visibleDuration();
                float ratio = getWidth() <= 0 ? 0.5f : detector.getFocusX() / getWidth();
                visibleStartMs = clampLong(before - Math.round(visible * ratio), 0, Math.max(0, durationMs - visible));
                invalidate();
                return true;
            }
        });
    }

    public void setSelectionListener(SelectionListener listener) { this.listener = listener; }

    public void setAudio(short[] samples, int sampleRate) {
        this.samples = samples == null ? new short[0] : samples;
        this.sampleRate = sampleRate;
        this.durationMs = this.samples.length == 0 ? 0 : Math.round(this.samples.length * 1000.0 / sampleRate);
        selectionStartMs = 0; selectionEndMs = durationMs; playheadMs = -1; zoom = 1f; visibleStartMs = 0;
        invalidate();
    }

    public void setSelection(long startMs, long endMs) {
        selectionStartMs = clampLong(startMs, 0, durationMs);
        selectionEndMs = clampLong(endMs, selectionStartMs, durationMs);
        invalidate();
    }

    public void setPlayheadMs(long playheadMs) { this.playheadMs = playheadMs; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float centerY = getHeight() / 2f;
        canvas.drawLine(0, centerY, getWidth(), centerY, centerPaint);
        if (samples.length == 0 || durationMs <= 0) return;
        long visibleEnd = Math.min(durationMs, visibleStartMs + visibleDuration());
        int firstSample = (int) Math.max(0, Math.round(visibleStartMs * sampleRate / 1000.0));
        int lastSample = (int) Math.min(samples.length, Math.round(visibleEnd * sampleRate / 1000.0));
        int span = Math.max(1, lastSample - firstSample);
        int columns = Math.max(1, getWidth());
        for (int x = 0; x < columns; x++) {
            int from = firstSample + (int) ((long) x * span / columns);
            int to = firstSample + (int) ((long) (x + 1) * span / columns);
            to = Math.min(lastSample, Math.max(from + 1, to));
            int peak = 0;
            for (int i = from; i < to; i++) peak = Math.max(peak, Math.abs(samples[i]));
            float half = peak / 32768f * (getHeight() * 0.43f);
            canvas.drawLine(x, centerY - half, x, centerY + half, wavePaint);
        }
        float sx = timeToX(selectionStartMs), ex = timeToX(selectionEndMs);
        canvas.drawRect(new RectF(Math.max(0, sx), 0, Math.min(getWidth(), ex), getHeight()), selectionPaint);
        if (sx >= 0 && sx <= getWidth()) canvas.drawLine(sx, 0, sx, getHeight(), markerPaint);
        if (ex >= 0 && ex <= getWidth()) canvas.drawLine(ex, 0, ex, getHeight(), markerPaint);
        if (playheadMs >= visibleStartMs && playheadMs <= visibleEnd) canvas.drawLine(timeToX(playheadMs), 0, timeToX(playheadMs), getHeight(), playheadPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (durationMs <= 0) return true;
        float x = event.getX();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                float sx = timeToX(selectionStartMs), ex = timeToX(selectionEndMs), hit = dp(24);
                dragMode = Math.abs(x - sx) <= hit ? 1 : Math.abs(x - ex) <= hit ? 2 : 3;
                lastX = x;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) return true;
                if (dragMode == 1) { selectionStartMs = clampLong(xToTime(x), 0, Math.max(0, selectionEndMs - 1)); notifySelection(true); }
                else if (dragMode == 2) { selectionEndMs = clampLong(xToTime(x), Math.min(durationMs, selectionStartMs + 1), durationMs); notifySelection(true); }
                else if (dragMode == 3 && zoom > 1f) {
                    long visible = visibleDuration();
                    long delta = Math.round((lastX - x) / Math.max(1f, getWidth()) * visible);
                    visibleStartMs = clampLong(visibleStartMs + delta, 0, Math.max(0, durationMs - visible));
                }
                lastX = x; invalidate(); return true;
            case MotionEvent.ACTION_UP:
                if (dragMode == 3 && listener != null) listener.onSeek(xToTime(x));
                dragMode = 0; getParent().requestDisallowInterceptTouchEvent(false); return true;
            case MotionEvent.ACTION_CANCEL:
                dragMode = 0; getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
    }

    private void notifySelection(boolean fromUser) { if (listener != null) listener.onSelectionChanged(selectionStartMs, selectionEndMs, fromUser); }
    private long visibleDuration() { return Math.max(1, Math.round(durationMs / zoom)); }
    private float timeToX(long timeMs) { return (timeMs - visibleStartMs) * getWidth() / (float) visibleDuration(); }
    private long xToTime(float x) { return clampLong(visibleStartMs + Math.round(clamp(x, 0, getWidth()) / Math.max(1f, getWidth()) * visibleDuration()), 0, durationMs); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static long clampLong(long v, long min, long max) { return Math.max(min, Math.min(max, v)); }
}
