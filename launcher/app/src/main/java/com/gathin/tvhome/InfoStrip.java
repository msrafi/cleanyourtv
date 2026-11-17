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
            "event_kicker", "event_title", "event_when", "prayers", "reminders", "calendar", "forecast"
    };
    private static final int FORECAST_DAYS = 5;
    private static final int PRAYER_ROWS = 6;
    private static final int REMINDER_ROWS = 5;
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
    private final LinearLayout forecastBlock;
    private final TextView[] forecastDay = new TextView[FORECAST_DAYS];
    private final ForecastIcon[] forecastIcon = new ForecastIcon[FORECAST_DAYS];
    private final TextView[] forecastHigh = new TextView[FORECAST_DAYS];
    private final TextView[] forecastLow = new TextView[FORECAST_DAYS];
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
    private final LinearLayout lowerBand;
    private final LinearLayout reminderBlock;
    private final TextView[] reminderLine = new TextView[REMINDER_ROWS];
    private final MonthGrid monthGrid;
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

        // Five days, only under today's weather, and small enough to stay inside that column.
        forecastBlock = row(activity, density);
        forecastBlock.setGravity(Gravity.TOP);
        forecastBlock.setPadding(0, dp(density, 6), 0, 0);
        for (int i = 0; i < FORECAST_DAYS; i++) {
            LinearLayout dayColumn = column(activity);
            dayColumn.setGravity(Gravity.CENTER_HORIZONTAL);
            forecastDay[i] = text(activity, 9, 0xE6FFFFFF);
            forecastDay[i].setGravity(Gravity.CENTER);
            forecastIcon[i] = new ForecastIcon(activity);
            LinearLayout temps = row(activity, density);
            temps.setGravity(Gravity.CENTER);
            temps.setPadding(0, 0, 0, 0);
            forecastHigh[i] = text(activity, 10, Color.WHITE);
            forecastLow[i] = text(activity, 9, 0xFF9AA0A6);
            temps.addView(forecastHigh[i]);
            temps.addView(forecastLow[i], inset(density, 3));
            dayColumn.addView(forecastDay[i], wrap());
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(density, 16), dp(density, 16));
            iconParams.topMargin = dp(density, 1);
            dayColumn.addView(forecastIcon[i], iconParams);
            dayColumn.addView(temps, wrap());
            forecastBlock.addView(dayColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        LinearLayout.LayoutParams forecastParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        forecastParams.setMarginStart(dp(density, 31));
        forecastBlock.setVisibility(View.GONE);

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
        strip.setGravity(Gravity.TOP);
        strip.setPadding(0, dp(density, 8), 0, 0);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout weatherColumn = column(activity);
        weatherColumn.addView(weather, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherColumn.addView(forecastBlock, forecastParams);
        strip.addView(weatherColumn, third);
        strip.addView(event, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        strip.addView(prayers, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout header = (LinearLayout) activity.findViewById(R.id.clock).getParent().getParent();
        View clockRow = (View) activity.findViewById(R.id.clock).getParent();
        int underClock = header.indexOfChild(clockRow) + 1;
        header.addView(strip, underClock,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Under the prayer line, which is drawn over the gap below the clock block.
        reminderBlock = column(activity);
        reminderBlock.setFocusable(false);
        TextView reminderKicker = text(activity, 12, 0xB3FFFFFF);
        reminderKicker.setLetterSpacing(0.18f);
        reminderKicker.setAllCaps(true);
        reminderKicker.setText("Reminders");
        reminderBlock.addView(reminderKicker);
        for (int i = 0; i < REMINDER_ROWS; i++) {
            reminderLine[i] = text(activity, 18, Color.WHITE);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineParams.topMargin = dp(density, i == 0 ? 8 : 4);
            reminderBlock.addView(reminderLine[i], lineParams);
        }
        // Beside the prayer line: reminders stay under it, and the month sits in the open space to the right.
        reminderBlock.setPadding(0, dp(density, 92), 0, 0);
        lowerBand = new LinearLayout(activity);
        lowerBand.setOrientation(LinearLayout.HORIZONTAL);
        lowerBand.setGravity(Gravity.TOP | Gravity.END);
        lowerBand.setClipChildren(false);
        lowerBand.setFocusable(false);
        lowerBand.addView(reminderBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        monthGrid = new MonthGrid(activity, density);
        LinearLayout.LayoutParams calendarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        calendarParams.setMarginStart(dp(density, 24));
        calendarParams.gravity = Gravity.TOP;
        lowerBand.addView(monthGrid.view(), calendarParams);
        View relative = (View) header.getParent();
        LinearLayout page = (LinearLayout) relative.getParent();
        LinearLayout.LayoutParams bandParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bandParams.topMargin = dp(density, 16);
        page.addView(lowerBand, page.indexOfChild(relative) + 1, bandParams);
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
            applyReminders("");
            monthGrid.bind("");
            applyForecast("");
            lowerBand.setVisibility(View.GONE);
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
        applyReminders(values.length > 10 ? values[10] : "");
        monthGrid.bind(values.length > 11 ? values[11] : "");
        applyForecast(values.length > 12 ? values[12] : "");
        lowerBand.setVisibility(View.VISIBLE);
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

    /** Tomorrow and the four days after it: weekday, icon, high, low. An empty list hides the rows. */
    private void applyForecast(String block) {
        String[] lines = block == null ? new String[0] : block.split("\n", -1);
        boolean any = false;
        int shown = 0;
        for (int i = 0; i < FORECAST_DAYS; i++) {
            String line = "";
            while (shown < lines.length && lines[shown].trim().isEmpty()) shown++;
            if (shown < lines.length) line = lines[shown++].trim();
            String[] parts = line.split("\t", -1);
            String day = parts.length > 0 ? parts[0] : "";
            forecastDay[i].setText(day);
            forecastIcon[i].setKind(parts.length > 1 ? parts[1] : "");
            forecastHigh[i].setText(parts.length > 2 ? parts[2] : "");
            forecastLow[i].setText(parts.length > 3 ? parts[3] : "");
            ((View) forecastDay[i].getParent()).setVisibility(day.isEmpty() ? View.GONE : View.VISIBLE);
            if (!day.isEmpty()) any = true;
        }
        forecastBlock.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** Up to five lines under the prayer line. An empty list hides the block. */
    private void applyReminders(String block) {
        String[] lines = block == null ? new String[0] : block.split("\n", -1);
        boolean any = false;
        int shown = 0;
        for (int i = 0; i < REMINDER_ROWS; i++) {
            String line = "";
            while (shown < lines.length && lines[shown].trim().isEmpty()) shown++;
            if (shown < lines.length) line = lines[shown++].trim();
            reminderLine[i].setText(line);
            reminderLine[i].setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
            if (!line.isEmpty()) any = true;
        }
        reminderBlock.setVisibility(any ? View.VISIBLE : View.GONE);
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
