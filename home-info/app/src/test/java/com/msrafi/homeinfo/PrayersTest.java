package com.msrafi.homeinfo;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PrayersTest {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    /** 16 Nov 2026, 10:00 in New York. Dhuhr at 11:42 is the next time. */
    private static final long NOW = LocalDateTime.of(2026, 11, 16, 10, 0).atZone(ZONE).toInstant().toEpochMilli();
    private static final String JSON = "{\"data\":{\"timings\":{"
            + "\"Fajr\":\"05:32 (EST)\",\"Sunrise\":\"06:51 (EST)\",\"Dhuhr\":\"11:42 (EST)\","
            + "\"Asr\":\"14:28 (EST)\",\"Maghrib\":\"16:45 (EST)\",\"Isha\":\"18:02 (EST)\"}}}";

    @Test
    public void aTimingSuffixIsDropped() {
        assertEquals("Fajr=05:32;Sunrise=06:51;Dhuhr=11:42;Asr=14:28;Maghrib=16:45;Isha=18:02",
                Prayers.timings(JSON));
    }

    @Test
    public void aMissingPrayerRejectsTheAnswer() {
        assertNull(Prayers.timings("{\"timings\":{\"Fajr\":\"05:32\"}}"));
    }

    @Test
    public void theNextTimeStillAheadIsMarked() {
        String raw = Prayers.timings(JSON);
        String lines = Prayers.lines(raw, NOW, ZONE, "2026-11-16", false);
        assertEquals("Fajr\t5:32 AM\tcurrent\n"
                + "Sunrise\t6:51 AM\n"
                + "Dhuhr\t11:42 AM · 1h 42m\tnext\n"
                + "Asr\t2:28 PM\n"
                + "Maghrib\t4:45 PM\n"
                + "Isha\t6:02 PM", lines);
    }

    @Test
    public void sunriseIsNotTheCurrentPrayer() {
        long noon = LocalDateTime.of(2026, 11, 16, 12, 0).atZone(ZONE).toInstant().toEpochMilli();
        String lines = Prayers.lines(Prayers.timings(JSON), noon, ZONE, "2026-11-16", false);
        assertEquals("Dhuhr\t11:42 AM\tcurrent", lines.split("\n")[2]);
        assertEquals("Asr\t2:28 PM · 2h 28m\tnext", lines.split("\n")[3]);
    }

    @Test
    public void yesterdaysTimesStayUnmarked() {
        String lines = Prayers.lines(Prayers.timings(JSON), NOW, ZONE, "2026-11-15", false);
        assertEquals(-1, lines.indexOf("next"));
        assertEquals("Dhuhr\t11:42 AM", lines.split("\n")[2]);
    }

    @Test
    public void aTwentyFourHourClockKeepsTheHour() {
        assertEquals("14:28", Prayers.clock("14:28", true));
        assertEquals("12:00 AM", Prayers.clock("00:00", false));
        assertEquals("12:05 PM", Prayers.clock("12:05", false));
    }
}
