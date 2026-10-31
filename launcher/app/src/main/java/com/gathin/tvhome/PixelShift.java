package com.gathin.tvhome;

import android.content.res.Resources;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * Burn-in prevention: drifts one container a few pixels along a slow Lissajous path, so no static edge sits on the
 * same pixels for long. The position follows the clock, not a counter, so it never accumulates error, and it is
 * applied on the frame clock because the animator duration scale on many TVs is 0.
 */
final class PixelShift {
    private final View target;
    private final boolean enabled;
    private final int amplitudeX;
    private final int amplitudeY;
    private final double periodX;
    private final double periodY;
    private final long intervalMs;
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Choreographer.FrameCallback tick = frameTimeNanos -> step();
    private boolean running;

    PixelShift(View target, Resources resources) {
        this.target = target;
        enabled = resources.getBoolean(R.bool.pixel_shift_enabled);
        amplitudeX = resources.getInteger(R.integer.pixel_shift_amplitude_x_px);
        amplitudeY = resources.getInteger(R.integer.pixel_shift_amplitude_y_px);
        periodX = resources.getInteger(R.integer.pixel_shift_period_x_seconds);
        periodY = resources.getInteger(R.integer.pixel_shift_period_y_seconds);
        intervalMs = resources.getInteger(R.integer.pixel_shift_interval_ms);
    }

    void start() {
        if (!enabled || running) return;
        running = true;
        choreographer.postFrameCallback(tick);
    }

    void stop() {
        running = false;
        choreographer.removeFrameCallback(tick);
    }

    private void step() {
        if (!running) return;
        double seconds = SystemClock.elapsedRealtime() / 1000.0;
        float x = Math.round(amplitudeX * Math.sin(2 * Math.PI * seconds / periodX));
        float y = Math.round(amplitudeY * Math.sin(2 * Math.PI * seconds / periodY));
        if (target.getTranslationX() != x) target.setTranslationX(x);
        if (target.getTranslationY() != y) target.setTranslationY(y);
        choreographer.postFrameCallbackDelayed(tick, intervalMs);
    }
}
