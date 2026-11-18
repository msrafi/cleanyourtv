package com.msrafi.homeinfo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.Locale;

/** The words the home strip shows. Weather stays numeric. The calendar column is the Hijri date, then the India holiday. */
final class Present {
    final String symbol;
    final String temperature;
    final String condition;
    final String feels;
    final String place;
    final String metrics;
    final String eventKicker;
    final String eventTitle;
    final String eventWhen;

    private Present(String symbol, String temperature, String condition, String feels, String place, String metrics,
                    String eventKicker, String eventTitle, String eventWhen) {
        this.symbol = symbol;
        this.temperature = temperature;
        this.condition = condition;
        this.feels = feels;
        this.place = place;
        this.metrics = metrics;
        this.eventKicker = eventKicker;
        this.eventTitle = eventTitle;
        this.eventWhen = eventWhen;
    }

    static Present of(Weather.Now weather, String place, boolean fahrenheit, boolean mph,
                      Agenda.Item event, long now, ZoneId zone, Locale locale, boolean hour24) {
        String symbol = "";
        String temperature = "";
        String condition = "";
        String feels = "";
        String shownPlace = "";
        String metrics = "";
        if (weather != null) {
            symbol = Weather.symbol(weather.code, weather.day);
            temperature = degrees(weather.tempC, fahrenheit);
            condition = Weather.condition(weather.code);
            feels = "Feels like " + degrees(weather.feelsC, fahrenheit);
            shownPlace = place == null ? "" : place;
            metrics = wind(weather.windKmh, mph) + (weather.humidity >= 0 ? "    " + weather.humidity + "%" : "");
        }
        String kicker = "";
        String title = "";
        String when = "";
        if (event != null && !event.title.isEmpty()) {
            kicker = kicker(event, now, zone);
            title = event.title;
            when = when(event, now, zone, locale, hour24);
        }
        return new Present(symbol, temperature, condition, feels, shownPlace, metrics, kicker, title, when);
    }

    static String degrees(double celsius, boolean fahrenheit) {
        double value = fahrenheit ? celsius * 9d / 5d + 32d : celsius;
        return Math.round(value) + (fahrenheit ? "°F" : "°C");
    }

    static String wind(double kmh, boolean mph) {
        if (mph) return Math.round(kmh * 0.621371d) + " mph";
        return Math.round(kmh) + " km/h";
    }

    static String hijriMonthName(int month) {
        if (month < 1 || month > HIJRI_MONTHS.length) return Integer.toString(month);
        return HIJRI_MONTHS[month - 1];
    }

    private static final String[] HIJRI_MONTHS = {
            "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Shaban",
            "Ramadan", "Shawwal", "Dhul Qadah", "Dhul Hijjah"
    };

    /** "US · Nov 26 · Thanksgiving Day", or "US · none soon" when the feed has nothing in the window. */
    static String holidayLine(String label, Agenda.Item event, ZoneId zone, Locale locale) {
        if (event == null) return label + " · none soon";
        LocalDate day = event.allDay
                ? eventDay(event)
                : Instant.ofEpochMilli(event.startMs).atZone(zone).toLocalDate();
        String date = day.format(DateTimeFormatter.ofPattern("MMM d", locale));
        return label + " · " + date + " · " + event.title;
    }

    /**
     * Today's date on the Umm al-Qura calendar, the civil Hijri calendar Java ships.
     * A local moon sighting can fall a day to either side of it.
     */
    static String hijriLine(long now, ZoneId zone) {
        HijrahDate hijri = HijrahDate.from(Instant.ofEpochMilli(now).atZone(zone).toLocalDate());
        int month = hijri.get(ChronoField.MONTH_OF_YEAR);
        return "Hijri · " + hijri.get(ChronoField.DAY_OF_MONTH) + " " + hijriMonthName(month)
                + " " + hijri.get(ChronoField.YEAR);
    }

    static String kicker(Agenda.Item event, long now, ZoneId zone) {
        if (event.allDay && eventDay(event).equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())) return "TODAY";
        if (event.startMs <= now) return "NOW";
        return "UP NEXT";
    }

    static String when(Agenda.Item event, long now, ZoneId zone, Locale locale, boolean hour24) {
        if (event.allDay) {
            LocalDate day = eventDay(event);
            LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            if (day.equals(today)) return "All day";
            return day.format(DateTimeFormatter.ofPattern("EEE", locale));
        }
        String pattern = hour24 ? "EEE · HH:mm" : "EEE · h:mm a";
        return Instant.ofEpochMilli(event.startMs).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    private static LocalDate eventDay(Agenda.Item event) {
        return Instant.ofEpochMilli(event.startMs).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
