package com.msrafi.homeinfo;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Today's prayer times from Aladhan, the same service the prayer-times app uses. The raw row is
 * stored as "Fajr=05:32;Sunrise=06:51;..." and turned into the strip's lines when Home asks.
 */
final class Prayers {
    private static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);

    private Prayers() { }

    /** The stored row, or null when the answer did not contain all six times. */
    static String fetch(String latitude, String longitude, String method, String school, LocalDate day) throws IOException {
        String url = "https://api.aladhan.com/v1/timings/" + day.format(DAY)
                + "?latitude=" + latitude + "&longitude=" + longitude
                + "&method=" + method + "&school=" + school;
        return timings(Net.get(url));
    }

    /** "Fajr=05:32;..." from an Aladhan body. A "05:32 (EST)" suffix is dropped. */
    static String timings(String json) {
        if (json == null || json.isEmpty()) return null;
        StringBuilder raw = new StringBuilder();
        for (String name : NAMES) {
            String hhmm = value(json, name);
            if (hhmm == null) return null;
            if (raw.length() > 0) raw.append(';');
            raw.append(name).append('=').append(hhmm);
        }
        return raw.toString();
    }

    /**
     * One line per time. The prayer underway is "current" and the next prayer is "next", with the
     * minutes still left until it ("1h 42m"). Sunrise is shown but is not a prayer. Marks are added
     * only when {@code day} is the day {@code now} falls on.
     */
    static String lines(String raw, long now, ZoneId zone, String day, boolean hour24) {
        if (raw == null || raw.isEmpty()) return "";
        boolean mark = day != null && day.equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString());
        int nowMin = mark ? minuteOfDay(now, zone) : -1;
        String current = null;
        String next = null;
        int nextAt = -1;
        if (mark) {
            for (String entry : raw.split(";")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2 || "Sunrise".equals(pair[0])) continue;
                int at = minutes(pair[1]);
                if (at < 0) continue;
                if (at <= nowMin) current = pair[0];
                else if (next == null) {
                    next = pair[0];
                    nextAt = at;
                }
            }
        }
        StringBuilder out = new StringBuilder();
        for (String entry : raw.split(";")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || minutes(pair[1]) < 0) continue;
            if (out.length() > 0) out.append('\n');
            String time = clock(pair[1], hour24);
            if (pair[0].equals(next)) time = time + " · " + remaining(nextAt - nowMin);
            out.append(pair[0]).append('\t').append(time);
            if (pair[0].equals(current)) out.append("\tcurrent");
            else if (pair[0].equals(next)) out.append("\tnext");
        }
        return out.toString();
    }

    /** "12m", "1h", or "1h 42m". */
    static String remaining(int minutes) {
        if (minutes < 60) return Math.max(0, minutes) + "m";
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
    }

    static String clock(String hhmm, boolean hour24) {
        int hour = Integer.parseInt(hhmm.substring(0, 2));
        int minute = Integer.parseInt(hhmm.substring(3, 5));
        if (hour24) return String.format(Locale.US, "%02d:%02d", hour, minute);
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;
        return hour12 + ":" + String.format(Locale.US, "%02d", minute) + (hour < 12 ? " AM" : " PM");
    }

    private static String value(String json, String name) {
        String key = "\"" + name + "\":\"";
        int at = json.indexOf(key);
        if (at < 0) return null;
        int start = at + key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        String value = json.substring(start, end).trim();
        int space = value.indexOf(' ');
        if (space > 0) value = value.substring(0, space);
        return minutes(value) < 0 ? null : value;
    }

    private static int minutes(String hhmm) {
        if (hhmm == null || hhmm.length() < 5 || hhmm.charAt(2) != ':') return -1;
        try {
            int hour = Integer.parseInt(hhmm.substring(0, 2));
            int minute = Integer.parseInt(hhmm.substring(3, 5));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException bad) {
            return -1;
        }
    }

    private static int minuteOfDay(long now, ZoneId zone) {
        Instant instant = Instant.ofEpochMilli(now);
        return instant.atZone(zone).get(ChronoField.HOUR_OF_DAY) * 60
                + instant.atZone(zone).get(ChronoField.MINUTE_OF_HOUR);
    }
}
