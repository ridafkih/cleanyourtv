package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * The sound that plays when focus moves or something is clicked. nav_sound in config.xml picks it: off (silent, the
 * default), default (Android's own click) or custom (the bundled raw/nav_click).
 */
final class NavSound {
    private static final String OFF = "off";
    private static final String DEFAULT = "default";
    private static final String CUSTOM = "custom";

    private final boolean systemSounds;
    private final boolean custom;
    private final SoundPool pool;
    private final int clickId;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener;
    private final View decor;

    static boolean systemSoundsEnabled(Context context) {
        String mode = context.getString(R.string.nav_sound);
        if (!OFF.equals(mode) && !DEFAULT.equals(mode) && !CUSTOM.equals(mode)) {
            throw new IllegalStateException("nav_sound must be off, default or custom, not " + mode);
        }
        return DEFAULT.equals(mode);
    }

    static void silence(Context context, View root) {
        if (systemSoundsEnabled(context)) return;
        silenceTree(root);
    }

    NavSound(Activity activity) {
        String mode = activity.getString(R.string.nav_sound);
        systemSounds = systemSoundsEnabled(activity);
        custom = CUSTOM.equals(mode);
        decor = activity.getWindow().getDecorView();
        if (!systemSounds) silenceTree(decor);
        if (!custom) {
            pool = null;
            clickId = 0;
            focusListener = null;
            return;
        }
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        clickId = pool.load(activity, R.raw.nav_click, 1);
        focusListener = (oldFocus, newFocus) -> {
            if (oldFocus != null && newFocus != null) pool.play(clickId, 1f, 1f, 1, 0, 1f);
        };
        decor.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
    }

    void vertical(View view, boolean up) {
        if (!systemSounds) return;
        view.playSoundEffect(up ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
    }

    void release() {
        if (pool == null) return;
        decor.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        pool.release();
    }

    private static void silenceTree(View view) {
        view.setSoundEffectsEnabled(false);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) silenceTree(group.getChildAt(i));
    }
}
