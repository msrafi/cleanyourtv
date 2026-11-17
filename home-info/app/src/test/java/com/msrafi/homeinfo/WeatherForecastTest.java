package com.msrafi.homeinfo;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeatherForecastTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Test
    public void theNextFiveDaysSkipToday() {
        String json = "{\"current\":{\"time\":\"2026-11-17T07:00\",\"weather_code\":0},"
                + "\"daily\":{\"time\":[\"2026-11-17\",\"2026-11-18\",\"2026-11-19\",\"2026-11-20\","
                + "\"2026-11-21\",\"2026-11-22\"],"
                + "\"weather_code\":[1,0,2,61,3,71],"
                + "\"temperature_2m_max\":[10.0,0.0,5.2,8.0,12.4,1.1],"
                + "\"temperature_2m_min\":[0.0,-5.0,1.0,2.0,4.0,-2.0]}}";
        String days = Weather.forecast(json, TODAY, true, Locale.US);
        String[] lines = days.split("\n");
        assertEquals(5, lines.length);
        assertEquals("Wed\tclear\t32°\t23°", lines[0]);
        assertTrue(lines[1].startsWith("Thu\t"));
        assertTrue(lines[4].startsWith("Sun\t"));
        assertTrue(!days.contains("Tue"));
    }

    @Test
    public void celsiusKeepsTheFeedNumbers() {
        String json = "{\"daily\":{\"time\":[\"2026-11-18\"],\"weather_code\":[0],"
                + "\"temperature_2m_max\":[10.2],\"temperature_2m_min\":[-1.4]}}";
        assertEquals("Wed\tclear\t10°\t-1°", Weather.forecast(json, TODAY, false, Locale.US));
    }

    @Test
    public void aReplyWithoutDaysStaysBlank() {
        assertEquals("", Weather.forecast("{\"current\":{\"weather_code\":0}}", TODAY, true, Locale.US));
    }
}
