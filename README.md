# Nimbus

A fast, modern Android weather app with live home screen widgets, rain alerts and one-tap
in-app updates.

## Highlights

- **Always current.** Shows *when* the data was fetched ("Updated 2 min ago"). It refreshes
  whenever you open the app, every 15 minutes in the background (adjustable), and whenever you pull
  down. Between fetches the temperature follows the forecast timeline, so an hour-old fetch shows the
  temperature for *now*, not for an hour ago.
- **Home screen widgets with a refresh button.** A compact "current weather" widget and a forecast
  widget (current + hourly + daily). Both resize, show the time they were updated (or "Offline"), and
  have a ↻ button that fetches fresh data on the spot. Each widget can follow a different place.
- **Animated sky.** The background changes with conditions and time of day, including dawn and dusk.
  It draws rain angled by the real wind, snow, stars, a moon in its actual phase, drifting clouds,
  fog and lightning.
- **Rain in the next two hours.** 15-minute precipitation data shows "Light rain starting in about
  20 min, lasting about 45 min", with an intensity chart and an optional notification before it starts.
- **Plain-English overview.** For example: "Partly cloudy today. Rain likely from 3 PM to 6 PM. High
  of 18°, 4° cooler than yesterday."
- **Rich detail.** A 48-hour chart (temperature, rain chance, wind), a 14-day forecast with
  temperature range bars, UV with "use sun protection until…", sunrise/sunset arc, wind compass,
  precipitation totals, feels-like explanation, humidity & dew point, visibility, pressure trend,
  moon phase, cloud cover, air quality (US or European AQI) and pollen.
- **Severe weather alerts** (US, from the National Weather Service), with notifications.
- **Multiple places.** Your location plus any cities you add; swipe between them.
- **Your units.** °C/°F, km/h, mph, m/s, knots, mm/in, hPa/inHg/mmHg, km/mi and 12/24-hour time.
  Defaults follow your region.
- **In-app updates.** Nimbus checks this repository's GitHub Releases. When a new build is published
  you get a notification, and one tap downloads, verifies and installs it.
- **Private.** No accounts, no ads, no tracking. Your location is only sent to the weather services
  below to get the forecast.

## Install

1. Open the [latest release](https://github.com/duddmeister/Weather-app/releases/latest) on your
   phone and download `Nimbus-v….apk`.
2. Open the file. If Android asks, allow your browser to install apps.
3. Open Nimbus and tap **Use my location** (or search for a city).
4. Add a widget: long-press your home screen → **Widgets** → **Nimbus**.

Later updates arrive in the app: you'll get a notification, or see an **Update available** pill.
The first in-app update asks you to allow Nimbus to install apps.

## How releases work

Every push to `main` runs [`.github/workflows/release.yml`](.github/workflows/release.yml). It
builds a signed APK, runs the unit tests and publishes a GitHub Release named `v1.0.<build>` with
the APK attached and the commit messages as release notes. Pull requests run
[`ci.yml`](.github/workflows/ci.yml). It builds, tests, lints, runs the app on an emulator and
uploads screenshots.

Releases are signed with the key in [`signing/`](signing/README.md). To use a private key instead,
see that page.

## Building locally

Requires JDK 17+ and the Android SDK (API 36).

```sh
./gradlew assembleDebug          # debug build (installs alongside the release app)
./gradlew testDebugUnitTest      # unit tests
LIVE_API_TEST=1 ./gradlew testDebugUnitTest   # also check the real weather APIs
```

## Data sources

- Forecasts, air quality and place search: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0), with
  air quality from Copernicus CAMS.
- Severe weather alerts: [National Weather Service](https://www.weather.gov/) (US only).
- Reverse geocoding fallback (when the phone's own geocoder isn't available):
  [BigDataCloud](https://www.bigdatacloud.com/).
