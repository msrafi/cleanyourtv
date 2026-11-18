package com.msrafi.homeinfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** One picture chosen from the phone, shown behind Home until the theme picture is put back. */
final class Wallpaper {
    private static final String NAME = "wallpaper.jpg";
    private static final int MAX_EDGE = 1920;
    private static final int MAX_STORED = 2 * 1024 * 1024;

    private Wallpaper() { }

    static File file(Context ctx) {
        if (ctx == null) return null;
        File file = new File(ctx.getFilesDir(), NAME);
        return file.isFile() && file.length() > 0 ? file : null;
    }

    static boolean on(Context ctx) {
        return file(ctx) != null;
    }

    static byte[] bytes(Context ctx) {
        File file = file(ctx);
        if (file == null || file.length() > MAX_STORED) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream raw = new ByteArrayOutputStream((int) file.length());
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) raw.write(buf, 0, n);
            return raw.toByteArray();
        } catch (IOException ignored) {
            return null;
        }
    }

    /** Scales the upload down to the screen and stores it. False when it is not a picture. */
    static boolean store(Context ctx, byte[] raw) {
        if (ctx == null || raw == null || raw.length == 0) return false;
        Bitmap scaled = scale(raw);
        if (scaled == null) return false;
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        boolean wrote = scaled.compress(Bitmap.CompressFormat.JPEG, 80, jpeg);
        scaled.recycle();
        if (!wrote || jpeg.size() == 0) return false;
        File dir = ctx.getFilesDir();
        File out = new File(dir, NAME);
        File tmp = new File(dir, NAME + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            stream.write(jpeg.toByteArray());
            stream.flush();
        } catch (IOException failed) {
            tmp.delete();
            return false;
        }
        if (out.exists() && !out.delete()) {
            tmp.delete();
            return false;
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            return false;
        }
        ctx.getContentResolver().notifyChange(NowProvider.URI, null);
        return true;
    }

    static void clear(Context ctx) {
        if (ctx == null) return;
        new File(ctx.getFilesDir(), NAME).delete();
        new File(ctx.getFilesDir(), NAME + ".tmp").delete();
        ctx.getContentResolver().notifyChange(NowProvider.URI, null);
    }

    private static Bitmap scale(byte[] raw) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        if (bounds.outWidth > 16000 || bounds.outHeight > 16000) return null;
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap decoded = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        if (decoded == null) return null;
        int edge = Math.max(decoded.getWidth(), decoded.getHeight());
        if (edge <= MAX_EDGE) return decoded;
        float ratio = MAX_EDGE / (float) edge;
        int width = Math.max(1, Math.round(decoded.getWidth() * ratio));
        int height = Math.max(1, Math.round(decoded.getHeight() * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }
}
