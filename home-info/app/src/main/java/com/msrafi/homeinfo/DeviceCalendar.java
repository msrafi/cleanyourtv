package com.msrafi.homeinfo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

/** The soonest event on the TV's own calendars, when READ_CALENDAR has been granted. */
final class DeviceCalendar {
    private DeviceCalendar() { }

    static Agenda.Item next(Context ctx, long now) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        long windowEnd = now + Agenda.WINDOW_MS;
        Uri uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(Long.toString(now - 24L * 60 * 60 * 1000))
                .appendPath(Long.toString(windowEnd))
                .build();
        String[] columns = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };
        try (Cursor cursor = ctx.getContentResolver().query(uri, columns, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) return null;
            Agenda.Item best = null;
            while (cursor.moveToNext()) {
                String title = cursor.getString(0);
                if (title == null || title.trim().isEmpty()) continue;
                long start = cursor.getLong(1);
                long end = cursor.getLong(2);
                if (end <= start) end = start + 60 * 60 * 1000L;
                boolean allDay = cursor.getInt(3) == 1;
                best = Agenda.choose(best, new Agenda.Item(title.trim(), start, end, allDay), now);
            }
            return best;
        } catch (SecurityException denied) {
            return null;
        }
    }
}
