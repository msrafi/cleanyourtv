package com.msrafi.homeinfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refreshes the cached strip. A query from Home starts one refresh when the cache is older than
 * fifteen minutes. Weather and the feed are fetched here; the launcher never opens a connection.
 */
final class Refresh {
    static final String PREFS = "home_info";
    /** Bump when the calendars change, so a TV that already cached a row fetches again. */
    private static final int GENERATION = 8;
    private static final long FRESH_MS = 15 * 60 * 1000L;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "home-info");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private Refresh() { }

    static void kick(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!stale(app) || !BUSY.compareAndSet(false, true)) return;
        EXEC.execute(() -> {
            try {
                if (stale(app)) update(app);
            } finally {
                BUSY.set(false);
            }
        });
    }

    private static boolean stale(Context ctx) {
        SharedPreferences prefs = prefs(ctx);
        if (prefs.getInt("generation", 0) != GENERATION) return true;
        return System.currentTimeMillis() - prefs.getLong("fetched_at", 0) >= FRESH_MS;
    }

    private static void update(Context ctx) {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        Weather.Now weather = null;
        String forecast = null;
        String prayerRaw = null;
        boolean coordinatesOk = false;
        boolean fahrenheit = ctx.getResources().getBoolean(R.bool.weather_fahrenheit);
        try {
            double latitude = Double.parseDouble(ctx.getString(R.string.latitude));
            double longitude = Double.parseDouble(ctx.getString(R.string.longitude));
            Weather.checkCoordinates(latitude, longitude);
            coordinatesOk = true;
        } catch (Exception ignored) {
            // A bad place leaves the last weather and the last prayer times on screen.
        }
        if (coordinatesOk) {
            try {
                Weather.Report report = Weather.fetch(Double.parseDouble(ctx.getString(R.string.latitude)),
                        Double.parseDouble(ctx.getString(R.string.longitude)),
                        LocalDate.now(zone), fahrenheit, Locale.getDefault());
                weather = report.now;
                if (report.days != null && !report.days.isEmpty()) forecast = report.days;
            } catch (Exception ignored) {
                // Keep the previous conditions. Home still has them until a later refresh works.
            }
        }
        SharedPreferences prefs = prefs(ctx);
        String today = LocalDate.now(zone).toString();
        if (coordinatesOk && !today.equals(prefs.getString("prayer_day", ""))) {
            try {
                prayerRaw = Prayers.fetch(ctx.getString(R.string.latitude), ctx.getString(R.string.longitude),
                        ctx.getString(R.string.prayer_method), ctx.getString(R.string.prayer_school),
                        LocalDate.now(zone));
            } catch (Exception ignored) {
                // Keep yesterday's times. The next refresh tries today's again.
            }
        }
        boolean replacing = prefs.getInt("generation", 0) != GENERATION;
        Lines lines = lines(ctx, now, replacing);
        Present present = Present.of(weather, ctx.getString(R.string.place), fahrenheit, fahrenheit,
                null, now, zone, Locale.getDefault(), DateFormat.is24HourFormat(ctx));
        SharedPreferences.Editor editor = prefs.edit();
        if (weather != null) {
            editor.putString("symbol", present.symbol)
                    .putString("temperature", present.temperature)
                    .putString("condition", present.condition)
                    .putString("feels", present.feels)
                    .putString("place", present.place)
                    .putString("metrics", present.metrics);
            if (forecast != null) editor.putString("forecast", forecast);
        }
        if (lines.top != null) editor.putString("event_kicker", lines.top);
        if (lines.middle != null) editor.putString("event_title", lines.middle);
        editor.putString("event_when", Present.hijriLine(now, zone));
        if (prayerRaw != null) editor.putString("prayer_raw", prayerRaw).putString("prayer_day", today);
        String month = YearMonth.now(zone).toString();
        if (lines.usBody != null) {
            editor.putString("us_month", month).putString("us_marks", MonthMarks.usMarks(lines.usBody, LocalDate.now(zone), zone));
        } else if (!month.equals(prefs.getString("us_month", ""))) {
            editor.remove("us_marks").remove("us_month");
        }
        if (lines.complete) editor.putInt("generation", GENERATION);
        editor.putLong("fetched_at", now).apply();
        ctx.getContentResolver().notifyChange(NowProvider.URI, null);
    }

    /**
     * One row per feed, in the order written in config. A feed that fails is left as it was, unless
     * this is the first fetch after the calendars changed, in which case the old row is cleared.
     * Generation advances only once every feed has answered, so a miss is tried again next time.
     */
    private static Lines lines(Context ctx, long now, boolean replacing) {
        String[] feeds = ctx.getResources().getStringArray(R.array.calendar_feeds);
        String top = null;
        String middle = null;
        String usBody = null;
        int answered = 0;
        int listed = 0;
        ZoneId zone = ZoneId.systemDefault();
        Locale locale = Locale.getDefault();
        for (int i = 0; i < feeds.length && i < 2; i++) {
            int bar = feeds[i].indexOf('|');
            if (bar <= 0 || bar == feeds[i].length() - 1) continue;
            listed++;
            String label = feeds[i].substring(0, bar).trim();
            String url = feeds[i].substring(bar + 1).trim();
            String line;
            try {
                String body = Net.get(url);
                line = Present.holidayLine(label, Agenda.next(body, now, zone), zone, locale);
                answered++;
                if ("US".equalsIgnoreCase(label)) usBody = body;
            } catch (Exception ignored) {
                line = replacing ? "" : null;
            }
            if (i == 0) top = line;
            else middle = line;
        }
        return new Lines(top, middle, listed > 0 && answered == listed, usBody);
    }

    private static final class Lines {
        final String top;
        final String middle;
        final boolean complete;
        /** The US holiday feed when this refresh got one, so the month grid can mark those days. */
        final String usBody;

        Lines(String top, String middle, boolean complete, String usBody) {
            this.top = top;
            this.middle = middle;
            this.complete = complete;
            this.usBody = usBody;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
