package com.msrafi.homeinfo;

import org.json.JSONObject;

import java.io.IOException;
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

    private Weather() { }

    static Now fetch(double latitude, double longitude) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,apparent_temperature,weather_code,is_day,"
                        + "wind_speed_10m,relative_humidity_2m"
                        + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto",
                latitude, longitude);
        return parse(Net.get(url));
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

    /** One character beside the temperature. Night and clear is a moon, matching a dark sky. */
    static String symbol(int code, boolean day) {
        if (code == 0) return day ? "\u2600" : "\u263E";
        if (code <= 3) return "\u2601";
        if (code == 45 || code == 48) return "\u224B";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "\u2744";
        if (code >= 95) return "\u2607";
        return "\u2602";
    }

    static void checkCoordinates(double latitude, double longitude) throws IOException {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IOException("coordinates out of range");
        }
    }
}
