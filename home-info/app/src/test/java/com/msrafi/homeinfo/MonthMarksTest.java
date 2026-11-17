package com.msrafi.homeinfo;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MonthMarksTest {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    @Test
    public void theFourthThursdayIsThanksgivingAndAPassedHolidayStays() {
        String ics = "BEGIN:VEVENT\n"
                + "DTSTART;VALUE=DATE:20241128\n"
                + "RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=4TH\n"
                + "SUMMARY:Thanksgiving Day\n"
                + "END:VEVENT\n"
                + "BEGIN:VEVENT\n"
                + "DTSTART;VALUE=DATE:20261111\n"
                + "SUMMARY:Veterans Day\n"
                + "END:VEVENT\n";
        assertEquals("11\tVeterans Day\n26\tThanksgiving Day",
                MonthMarks.usMarks(ics, LocalDate.of(2026, 11, 17), ZONE));
    }

    @Test
    public void novemberMarksTheMonthStartAndTheUsHolidayOnTheSameDay() {
        String grid = MonthMarks.grid(LocalDate.of(2026, 11, 17),
                "11\tVeterans Day\n26\tThanksgiving Day", "2026-11");
        assertTrue(grid.startsWith("2026-11\n"));
        assertTrue(grid.contains("\n11\tboth\tVeterans Day\t1 Jumada al-Thani"));
        assertTrue(grid.contains("\n26\tus\tThanksgiving Day\t"));
    }

    @Test
    public void theClockChangeAndBlackFridayAreNotHolidays() {
        String marks = "1\tDaylight Saving Time ends\n11\tVeterans Day\n27\tBlack Friday";
        String grid = MonthMarks.grid(LocalDate.of(2026, 11, 17), marks, "2026-11");
        assertTrue(grid.contains("Veterans Day"));
        assertTrue(!grid.contains("Daylight"));
        assertTrue(!grid.contains("Black Friday"));
    }

    @Test
    public void aListFromAnotherMonthIsIgnored() {
        String grid = MonthMarks.grid(LocalDate.of(2026, 11, 17), "26\tThanksgiving Day", "2026-10");
        assertTrue(!grid.contains("Thanksgiving"));
        assertTrue(grid.contains("1 Jumada al-Thani"));
    }

    @Test
    public void namedIslamicDaysWinOverThePlainMonthStart() {
        assertEquals("Eid al-Fitr", MonthMarks.islamic(10, 1));
        assertEquals("Ramadan", MonthMarks.islamic(9, 1));
        assertEquals("Ashura", MonthMarks.islamic(1, 10));
        assertEquals("Eid al-Adha", MonthMarks.islamic(12, 10));
        assertEquals("1 Rajab", MonthMarks.islamic(7, 1));
        assertNull(MonthMarks.islamic(6, 6));
        HijrahDate shawwal = null;
        LocalDate cursor = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 400 && shawwal == null; i++) {
            HijrahDate hijri = HijrahDate.from(cursor);
            if (hijri.get(ChronoField.MONTH_OF_YEAR) == 10 && hijri.get(ChronoField.DAY_OF_MONTH) == 1) shawwal = hijri;
            cursor = cursor.plusDays(1);
        }
        assertEquals(10, shawwal.get(ChronoField.MONTH_OF_YEAR));
        String grid = MonthMarks.grid(LocalDate.from(shawwal), "", "");
        assertTrue(grid.contains("\tEid al-Fitr"));
    }
}
