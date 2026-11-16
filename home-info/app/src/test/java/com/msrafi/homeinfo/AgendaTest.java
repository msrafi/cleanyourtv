package com.msrafi.homeinfo;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AgendaTest {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    /** 16 Nov 2026, 09:00 in New York. */
    private static final long NOW = LocalDateTime.of(2026, 11, 16, 9, 0).atZone(ZONE).toInstant().toEpochMilli();

    @Test
    public void aFutureAllDayEventIsTheOneShown() {
        Agenda.Item item = Agenda.next(event("20261120", "Dussehra (Saptami)"), NOW, ZONE);
        assertEquals("Dussehra (Saptami)", item.title);
        assertEquals(true, item.allDay);
        assertEquals("UP NEXT", Present.kicker(item, NOW, ZONE));
        assertEquals("Fri", Present.when(item, NOW, ZONE, java.util.Locale.US, false));
    }

    @Test
    public void aPastEventIsSkipped() {
        assertNull(Agenda.next(event("20261101", "Already gone"), NOW, ZONE));
    }

    @Test
    public void aYearlyRuleLandsOnTheNextOccurrence() {
        String ics = "BEGIN:VEVENT\n"
                + "DTSTART;VALUE=DATE:20201017\n"
                + "RRULE:FREQ=YEARLY\n"
                + "SUMMARY:Dussehra\n"
                + "END:VEVENT\n";
        // 17 Oct 2026 has passed, and Oct 2027 is outside the 30-day window.
        assertNull(Agenda.next(ics, NOW, ZONE));
        long before = LocalDateTime.of(2026, 10, 10, 9, 0).atZone(ZONE).toInstant().toEpochMilli();
        Agenda.Item item = Agenda.next(ics, before, ZONE);
        assertEquals("Dussehra", item.title);
        assertEquals("UP NEXT", Present.kicker(item, before, ZONE));
    }

    @Test
    public void anOngoingTimedEventBeatsALaterHoliday() {
        Agenda.Item holiday = Agenda.next(event("20261116", "Holiday"), NOW, ZONE);
        Agenda.Item meeting = new Agenda.Item("Standup",
                NOW - 10 * 60 * 1000L, NOW + 20 * 60 * 1000L, false);
        Agenda.Item chosen = Agenda.choose(holiday, meeting, NOW);
        assertEquals("Standup", chosen.title);
        assertEquals("NOW", Present.kicker(chosen, NOW, ZONE));
    }

    @Test
    public void aFoldedSummaryIsJoined() {
        // The space at the start of the next line is the fold marker and is removed. The space that
        // belongs to the title sits at the end of the first line.
        String ics = "BEGIN:VEVENT\nDTSTART;VALUE=DATE:20261120\nSUMMARY:Dussehra \n (Saptami)\nEND:VEVENT\n";
        assertEquals("Dussehra (Saptami)", Agenda.next(ics, NOW, ZONE).title);
    }

    @Test
    public void clearNightFormatsLikeTheReferenceStrip() {
        Weather.Now now = new Weather.Now(29.2, 33.1, 0, false, 61, 4.2);
        assertEquals("\u263E", Weather.symbol(now.code, now.day));
        assertEquals("Clear", Weather.condition(now.code));
        Present present = Present.of(now, "Surat, Gujarat, India", false, false, null, NOW, ZONE,
                java.util.Locale.US, false);
        assertEquals("29°C", present.temperature);
        assertEquals("Feels like 33°C", present.feels);
        assertEquals("Surat, Gujarat, India", present.place);
        assertEquals("4 km/h    61%", present.metrics);
    }

    @Test
    public void eachCalendarKeepsItsOwnLine() {
        Agenda.Item thanksgiving = Agenda.next(event("20261126", "Thanksgiving Day"), NOW, ZONE);
        assertEquals("US · Nov 26 · Thanksgiving Day", Present.holidayLine("US", thanksgiving, ZONE, java.util.Locale.US));
        assertEquals("India · none soon", Present.holidayLine("India", null, ZONE, java.util.Locale.US));
    }

    @Test
    public void hijriDateForThisMorning() {
        assertEquals("Hijri · 6 Jumada al-Thani 1448", Present.hijriLine(NOW, ZONE));
    }

    @Test
    public void fahrenheitIsUsedForIselin() {
        assertEquals("84°F", Present.degrees(29.0, true));
        assertEquals("2 mph", Present.wind(4.0, true));
    }

    private static String event(String day, String title) {
        return "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART;VALUE=DATE:" + day + "\nSUMMARY:" + title
                + "\nEND:VEVENT\nEND:VCALENDAR\n";
    }

}
