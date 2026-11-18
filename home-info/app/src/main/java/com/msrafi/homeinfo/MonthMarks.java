package com.msrafi.homeinfo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The days to mark on this month's grid. US holidays come from the feed. Islamic days are worked
 * out on the device with the same Umm al-Qura date the Hijri line uses, so a local moon sighting
 * can still put one of them a day to either side.
 */
final class MonthMarks {
    private MonthMarks() { }

    /** "11\tVeterans Day" lines for the US holidays in the month that contains {@code today}. */
    static String usMarks(String ics, LocalDate today, ZoneId zone) {
        StringBuilder out = new StringBuilder();
        YearMonth month = YearMonth.from(today);
        String previous = "";
        for (Agenda.Holiday holiday : Agenda.inMonth(ics, today, zone)) {
            if (!YearMonth.from(holiday.date).equals(month)) continue;
            String title = holiday.title;
            if (title.isEmpty() || !holiday(title)) continue;
            String key = holiday.date.getDayOfMonth() + "\t" + title;
            if (key.equals(previous)) continue;
            previous = key;
            if (out.length() > 0) out.append('\n');
            out.append(holiday.date.getDayOfMonth()).append('\t').append(title);
        }
        return out.toString();
    }

    /**
     * First line is {@code yyyy-MM}. Each later line is
     * {@code day \t us|hijri|both|start|us-start \t us title \t hijri title}.
     * {@code start} is the first day of a Hijri month. {@code us-start} is that day and a US holiday.
     * A US list from another month is ignored.
     */
    static String grid(LocalDate today, String usMarks, String usMonth) {
        YearMonth month = YearMonth.from(today);
        Map<Integer, String> us = month.toString().equals(usMonth) ? parseUs(usMarks) : new HashMap<>();
        StringBuilder out = new StringBuilder();
        out.append(month);
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            HijrahDate hijri = HijrahDate.from(month.atDay(day));
            int hijriMonth = hijri.get(ChronoField.MONTH_OF_YEAR);
            int hijriDay = hijri.get(ChronoField.DAY_OF_MONTH);
            String islamic = islamic(hijriMonth, hijriDay);
            String holiday = us.get(day);
            if (islamic == null && holiday == null) continue;
            boolean monthStart = hijriDay == 1;
            String kind = monthStart && holiday != null ? "us-start"
                    : monthStart ? "start"
                    : islamic != null && holiday != null ? "both"
                    : islamic != null ? "hijri" : "us";
            out.append('\n').append(day).append('\t').append(kind).append('\t')
                    .append(holiday == null ? "" : holiday).append('\t')
                    .append(islamic == null ? "" : islamic);
        }
        return out.toString();
    }

    /** The label for this Hijri day, or null when the grid should leave it plain. */
    static String islamic(int month, int day) {
        if (month == 1 && day == 10) return "Ashura";
        if (month == 3 && day == 12) return "Mawlid";
        if (month == 7 && day == 27) return "Isra and Mi'raj";
        if (month == 8 && day == 15) return "Mid-Shaban";
        if (month == 9 && day == 1) return "Ramadan";
        if (month == 9 && day == 27) return "Laylat al-Qadr";
        if (month == 10 && day == 1) return "Eid al-Fitr";
        if (month == 12 && day == 9) return "Day of Arafah";
        if (month == 12 && day == 10) return "Eid al-Adha";
        if (day == 1) return "1 " + Present.hijriMonthName(month);
        return null;
    }

    /** The public US calendar also lists the clock change and Black Friday. Those are not holidays. */
    private static boolean holiday(String title) {
        String name = title.toLowerCase(Locale.ROOT);
        return !name.contains("daylight saving") && !"black friday".equals(name);
    }

    private static Map<Integer, String> parseUs(String marks) {
        Map<Integer, String> out = new HashMap<>();
        if (marks == null || marks.isEmpty()) return out;
        for (String line : marks.split("\n", -1)) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            try {
                int day = Integer.parseInt(line.substring(0, tab).trim());
                String title = line.substring(tab + 1).trim();
                if (day >= 1 && day <= 31 && !title.isEmpty() && holiday(title)) {
                    String already = out.get(day);
                    out.put(day, already == null ? title : already + " · " + title);
                }
            } catch (NumberFormatException ignored) {
                // A broken line is skipped. The other days still mark.
            }
        }
        return out;
    }
}
