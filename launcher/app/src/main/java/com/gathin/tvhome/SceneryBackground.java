package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home's background: the photos in the app's external "wallpapers" folder, centre-cropped to the screen and
 * cross-faded on a calm interval. Photos are decoded at screen size one at a time and recycled once replaced.
 */
final class SceneryBackground {
    private final ImageView back;
    private final ImageView front;
    private final List<File> files;
    private final long intervalMs;
    private final long crossfadeMs;
    private final int screenWidth;
    private final int screenHeight;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final List<File> queue = new ArrayList<>();
    private final Runnable advance = this::loadNext;
    private final Map<String, String> locations = new HashMap<>();
    private Listener listener;
    private Bitmap shown;
    private boolean active;
    private boolean loading;
    private boolean closed;

    interface Listener {
        /** The picture now fading in; the label is its place, or empty when the manifest has none. */
        void onScene(String location);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    SceneryBackground(Context context, FrameLayout root) {
        Resources res = context.getResources();
        intervalMs = res.getInteger(R.integer.scenery_rotation_seconds) * 1000L;
        crossfadeMs = res.getInteger(R.integer.scenery_crossfade_ms);
        DisplayMetrics metrics = res.getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        files = WallpaperRotation.images(context.getExternalFilesDir(res.getString(R.string.scenery_directory)));
        readLocations(context.getExternalFilesDir(res.getString(R.string.scenery_directory)), res.getString(R.string.scenery_locations_file));
        back = layer(context);
        front = layer(context);
        front.setAlpha(0f);
        if (files.isEmpty()) return;
        root.addView(back, 0, match());
        root.addView(front, 1, match());
        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                Color.argb(res.getInteger(R.integer.scenery_scrim_top_alpha), 0, 0, 0),
                Color.argb(res.getInteger(R.integer.scenery_scrim_middle_alpha), 0, 0, 0),
                Color.argb(res.getInteger(R.integer.scenery_scrim_bottom_alpha), 0, 0, 0)}));
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(scrim, 2, match());
    }

    private void readLocations(File directory, String name) {
        File manifest = new File(directory, name);
        if (!manifest.isFile()) return;
        try {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
            for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                locations.put(key, json.getString(key));
            }
        } catch (Exception e) {
            locations.clear();
        }
    }

    boolean hasImages() {
        return !files.isEmpty();
    }

    void start() {
        if (files.isEmpty() || closed) return;
        active = true;
        main.removeCallbacks(advance);
        if (shown == null) loadNext();
        else main.postDelayed(advance, intervalMs);
    }

    void stop() {
        active = false;
        main.removeCallbacks(advance);
    }

    void close() {
        closed = true;
        stop();
        decoder.shutdownNow();
        back.setImageDrawable(null);
        front.setImageDrawable(null);
        shown = null;
    }

    private static ImageView layer(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private File nextFile() {
        if (queue.isEmpty()) {
            queue.addAll(files);
            Collections.shuffle(queue);
        }
        return queue.remove(queue.size() - 1);
    }

    private void loadNext() {
        if (!active || loading || closed) return;
        loading = true;
        File file = nextFile();
        decoder.execute(() -> {
            Bitmap bitmap = decode(file);
            main.post(() -> {
                loading = false;
                if (closed) return;
                if (bitmap != null) reveal(bitmap, file);
                if (active) main.postDelayed(advance, bitmap == null ? 0 : intervalMs);
            });
        });
    }

    private Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= screenWidth && bounds.outHeight / (sample * 2) >= screenHeight) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void reveal(Bitmap next, File file) {
        if (listener != null) listener.onScene(locations.getOrDefault(file.getName(), ""));
        Bitmap previous = shown;
        shown = next;
        AlphaFade.cancel(front);
        front.setImageBitmap(next);
        if (previous == null) {
            back.setImageBitmap(next);
            return;
        }
        front.setAlpha(0f);
        AlphaFade.to(front, 1f, crossfadeMs, () -> {
            back.setImageBitmap(next);
            front.setAlpha(0f);
            front.setImageDrawable(null);
            previous.recycle();
        });
    }
}
