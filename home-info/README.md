# Home info

The top of Home can show the weather and the next calendar event beside the clock. This app fetches both. The launcher stays offline and only reads one row from here.

Nothing has been installed on the TV. Building this folder does not change the set.

## What shows

Beside the clock, once this app is installed:

- A symbol, the temperature, and the condition.
- Feels-like, the place, the wind, and the humidity.
- Up next: the next event's name and when it is. An event happening now is labelled NOW. An all-day event today is labelled TODAY.

The place is Iselin, NJ, in Fahrenheit and mph. That is `app/src/main/res/values/config.xml`. Set `weather_fahrenheit` to false for Celsius and km/h.

The three rows are the next US holiday, the next India holiday, and today's Hijri date. The holidays come from the public Google calendars in `calendar_feeds`. The Hijri date is the Umm al-Qura civil calendar, worked out on the TV.

## How it stays off the launcher's network

Home looks for the package `com.msrafi.homeinfo`. On each return to Home, and about once a minute after that, the launcher queries `content://com.msrafi.homeinfo.now/now` and copies the text into the clock row. Open-Meteo and the calendar feed are contacted from this app, about every 15 minutes while Home is open. The last good weather line stays up if a later fetch fails.

## Install, when you want it on the TV

From this folder, with JDK 17 and Android SDK 34:

```bash
./gradlew --no-daemon assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. Install it together with a launcher built from this checkout. Until this app is on the TV, Home looks as it does now.

The app is in the launcher's hidden list, so it does not take a tile.
