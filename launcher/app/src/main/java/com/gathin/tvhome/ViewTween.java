package com.gathin.tvhome;

import android.os.SystemClock;
import android.view.View;

/**
 * Scale and slide tweens on the frame clock, for the same reason as {@link AlphaFade}: the TV's animator duration
 * scale is 0, which turns every ViewPropertyAnimator into a one-frame jump. Eased out, so a focus change lands softly.
 */
final class ViewTween {
    private interface Setter {
        void set(float value);
    }

    private ViewTween() {}

    static void scale(View view, float target, long durationMs) {
        run(view, R.id.scale_tween_step, view.getScaleX(), target, durationMs, value -> {
            view.setScaleX(value);
            view.setScaleY(value);
        });
    }

    static void translationX(View view, float target, long durationMs) {
        run(view, R.id.translation_tween_step, view.getTranslationX(), target, durationMs, view::setTranslationX);
    }

    static void cancelScale(View view) {
        view.setTag(R.id.scale_tween_step, null);
    }

    private static void run(View view, int tag, float from, float target, long durationMs, Setter setter) {
        long start = SystemClock.uptimeMillis();
        Runnable step = new Runnable() {
            @Override
            public void run() {
                if (view.getTag(tag) != this) return;
                float progress = durationMs <= 0 ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) durationMs);
                float remaining = 1f - progress;
                setter.set(from + (target - from) * (1f - remaining * remaining * remaining));
                if (progress < 1f) view.postOnAnimation(this);
                else view.setTag(tag, null);
            }
        };
        view.setTag(tag, step);
        view.postOnAnimation(step);
    }
}
