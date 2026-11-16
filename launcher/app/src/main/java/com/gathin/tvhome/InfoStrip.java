package com.gathin.tvhome;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Weather, three calendar rows and today's prayer times beside the clock. Home only reads a row published by
 * com.msrafi.homeinfo. Nothing here opens a connection. The strip stays hidden until that app is installed.
 */
final class InfoStrip {
    static final String PACKAGE = "com.msrafi.homeinfo";
    static final String META = "com.gathin.tvhome.info";
    private static final String[] COLUMNS = {
            "symbol", "temperature", "condition", "feels", "place", "metrics",
            "event_kicker", "event_title", "event_when", "prayers"
    };
    private static final int PRAYER_ROWS = 6;
    private static final long REREAD_MS = 60_000L;

    private final Context ctx;
    private final Uri uri;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver observer;
    private final Runnable reread = new Runnable() {
        @Override
        public void run() {
            read();
            main.postDelayed(this, REREAD_MS);
        }
    };
    private final LinearLayout weather;
    private final LinearLayout event;
    private final TextView symbol;
    private final TextView temperature;
    private final TextView condition;
    private final TextView feels;
    private final TextView place;
    private final TextView metrics;
    private final TextView kicker;
    private final TextView title;
    private final TextView when;
    private final LinearLayout prayers;
    private final TextView[] prayerName = new TextView[PRAYER_ROWS];
    private final TextView[] prayerTime = new TextView[PRAYER_ROWS];
    private final LinearLayout strip;
    private boolean watching;

    private InfoStrip(Activity activity, Uri uri) {
        this.ctx = activity;
        this.uri = uri;
        float density = activity.getResources().getDisplayMetrics().density;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
        symbol = text(activity, 28, Color.WHITE);
        symbol.setTypeface(Typeface.DEFAULT);
        temperature = text(activity, 28, Color.WHITE);
        temperature.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        condition = text(activity, 13, 0xB3FFFFFF);
        feels = text(activity, 13, 0xE6FFFFFF);
        place = text(activity, 12, 0xA6FFFFFF);
        metrics = text(activity, 12, 0xA6FFFFFF);
        kicker = text(activity, 14, Color.WHITE);
        title = text(activity, 14, Color.WHITE);
        when = text(activity, 14, Color.WHITE);

        weather = row(activity, density);
        weather.addView(bar(activity), barParams(density));
        weather.addView(symbol, wrap());
        LinearLayout tempColumn = column(activity);
        tempColumn.addView(temperature);
        tempColumn.addView(condition);
        weather.addView(tempColumn, inset(density, 10));
        LinearLayout detail = column(activity);
        detail.addView(feels);
        detail.addView(place);
        detail.addView(metrics);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        detailParams.setMarginStart(dp(density, 14));
        weather.addView(detail, detailParams);

        event = row(activity, density);
        LinearLayout.LayoutParams eventBar = barParams(density);
        eventBar.setMarginStart(dp(density, 8));
        event.addView(bar(activity), eventBar);
        LinearLayout copy = column(activity);
        copy.addView(kicker);
        copy.addView(title);
        copy.addView(when);
        event.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        prayers = row(activity, density);
        LinearLayout.LayoutParams prayerBar = barParams(density);
        prayerBar.height = dp(density, 112);
        prayerBar.setMarginStart(dp(density, 8));
        prayers.addView(bar(activity), prayerBar);
        LinearLayout prayerList = column(activity);
        for (int i = 0; i < PRAYER_ROWS; i++) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            prayerName[i] = text(activity, 13, 0xA6FFFFFF);
            prayerName[i].setMinWidth(dp(density, 64));
            prayerTime[i] = text(activity, 13, 0xE6FFFFFF);
            line.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            line.addView(prayerName[i]);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            timeParams.setMarginStart(dp(density, 8));
            line.addView(prayerTime[i], timeParams);
            prayerList.addView(line);
        }
        prayers.addView(prayerList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Under the clock, not in its row: the clock and the top buttons already fill that line.
        // Weather, holidays and prayer times each take a third of this row.
        strip = row(activity, density);
        strip.setPadding(0, dp(density, 8), 0, 0);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        strip.addView(weather, third);
        strip.addView(event, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        strip.addView(prayers, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout header = (LinearLayout) activity.findViewById(R.id.clock).getParent().getParent();
        View clockRow = (View) activity.findViewById(R.id.clock).getParent();
        header.addView(strip, header.indexOfChild(clockRow) + 1,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        show(false, false, false);
    }

    /** Null when com.msrafi.homeinfo is not installed, or it publishes no row of its own. */
    static InfoStrip attach(Activity activity) {
        String pkg = PACKAGE;
        try {
            PackageManager pm = activity.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(authority.trim()).appendPath("now").build();
            return new InfoStrip(activity, uri);
        } catch (PackageManager.NameNotFoundException missing) {
            return null;
        }
    }

    /** Home is in front: read now, then again each minute and whenever the other app updates the row. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException ignored) {
                // The other app went away. The last text stays until Home is recreated.
            }
        }
        read();
        main.removeCallbacks(reread);
        main.postDelayed(reread, REREAD_MS);
    }

    void stop() {
        main.removeCallbacks(reread);
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    private void read() {
        try {
            io.execute(() -> {
                String[] values = new String[COLUMNS.length];
                try (Cursor cursor = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        for (int i = 0; i < COLUMNS.length; i++) {
                            int index = cursor.getColumnIndex(COLUMNS[i]);
                            values[i] = index < 0 ? "" : cursor.getString(index);
                        }
                    }
                } catch (RuntimeException gone) {
                    return;
                }
                main.post(() -> apply(values));
            });
        } catch (RuntimeException ignored) {
            // The executor was shut down while a read was queued.
        }
    }

    private void apply(String[] values) {
        if (values[0] == null) {
            show(false, false, false);
            return;
        }
        symbol.setText(blank(values[0]));
        temperature.setText(blank(values[1]));
        condition.setText(blank(values[2]));
        feels.setText(blank(values[3]));
        place.setText(blank(values[4]));
        metrics.setText(blank(values[5]));
        kicker.setText(blank(values[6]));
        title.setText(blank(values[7]));
        when.setText(blank(values[8]));
        boolean hasPrayers = applyPrayers(values.length > 9 ? values[9] : "");
        boolean hasWeather = !blank(values[1]).isEmpty();
        boolean hasEvent = !blank(values[6]).isEmpty() || !blank(values[7]).isEmpty() || !blank(values[8]).isEmpty();
        show(hasWeather, hasEvent, hasPrayers);
    }

    /** Fills the six prayer rows from "Name\ttime" lines. A third field of "next" marks the one still ahead. */
    private boolean applyPrayers(String block) {
        String[] lines = block.split("\n", -1);
        boolean any = false;
        for (int i = 0; i < PRAYER_ROWS; i++) {
            String line = i < lines.length ? lines[i] : "";
            String[] parts = line.split("\t", -1);
            String name = parts.length > 0 ? parts[0] : "";
            String time = parts.length > 1 ? parts[1] : "";
            boolean next = parts.length > 2 && "next".equals(parts[2]);
            boolean current = parts.length > 2 && "current".equals(parts[2]);
            prayerName[i].setText(name);
            prayerTime[i].setText(time);
            // Green is the prayer underway. White is the next one, with the minutes still left.
            int color = next ? Color.WHITE : current ? 0xFF2BB673 : 0xA6FFFFFF;
            prayerName[i].setTextColor(color);
            prayerTime[i].setTextColor(next ? Color.WHITE : current ? 0xFF2BB673 : 0xE6FFFFFF);
            ((View) prayerName[i].getParent()).setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
            if (!name.isEmpty()) any = true;
        }
        return any;
    }

    private void show(boolean hasWeather, boolean hasEvent, boolean hasPrayers) {
        weather.setVisibility(hasWeather ? View.VISIBLE : View.GONE);
        event.setVisibility(hasEvent ? View.VISIBLE : View.GONE);
        prayers.setVisibility(hasPrayers ? View.VISIBLE : View.GONE);
        strip.setVisibility(hasWeather || hasEvent || hasPrayers ? View.VISIBLE : View.GONE);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static TextView text(Activity activity, float sizeSp, int color) {
        TextView view = new TextView(activity);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }

    private static LinearLayout row(Activity activity, float density) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setFocusable(false);
        layout.setPadding(0, dp(density, 2), 0, dp(density, 2));
        return layout;
    }

    private static LinearLayout column(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static View bar(Activity activity) {
        View bar = new View(activity);
        bar.setBackgroundColor(0x33FFFFFF);
        return bar;
    }

    private static LinearLayout.LayoutParams barParams(float density) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(density, 1), dp(density, 52));
        params.setMarginStart(dp(density, 16));
        params.setMarginEnd(dp(density, 14));
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams inset(float density, int startDp) {
        LinearLayout.LayoutParams params = wrap();
        params.setMarginStart(dp(density, startDp));
        return params;
    }

    private static int dp(float density, int value) {
        return Math.round(value * density);
    }
}
