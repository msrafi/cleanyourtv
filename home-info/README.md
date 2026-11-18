# Home info

The top of Home can show the weather and the next calendar event beside the clock. This app fetches both. The launcher stays offline and only reads one row from here.

This app is already on the TV. Building this folder does not change the set until `tools/push-home.sh` installs it.

## What shows

Beside the clock, once this app is installed:

- A symbol, the temperature, and the condition.
- Feels-like, the place, the wind, and the humidity.
- Under today's weather, still inside that column, the next five days in a small row: the weekday, an icon, the high, and the low. Today is the line above, so it is not repeated.
- Up next: the next event's name and when it is. An event happening now is labelled NOW. An all-day event today is labelled TODAY.

The place is Iselin, NJ, in Fahrenheit and mph. That is `app/src/main/res/values/config.xml`. Set `weather_fahrenheit` to false for Celsius and km/h.

The column shows today's Hijri date, then the next India holiday. The time in India sits under those two, in the same light numerals as the clock. The US holiday feed is still read, and it marks the month grid only. The holidays come from the public Google calendars in `calendar_feeds`. The Hijri date is the Umm al-Qura civil calendar, worked out on the TV.

A third column lists today's Fajr, sunrise, Dhuhr, Asr, Maghrib and Isha. The times come from Aladhan, with `prayer_method` and `prayer_school` in the same config file. The next time still ahead is shown in white. A failed fetch keeps the last times.

Under the clock, three columns: up to five reminders, this month, and the important days. US holidays from that same feed are gold. The 1st of each Hijri month is blue, including Ramadan and Eid al-Fitr. The other Islamic days are green: Ashura, Mawlid, Isra and Mi'raj, mid-Shaban, Laylat al-Qadr, the Day of Arafah, and Eid al-Adha. A local moon sighting can put an Islamic day one off. The lines in `reminders` are the first list. After that, a phone on the same Wi-Fi can change them, and can put one picture behind Home: open `http://<tv ip>:8787`. The picture stays until that page puts the theme picture back. The page answers only a home-network address, and a save from another website is refused. Do not forward port 8787 on the router. Once the page has saved a list, that list is what Home shows, including after this app is installed again. Clearing the app's data brings back the lines in the config file.

## How it stays off the launcher's network

Home looks for the package `com.msrafi.homeinfo`. On each return to Home, and about once a minute after that, the launcher queries `content://com.msrafi.homeinfo.now/now` and copies the text into the clock row. Open-Meteo and the calendar feed are contacted from this app, about every 15 minutes while Home is open. The last good weather line stays up if a later fetch fails.

## Install, when you want it on the TV

From this folder, with JDK 17 and Android SDK 34:

```bash
./gradlew --no-daemon assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. `tools/push-home.sh` installs it and reopens Home. The launcher does not need to be rebuilt for a reminder change.

The app is in the launcher's hidden list, so it does not take a tile.
