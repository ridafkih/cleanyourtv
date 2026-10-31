package com.gathin.tvhome;

import android.os.SystemClock;
import android.view.View;

/**
 * Fades a view's alpha on the frame clock. ViewPropertyAnimator and ValueAnimator both follow the system's
 * animator_duration_scale, which a debloated TV often sets to 0: every fade then finishes in one frame as a hard cut.
 * This one reads elapsed time itself, so a slow cross-fade stays slow whatever that setting says.
 */
final class AlphaFade {
    private AlphaFade() {}

    static void to(View view, float target, long durationMs, Runnable end) {
        float from = view.getAlpha();
        long start = SystemClock.uptimeMillis();
        Runnable step = new Runnable() {
            @Override
            public void run() {
                if (view.getTag(R.id.alpha_fade_step) != this) return;
                float progress = durationMs <= 0 ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) durationMs);
                float eased = progress * progress * (3f - 2f * progress);
                view.setAlpha(from + (target - from) * eased);
                if (progress < 1f) {
                    view.postOnAnimation(this);
                    return;
                }
                view.setTag(R.id.alpha_fade_step, null);
                if (end != null) end.run();
            }
        };
        view.setTag(R.id.alpha_fade_step, step);
        view.postOnAnimation(step);
    }

    static void cancel(View view) {
        view.setTag(R.id.alpha_fade_step, null);
    }
}
