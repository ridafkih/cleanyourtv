package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;
import android.util.TypedValue;

import org.json.JSONObject;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * The sound that plays when focus moves or something is clicked. nav_sound in config.xml picks it: off (silent, the
 * default), default (Android's own click) or custom (the bundled cuelume cues).
 */
final class NavSound {
    private static final String OFF = "off";
    private static final String DEFAULT = "default";
    private static final String CUSTOM = "custom";

    private final boolean systemSounds;
    private final boolean custom;
    private final float focusVolume;
    private final float selectVolume;
    private final long selectMs;
    private final SoundPool pool;
    private final int focusId;
    private final int selectId;
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

    static void muteSystemSounds(Context context) {
        if (systemSoundsEnabled(context) || !context.getResources().getBoolean(R.bool.mute_system_sound_effects)) return;
        if (!Settings.System.canWrite(context)) return;
        Settings.System.putInt(context.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 0);
    }

    static float volume(Context context, int resource, String key) {
        TypedValue value = new TypedValue();
        context.getResources().getValue(resource, value, true);
        float volume = value.getFloat();
        JSONObject overlay = Config.overlay(context);
        if (overlay != null && overlay.has(key)) volume = (float) overlay.optDouble(key, Double.NaN);
        if (!(volume >= 0f && volume <= 1f)) {
            throw new IllegalStateException(key + " must be between 0 and 1, not " + volume);
        }
        return volume;
    }

    NavSound(Activity activity) {
        String mode = activity.getString(R.string.nav_sound);
        systemSounds = systemSoundsEnabled(activity);
        custom = CUSTOM.equals(mode);
        focusVolume = custom ? volume(activity, R.dimen.nav_sound_volume, "nav_sound_volume") : 0f;
        selectVolume = custom ? volume(activity, R.dimen.nav_sound_select_volume, "nav_sound_select_volume") : 0f;
        selectMs = custom ? activity.getResources().getInteger(R.integer.nav_sound_select_ms) : 0;
        muteSystemSounds(activity);
        decor = activity.getWindow().getDecorView();
        if (!systemSounds) silenceTree(decor);
        if (!custom) {
            pool = null;
            focusId = 0;
            selectId = 0;
            focusListener = null;
            return;
        }
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        focusId = pool.load(activity, R.raw.cue_tap, 1);
        selectId = pool.load(activity, R.raw.cue_select, 1);
        focusListener = (oldFocus, newFocus) -> {
            if (oldFocus != null && newFocus != null) pool.play(focusId, focusVolume, focusVolume, 1, 0, 1f);
        };
        decor.getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
    }

    void vertical(View view, boolean up) {
        if (!systemSounds) return;
        view.playSoundEffect(up ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
    }

    void select() {
        if (pool != null) pool.play(selectId, selectVolume, selectVolume, 1, 0, 1f);
    }

    long selectSoundMs() {
        return selectMs;
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
