package com.msrafi.homeinfo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.format.DateFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;

/** One row for the home strip. Read-only. Home asks, and a stale row starts a refresh. */
public final class NowProvider extends ContentProvider {
    static final String AUTHORITY = "com.msrafi.homeinfo.now";
    static final Uri URI = Uri.parse("content://" + AUTHORITY + "/now");
    /** Names and order Home asks for. */
    static final String[] COLUMNS = {
            "symbol", "temperature", "condition", "feels", "place", "metrics",
            "event_kicker", "event_title", "event_when", "prayers", "reminders", "calendar", "forecast",
            "wallpaper"
    };

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx != null) {
            EditorServer.start(ctx);
            try {
                ctx.startForegroundService(new android.content.Intent(ctx, EditorService.class));
            } catch (RuntimeException ignored) {
                // Android may refuse a service start from the background. The page still runs
                // in this process, which Home wakes about once a minute while it is open.
            }
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null || !"now".equals(uri.getLastPathSegment())) return null;
        SharedPreferences prefs = ctx.getSharedPreferences(Refresh.PREFS, Context.MODE_PRIVATE);
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        String reminders = reminders(ctx);
        if (prefs.contains("fetched_at") || !reminders.isEmpty()) {
            cursor.addRow(new Object[] {
                    prefs.getString("symbol", ""),
                    prefs.getString("temperature", ""),
                    prefs.getString("condition", ""),
                    prefs.getString("feels", ""),
                    prefs.getString("place", ""),
                    prefs.getString("metrics", ""),
                    prefs.getString("event_kicker", ""),
                    prefs.getString("event_title", ""),
                    prefs.getString("event_when", ""),
                    Prayers.lines(prefs.getString("prayer_raw", ""), System.currentTimeMillis(),
                            ZoneId.systemDefault(), prefs.getString("prayer_day", ""),
                            DateFormat.is24HourFormat(ctx)),
                    reminders,
                    MonthMarks.grid(LocalDate.now(ZoneId.systemDefault()),
                            prefs.getString("us_marks", ""), prefs.getString("us_month", "")),
                    prefs.getString("forecast", ""),
                    Wallpaper.on(ctx) ? "phone" : ""
            });
        }
        cursor.setNotificationUri(ctx.getContentResolver(), URI);
        Refresh.kick(ctx);
        return cursor;
    }

    private static String reminders(Context ctx) {
        return Reminders.text(ctx);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uri == null || !"wallpaper".equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        if (mode != null && mode.contains("w")) throw new FileNotFoundException();
        File file = Wallpaper.file(getContext());
        if (file == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.msrafi.homeinfo";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
