package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;
import android.util.Log;
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
    private static final String TAG = "NavSound";
    private static final float MIN_RATE = 0.5f;
    private static final float MAX_RATE = 2f;

    private final boolean systemSounds;
    private final boolean custom;
    private final float focusVolume;
    private final float selectVolume;
    private final float selectPitch;
    private final long selectMs;
    private final SoundPool pool;
    private final int focusId;
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

    static float setting(Context context, int resource, String key, float min, float max) {
        TypedValue value = new TypedValue();
        context.getResources().getValue(resource, value, true);
        float result = value.getFloat();
        JSONObject overlay = Config.overlay(context);
        if (overlay != null && overlay.has(key)) result = (float) overlay.optDouble(key, Double.NaN);
        if (!(result >= min && result <= max)) {
            throw new IllegalStateException(key + " must be between " + min + " and " + max + ", not " + result);
        }
        return result;
    }

    NavSound(Activity activity) {
        String mode = activity.getString(R.string.nav_sound);
        systemSounds = systemSoundsEnabled(activity);
        custom = CUSTOM.equals(mode);
        focusVolume = custom ? setting(activity, R.dimen.nav_sound_volume, "nav_sound_volume", 0f, 1f) : 0f;
        selectVolume = custom ? setting(activity, R.dimen.nav_sound_select_volume, "nav_sound_select_volume", 0f, 1f) : 0f;
        selectPitch = custom ? setting(activity, R.dimen.nav_sound_select_pitch, "nav_sound_select_pitch", MIN_RATE, MAX_RATE) : 1f;
        selectMs = custom ? activity.getResources().getInteger(R.integer.nav_sound_select_ms) : 0;
        muteSystemSounds(activity);
        decor = activity.getWindow().getDecorView();
        if (!systemSounds) silenceTree(decor);
        if (!custom) {
            pool = null;
            focusId = 0;
            focusListener = null;
            return;
        }
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        focusId = pool.load(activity, R.raw.cue_tap, 1);
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
        if (pool == null) return;
        Log.d(TAG, "select plays cue_tap at rate " + selectPitch + ", volume " + selectVolume);
        pool.play(focusId, selectVolume, selectVolume, 1, 0, selectPitch);
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
