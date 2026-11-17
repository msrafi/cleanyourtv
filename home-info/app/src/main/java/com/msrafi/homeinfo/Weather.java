package com.msrafi.homeinfo;

import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Current conditions from Open-Meteo. No key. The request sends only the latitude and longitude
 * from config.xml. Values stay in Celsius and km/h; the strip converts them for display.
 */
final class Weather {
    static final class Now {
        final double tempC;
        final double feelsC;
        final int code;
        final boolean day;
        final int humidity;
        final double windKmh;

        Now(double tempC, double feelsC, int code, boolean day, int humidity, double windKmh) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.code = code;
            this.day = day;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }
    }

    /** Current conditions plus the next five days, from one reply. {@code days} may be empty. */
    static final class Report {
        final Now now;
        final String days;

        Report(Now now, String days) {
            this.now = now;
            this.days = days;
        }
    }

    private Weather() { }

    static Report fetch(double latitude, double longitude, LocalDate today, boolean fahrenheit, Locale locale) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,apparent_temperature,weather_code,is_day,"
                        + "wind_speed_10m,relative_humidity_2m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=6"
                        + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto",
                latitude, longitude);
        String json = Net.get(url);
        return new Report(parse(json), forecast(json, today, fahrenheit, locale));
    }

    /**
     * The five days after {@code today}. Each line is {@code Wed \t icon \t high \t low}.
     * Today is left out, because the strip already shows it. An empty string means the feed had no days.
     */
    static String forecast(String json, LocalDate today, boolean fahrenheit, Locale locale) {
        if (json == null || today == null) return "";
        int daily = json.indexOf("\"daily\"");
        if (daily < 0) return "";
        String section = json.substring(daily);
        String[] dates = texts(section, "time");
        int[] codes = ints(section, "weather_code");
        double[] highs = doubles(section, "temperature_2m_max");
        double[] lows = doubles(section, "temperature_2m_min");
        if (dates == null || codes == null || highs == null || lows == null) return "";
        int count = dates.length;
        if (codes.length < count) count = codes.length;
        if (highs.length < count) count = highs.length;
        if (lows.length < count) count = lows.length;
        DateTimeFormatter day = DateTimeFormatter.ofPattern("EEE", locale == null ? Locale.US : locale);
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (int i = 0; i < count && kept < 5; i++) {
            LocalDate date;
            try {
                date = LocalDate.parse(dates[i]);
            } catch (RuntimeException bad) {
                continue;
            }
            if (!date.isAfter(today)) continue;
            if (out.length() > 0) out.append('\n');
            out.append(day.format(date)).append('\t')
                    .append(kind(codes[i])).append('\t')
                    .append(degree(highs[i], fahrenheit)).append('\t')
                    .append(degree(lows[i], fahrenheit));
            kept++;
        }
        return out.toString();
    }

    static String degree(double celsius, boolean fahrenheit) {
        double value = fahrenheit ? celsius * 9d / 5d + 32d : celsius;
        return Math.round(value) + "°";
    }

    static Now parse(String json) throws Exception {
        JSONObject current = new JSONObject(json).getJSONObject("current");
        return new Now(
                current.getDouble("temperature_2m"),
                current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
                current.getInt("weather_code"),
                current.optInt("is_day", 1) == 1,
                current.optInt("relative_humidity_2m", -1),
                current.optDouble("wind_speed_10m", 0));
    }

    /** A short condition name for a WMO weather code. */
    static String condition(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Thunderstorm";
        return "Mixed";
    }

    /** What the five-day row draws. Daytime, since a daily high is not a night reading. */
    static String kind(int code) {
        if (code == 0) return "clear";
        if (code <= 2) return "partly";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if (code >= 95) return "storm";
        if (code >= 51) return "rain";
        return "cloud";
    }

    /** One character beside the temperature. Night and clear is a moon, matching a dark sky. */
    static String symbol(int code, boolean day) {
        if (code == 0) return day ? "\u2600" : "\u263E";
        if (code <= 3) return "\u2601";
        if (code == 45 || code == 48) return "\u224B";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "\u2744";
        if (code >= 95) return "\u2607";
        return "\u2602";
    }

    private static String[] texts(String json, String key) {
        String body = array(json, key);
        if (body == null) return null;
        String[] parts = body.split(",", -1);
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = parts[i].trim().replace("\"", "");
        }
        return out;
    }

    private static int[] ints(String json, String key) {
        double[] values = doubles(json, key);
        if (values == null) return null;
        int[] out = new int[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (int) Math.round(values[i]);
        return out;
    }

    private static double[] doubles(String json, String key) {
        String body = array(json, key);
        if (body == null || body.trim().isEmpty()) return null;
        String[] parts = body.split(",", -1);
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException bad) {
                return null;
            }
        }
        return out;
    }

    /** The inside of the first JSON array named {@code key}, or null when it is missing. */
    private static String array(String json, String key) {
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) return null;
        int start = json.indexOf('[', at);
        int end = start < 0 ? -1 : json.indexOf(']', start);
        if (start < 0 || end < 0) return null;
        return json.substring(start + 1, end);
    }

    static void checkCoordinates(double latitude, double longitude) throws IOException {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IOException("coordinates out of range");
        }
    }
}
