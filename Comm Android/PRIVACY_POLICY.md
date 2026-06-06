# Privacy Policy — Garmin Camera Click Companion

_Last updated: May 14, 2026_

## Overview

Garmin Camera Click Companion ("the app") is a simple utility that lets your
paired Garmin smartwatch trigger your phone's camera shutter. We do not collect,
store, or sell your personal data. Period.

---

## What the App Does

The app listens for a shutter signal from your Garmin watch and clicks the
capture button in your phone's camera app on your behalf. That's it.

---

## Data We Collect

**None of your personal data.**

The app does **NOT** access or collect:

- Your name, email address, or account information
- Your location
- Your contacts or calendar
- Your photos or media library
- Your microphone or audio
- Your call history, SMS messages, or notifications from other apps
- Any other personally identifiable information

---

## What Happens on Your Device (Stays on Your Device)

**Camera accessibility control** — The app uses Android's Accessibility Service
to locate and click the shutter button inside your open camera app. It reads only
the on-screen button positions needed to perform the click. This information is
never stored or transmitted.

**Watch communication** — When your Garmin watch sends a shutter command, the
app receives it over your local Bluetooth or Wi-Fi connection and replies with a
simple "Success" or "Failed" message. No personal data is part of this exchange.

**Photo detection** — For certain camera apps, the app briefly monitors whether a
new photo file was created (so it can confirm the shutter worked). It detects
only the *existence* of a new file — it never reads the photo, its metadata, or
its location.

**Preferences** — Your in-app settings (e.g., preferred button-detection method)
are saved locally on your device using Android's SharedPreferences. They are
never transmitted anywhere.

---

## About the "Send/Receive Data to and from the Internet" Permission

Garmin shows this notice because the app includes the standard `INTERNET`
permission. This is nothing to worry about, and — importantly — **you do not
need an active internet connection for this app to work.** The watch-to-phone
shutter trigger runs entirely over Bluetooth, the same way your watch syncs
steps and notifications. If your phone has no signal and no Wi-Fi, the shutter
still fires.

The `INTERNET` permission is declared for two background reasons unrelated to
the core feature:

**Garmin ConnectIQ communication** — The Garmin ConnectIQ SDK (the library that
lets this app talk to your watch) requires network access to relay messages
between the phone and your Garmin device through Garmin's services. The only
data exchanged is the shutter trigger command from your watch and a "Success" or
"Failed" reply from the app. No personal data of any kind is part of this
exchange.

**Crash reporting** — If the app crashes, a small anonymous report is sent to
Google Firebase Crashlytics. This report contains:

- Your device model and manufacturer (e.g., "Google Pixel 9")
- Android version
- App version number
- The stack trace of the crash (code location, not user data)

It does **not** contain your name, photos, location, or anything personal.

**Basic analytics** — Firebase Analytics receives anonymous signals about which
app features are used (e.g., "shutter triggered successfully"). No personally
identifiable information is included.

The app has no backend server of its own. **Your personal data is never
collected or uploaded anywhere.**

If you prefer to opt out of Firebase analytics entirely, you can do so in your
Android device settings under **Privacy → Ads → Delete advertising ID** or by
using a network-level blocker.

---

## Third-Party Services

| Service | Purpose | Data Shared |
|---|---|---|
| Garmin ConnectIQ SDK | Watch communication via Garmin's relay | Shutter trigger command + "Success"/"Failed" reply — no PII |
| Google Firebase Crashlytics | Crash reporting | Device model, OS version, app version, crash trace — no PII |
| Google Firebase Analytics | Anonymous usage statistics | Anonymous event counts — no PII |

No other third-party services, SDKs, or APIs are used.

---

## Children's Privacy

The app does not collect any data from anyone, including children under 13.

---

## Changes to This Policy

If we ever change what data the app collects (we don't plan to), we will update
this document and the "Last updated" date above.

---

## Contact

Questions? Reach out at csdev971@gmail.com.
