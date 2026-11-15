package com.majnioui.tv.prayer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Calendar;

/**
 * The next prayer, for the launcher's line under the clock (docs/modules.md, game_day). Read-only.
 * One row: the place, the prayer name, and when it starts. The launcher writes the countdown.
 */
public final class PrayerTurnsProvider extends ContentProvider {
    static final String AUTHORITY = "com.majnioui.tv.prayer.times";
    static final Uri TURNS = Uri.parse("content://" + AUTHORITY + "/turns");
    /** Same names and order the launcher asks for. */
    static final String[] COLUMNS = {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"};
    /** Green from the prayer page. "Live now" lasts this long, then the next prayer takes the line. */
    private static final int COLOUR = 0xFF2BB673;
    private static final long LIVE_MS = 2L * 60L * 1000L;

    static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(TURNS, null);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"turns".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        Object[] row = next(ctx);
        if (row != null) c.addRow(row);
        c.setNotificationUri(ctx.getContentResolver(), TURNS);
        return c;
    }

    /** The next prayer that has not ended, or null when today's times are not stored yet. */
    static Object[] next(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("prayer_prefs", Context.MODE_PRIVATE);
        String day = prefs.getString("cached_date", "");
        if (day.isEmpty()) return null;
        String place = prefs.getString("place", ctx.getString(R.string.place));
        if (place == null || place.isEmpty()) place = "Prayer";
        long now = System.currentTimeMillis();
        int[] minutes = new int[PrayerScheduler.NAMES.length];
        for (int i = 0; i < minutes.length; i++) {
            minutes[i] = prefs.getInt("min_" + PrayerScheduler.NAMES[i], -1);
        }
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < minutes.length; i++) {
            if (minutes[i] < 0) continue;
            long start = at(today, 0, minutes[i]);
            long end = start + LIVE_MS;
            if (now < end) return row(place, label(i, minutes[i]), start, end);
        }
        if (minutes[0] < 0) return null;
        long fajr = at(today, 1, minutes[0]);
        return row(place, label(0, minutes[0]), fajr, fajr + LIVE_MS);
    }

    /** "Isha 5:58 PM", so Home's line reads "Isha 5:58 PM in 1h 16m". */
    private static String label(int index, int minutes) {
        int hour = minutes / 60;
        int min = minutes % 60;
        int h12 = hour % 12;
        if (h12 == 0) h12 = 12;
        String suffix = hour < 12 ? "AM" : "PM";
        return PrayerScheduler.NAMES[index] + " " + h12 + ":" + String.format(java.util.Locale.ROOT, "%02d", min) + " " + suffix;
    }

    private static Object[] row(String place, String name, long start, long end) {
        return new Object[] {place, name, start, end, COLOUR, "prayer", 30};
    }

    /** Local midnight plus {@code days} and {@code minutes} from midnight. */
    private static long at(Calendar now, int days, int minutes) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, days);
        c.add(Calendar.MINUTE, minutes);
        return c.getTimeInMillis();
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".turn";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
