package com.msrafi.homeinfo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The next event in an iCalendar feed. All-day dates are kept as UTC midnights, which is how
 * Android stores them, so a holiday lands on the right local day. Yearly rules are projected
 * onto this year and the next. Anything else is taken as the single start it already has.
 */
final class Agenda {
    static final long WINDOW_MS = 30L * 24 * 60 * 60 * 1000;
    private static final DateTimeFormatter BASIC_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASIC_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);
    private static final DateTimeFormatter BASIC_LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);

    static final class Item {
        final String title;
        final long startMs;
        final long endMs;
        final boolean allDay;

        Item(String title, long startMs, long endMs, boolean allDay) {
            this.title = title;
            this.startMs = startMs;
            this.endMs = endMs;
            this.allDay = allDay;
        }
    }

    private Agenda() { }

    /** The event that should be on the strip, or null when the feed has nothing in the window. */
    static Item next(String ics, long now, ZoneId zone) {
        if (ics == null || ics.isEmpty()) return null;
        Item best = null;
        List<String> block = null;
        for (String line : unfold(ics)) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line.trim())) {
                block = new ArrayList<>();
            } else if ("END:VEVENT".equalsIgnoreCase(line.trim())) {
                if (block != null) best = choose(best, fromEvent(block, now, zone), now);
                block = null;
            } else if (block != null) {
                block.add(line);
            }
        }
        return best;
    }

    /**
     * Ongoing timed events beat an all-day holiday, an event already underway beats a later one,
     * and otherwise the soonest start in the window wins.
     */
    static Item choose(Item current, Item candidate, long now) {
        if (candidate == null || candidate.endMs <= now || candidate.startMs > now + WINDOW_MS) return current;
        if (current == null) return candidate;
        Item ongoing = timedOngoing(current, now);
        if (ongoing == null) ongoing = timedOngoing(candidate, now);
        if (ongoing != null) return ongoing;
        if (covers(current, now) && !covers(candidate, now)) return current;
        if (covers(candidate, now) && !covers(current, now)) return candidate;
        return candidate.startMs < current.startMs ? candidate : current;
    }

    private static Item timedOngoing(Item item, long now) {
        return item != null && !item.allDay && item.startMs <= now && now < item.endMs ? item : null;
    }

    private static boolean covers(Item item, long now) {
        return item.startMs <= now && now < item.endMs;
    }

    private static Item fromEvent(List<String> lines, long now, ZoneId zone) {
        String summary = "";
        String startRaw = null;
        String startParams = "";
        String rule = "";
        boolean cancelled = false;
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String left = line.substring(0, colon);
            String value = unescape(line.substring(colon + 1).trim());
            int semi = left.indexOf(';');
            String name = (semi < 0 ? left : left.substring(0, semi)).toUpperCase(Locale.ROOT);
            String params = semi < 0 ? "" : left.substring(semi + 1);
            if ("SUMMARY".equals(name)) summary = value;
            else if ("DTSTART".equals(name)) {
                startRaw = value;
                startParams = params;
            } else if ("RRULE".equals(name)) rule = value;
            else if ("STATUS".equals(name) && "CANCELLED".equalsIgnoreCase(value)) cancelled = true;
        }
        if (cancelled || summary.isEmpty() || startRaw == null) return null;
        boolean allDay = startParams.toUpperCase(Locale.ROOT).contains("VALUE=DATE") && !startRaw.contains("T");
        ZoneId when = zoneOf(startParams, zone);
        Long start = parseStart(startRaw, allDay, when);
        if (start == null) return null;
        Map<String, String> rrule = rule(rule);
        if ("YEARLY".equals(rrule.get("FREQ"))) return yearly(summary, start, allDay, when, rrule, now);
        long end = allDay ? start + 24 * 60 * 60 * 1000L : start + 60 * 60 * 1000L;
        return new Item(summary, start, end, allDay);
    }

    /** This year's occurrence, or next year's when this year's has already ended. */
    private static Item yearly(String title, long firstStart, boolean allDay, ZoneId zone,
                               Map<String, String> rule, long now) {
        LocalDate first = Instant.ofEpochMilli(firstStart).atZone(allDay ? ZoneOffset.UTC : zone).toLocalDate();
        int month = number(rule.get("BYMONTH"), first.getMonthValue());
        int day = number(rule.get("BYMONTHDAY"), first.getDayOfMonth());
        int year = Instant.ofEpochMilli(now).atZone(zone).getYear();
        Item best = null;
        for (int y = year - 1; y <= year + 1; y++) {
            LocalDate date;
            try {
                date = LocalDate.of(y, month, day);
            } catch (RuntimeException bad) {
                continue;
            }
            if (untilBefore(rule.get("UNTIL"), date, zone)) continue;
            long start = allDay
                    ? date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    : date.atTime(Instant.ofEpochMilli(firstStart).atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli();
            long end = allDay ? start + 24 * 60 * 60 * 1000L : start + 60 * 60 * 1000L;
            best = choose(best, new Item(title, start, end, allDay), now);
        }
        return best;
    }

    private static boolean untilBefore(String until, LocalDate date, ZoneId zone) {
        if (until == null || until.isEmpty()) return false;
        try {
            if (until.endsWith("Z") && until.indexOf('T') > 0) {
                Instant limit = java.time.LocalDateTime.parse(until, BASIC_UTC).toInstant(ZoneOffset.UTC);
                return date.atStartOfDay(zone).toInstant().isAfter(limit);
            }
            LocalDate limit = LocalDate.parse(until.substring(0, Math.min(8, until.length())), BASIC_DAY);
            return date.isAfter(limit);
        } catch (RuntimeException bad) {
            return false;
        }
    }

    private static Long parseStart(String value, boolean allDay, ZoneId zone) {
        try {
            if (allDay) {
                LocalDate date = LocalDate.parse(value.substring(0, Math.min(8, value.length())), BASIC_DAY);
                return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            }
            if (value.endsWith("Z")) {
                return java.time.LocalDateTime.parse(value, BASIC_UTC).toInstant(ZoneOffset.UTC).toEpochMilli();
            }
            return java.time.LocalDateTime.parse(value, BASIC_LOCAL).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException | IndexOutOfBoundsException bad) {
            return null;
        }
    }

    private static ZoneId zoneOf(String params, ZoneId fallback) {
        for (String part : params.split(";")) {
            if (part.toUpperCase(Locale.ROOT).startsWith("TZID=")) {
                try {
                    return ZoneId.of(part.substring(5));
                } catch (RuntimeException bad) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    private static Map<String, String> rule(String rrule) {
        Map<String, String> map = new HashMap<>();
        if (rrule == null || rrule.isEmpty()) return map;
        for (String part : rrule.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) map.put(part.substring(0, eq).toUpperCase(Locale.ROOT), part.substring(eq + 1));
        }
        return map;
    }

    private static int number(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw.split(",")[0]);
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    private static String unescape(String value) {
        return value.replace("\\n", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim();
    }

    static List<String> unfold(String ics) {
        String[] raw = ics.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> lines = new ArrayList<>();
        StringBuilder current = null;
        for (String line : raw) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && current != null) {
                current.append(line.substring(1));
            } else {
                if (current != null) lines.add(current.toString());
                current = new StringBuilder(line);
            }
        }
        if (current != null) lines.add(current.toString());
        return lines;
    }
}
