# Hands-free field test log

A record of each real-world test: what Diagnostics and the Activity log showed, the root cause
found, and what was changed. **Read this before changing hands-free behavior** so the same ideas
are not tried twice. Newest test first. Times are local (EDT). Never paste garage coordinates here;
distances only.

## How to pull the logs from the phone

With the phone on USB (debug build), from the repo root:

```sh
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB exec-out run-as com.raf.garagedoor cat files/datastore/automation_diagnostics.preferences_pb > diagnostics.pb
$ADB exec-out run-as com.raf.garagedoor cat files/datastore/automation_activity_log.preferences_pb > activity.pb
```

Each file holds one JSON array (newest first) inside a small protobuf wrapper; `strings` or a
regex for `\[.*\]` extracts it. Other useful stores in the same folder: `automation_presence`
(current HOME/AWAY zone, armed flag, registered fingerprint) and `automation_settings`.

Android's own record (kept for a few hours) shows when anything used GPS for this app, including
Google Play services evaluating its geofences (lines with `WorkSource{... com.raf.garagedoor}`):

```sh
$ADB shell dumpsys location > location.txt          # GPS registrations, with times
$ADB logcat -d -v time | grep -i garagedoor         # broadcasts and service starts
```

Quick checks that the phone allows background wake-ups:

```sh
$ADB shell dumpsys deviceidle whitelist | grep garage     # battery Unrestricted
$ADB shell am get-standby-bucket com.raf.garagedoor       # 5 = exempt
$ADB shell cmd appops get com.raf.garagedoor | grep -E "LOCATION|RUN_ANY|ACTIVITY"
```

Is Power saving on? It changes GPS and background behavior (see test 4):

```sh
$ADB shell settings get global low_power          # 1 = on
$ADB shell settings get global low_power_sticky   # 1 = turned on by hand, stays on at any charge
$ADB shell dumpsys power | grep location_mode     # 1 = GPS off while the screen is off
$ADB shell dumpsys batterystats --history | grep -m1 power_save   # +power_save = on since unplugged
```

For what Google Play services did with this app's location requests (which source answered:
GPS, WIFI or CELL, and when Android held them back as "stationary"):

```sh
$ADB shell dumpsys activity service com.google.android.gms/com.google.android.location.internal.GoogleLocationManagerService > fused.txt
```

---

## Test 4: bike ride, 2026-10-10 about 18:17 to 23:09

**Settings:** open within 135 ft, close beyond 200 ft, home fence 492 ft, wake-up zone 3,281 ft,
left-open *Remind me* after 5 min. **Phone Power saving mode was on** the whole time (turned on
by hand at some point; it stays on at any battery level, and the phone was at 80% or more).

**What the user saw:** leaving, the close failed while the phone was still on weak home Wi-Fi;
it worked after the user turned Wi-Fi off. Coming home at about 23:09 the door opened.

**What Diagnostics, the Activity log and Android's logs showed:**

| Time | Event |
|---|---|
| 17:41 to 18:00 | 7 re-registrations at home, one per new app build installed (widget work); each started a departure check that timed out. Test noise only |
| 18:01:12 | Opened from the widget. The 5-minute left-open check due at about 18:06 **did not run**; it ran at 18:10:07, 5 s after the screen came on (reminder "Open 8 min") |
| 18:17:53 | Opened from the widget, departure check started |
| 18:19:20 | CLOSE decided at 159 ft (±12 ft, screen on) |
| 18:19:32, 18:19:41 | myQ attempts 1 and 2 failed: DNS "Unable to resolve host" (Wi-Fi connected, no internet) |
| 18:19:53 | Attempt 3 worked after the user turned Wi-Fi off. Closed 33 s after the decision |
| 18:19:54 | Away watch started (armed). Cadence stepped 10 s, 30 s, then 2 min at about 7 mi |
| 18:51:32 | Re-registered while away (geofence settings saved, app open), which **reset home/away and the armed flag**. Re-armed at 18:58 from a ±63 ft Wi-Fi fix |
| 18:58 to 23:04 | Away watch ran all evening (2 min, then 5 min while farther), 114 fixes in the session |
| 23:04:47 | Cadence 30 s, then 10 s at 23:07:50, then GPS every 2 s requested at 23:08:38 |
| 23:08:38 to 23:09:29 | **GPS never turned on.** Only Wi-Fi positions arrived, every 5-7 s. The screen was off from 23:05:26 to 23:09:57 |
| 23:09:24 | OPEN decided at **45 ft** (±114 ft); door opened 23:09:28. Should have fired near 135 ft |
| 23:10:03 | GPS fixes started 6 s after the screen came on |

**Root causes:**

1. **Power saving mode turns GPS off while the screen is off** (`location_mode=1`,
   GPS_DISABLED_WHEN_SCREEN_OFF). No app can override it. The app asked for GPS correctly, but
   only Wi-Fi positions arrived, so the open fired late. The same mode also held back the
   left-open check until the screen came on, and restricts background data.
2. **Weak home Wi-Fi at the edge of range**: connected but no internet (DNS failed). The retry
   schedule (3 s, 7 s, 15 s, 30 s) eventually worked, but only because the user turned Wi-Fi off.
3. **Saving geofence settings while away resets home/away**, which also clears the armed flag.
   It re-armed here only because a good Wi-Fi fix came along; with poor fixes the arrival would
   not have opened.

**Fixes:**

- The user turned Power saving off. Not something the app can override; a warning in the app is
  still an open idea.
- **myQ over mobile data first** (`network/MobileDataFirstRoute`): every myQ request is sent over
  mobile data even while Wi-Fi is connected, falling back to the default network. Never while
  roaming (checked before asking, and the network is checked again once it is up). A setting
  turns it off. Diagnostics logs "myQ requests now go over ..." whenever the route changes.
  Lesson: Android refuses `requestNetwork` with NET_CAPABILITY_NOT_ROAMING
  (IllegalArgumentException, which crashed the first build); roaming has to be checked on the
  network's capabilities after it comes up.
- Radius changes no longer reset home/away or the armed flag; only moving the garage pin (or
  turning hands-free off and on) does. Diagnostics says "home/away kept" or "home/away reset".
- A "Power saving is on" card on the Hands-free screen, and a note on every tracking session in
  Diagnostics.

**Battery:** Samsung showed the app at 3.9% of battery *use* since the last charge. Android's own
estimate was about 21 mAh over 5 hours (about 0.4% of a 5,000 mAh battery): wake locks while
watching (7 mAh), GPS (4.6 mAh, mostly departure checks at home during widget testing), the
app's own screen time (7 mAh) and CPU (2.3 mAh).

---

## Test 3: bike ride, 2026-10-10 about 13:04 to 16:57

**Settings:** open within 125 ft, close beyond 145 ft, home fence 492 ft, wake-up zone 3,281 ft,
open countdown 3 s, close countdown 5 s, left-open *Remind me* after 10 min.

**What the user saw:** a few short walk-out tests around 13:05 to 13:10, then opened and closed the
door from the app around 13:30 and rode off. Came back around 16:28: no auto-open, no sign the app
noticed. Walked in, opened the garage with the wall button, turned the phone's screen on around
16:30. Then left the door open for over 10 minutes and got no reminder.

**What Diagnostics and the Activity log showed:**

| Time | Event |
|---|---|
| 13:05:20 | Departure CLOSE at 499 ft, but myQ failed 3 times in 47 s (timeout, then DNS "Unable to resolve host", then timeout): phone clinging to weak home Wi-Fi. FAILED |
| 13:07:02 | Return detected (OPEN decision, door already open) |
| 13:09:01 | CLOSED at 550 ft (worked) |
| 13:09:55 | Return detected, but **SKIPPED_COOLDOWN**: the 90 s cooldown after the close blocked a genuine open |
| 13:28 to 13:35 | Door opened from app, departure seen, CLOSE decided (already closed by the user) |
| 13:35:15 | GPS jitter (129 ft, ±72 ft) briefly set HOME; corrected 26 s later at 514 ft (armed) |
| 13:41:33 | NEARBY session ended LEFT_AREA (beyond 1.25 x wake-up zone). From here only geofences were watching |
| 14:24 to 14:27 | Rode along the edge of the wake-up zone; ENTER/EXIT fired 4 times. ARRIVAL session got only 12 fixes in 3.5 minutes (best ±100 ft), probably because the phone was in a bag |
| 16:28 | **Arrived home: nothing fired** |
| 16:30:31 | Screen turned on: wake-up and home ENTER fired together at 27 ft. OPEN decision, door already open |
| 16:40:34 | Left-open check first saw the door open (12 minutes after it opened) |
| 16:55:34 | Reminder posted ("Open 15 min"); the door had been open about 25 minutes |

**Evidence from Android's own logs** (`adb shell dumpsys location`, `adb logcat`):

- Geofence evaluation (Google Play services GPS requests on this app's behalf) ran only at
  **15:00, 15:41 and 16:10** between 14:27 and the screen turning on at 16:30, roughly every
  30-40 minutes. A geofence cannot catch an arrival on this phone with the screen off.
- Background-started sessions log "Foreground service started from background can not have
  location access". This is **not** the blocker: the GPS registration for the 14:24 session is
  shown as permitted and active from 14:24:01 to 14:27:38, because "Allow all the time" is granted.
- Phone settings were fine: battery Unrestricted, standby bucket exempt, all permissions granted.
- myQ's `last_update` time matches the real open time (the app showed "Open · 23 min ago" at
  16:53), so it can be trusted as "open since".

**Root causes:**

1. After leaving the area, arrival detection depended entirely on geofences, which were checked
   only every 30-40 minutes.
2. The left-open monitor counted from when its 15-minute periodic check first saw the door open,
   then waited for another periodic run: up to about 30 minutes late in total.
3. The 90 s cooldown also blocked opening on a quick return.
4. 3 myQ attempts over about 47 s were not enough at the Wi-Fi edge.

**Fixes:**

- NEARBY and the 15-minute ARRIVAL session are replaced by one **away watch** (ARRIVAL purpose)
  that runs from departure until home, up to 12 hours, with tiered precision: Wi-Fi/cell every
  3 minutes beyond 15 km, every minute within 15 km, every 10 s within 2 km, and GPS every 2 s
  within 300 m. The service starts it as a follow-up to a departure, so the notification switches
  to "Away: watching for your return". It resumes after a reboot or app update while AWAY.
- The left-open monitor starts its timer at myQ's open time, and queues a one-off check for the
  exact deadline (and for the 30-minute repeat). Opening from the app or by hands-free also
  queues a check at the limit.
- The cooldown now blocks only closing; opening still needs a genuine trip away (arming).
- Automatic commands retry 5 times over about a minute (3, 7, 15 and 30 s waits).

**Follow-up the same evening (before any new test):**

- Fixed check intervals replaced by `ArrivalCadence`: the next check comes after half the time to
  get home at 60 mph (20 mi: 10 min, 40 mi: 20 min, capped at 1 hour; steps from 10 s to 60 min).
  It uses the nearest point the fix's accuracy allows, so a vague fix never delays a check. GPS
  every 2 s within 300 m. The away watch's 12-hour limit became a 30-day safety net.
- New engine rule: no auto-open unless the previous reading is at most 5 minutes old. Otherwise
  the user is marked HOME without opening ("not opened: previous reading too old" in Diagnostics).
  This would have applied at 16:30 in this test (previous reading at 14:27).
- **Clear logs** button (Activity log and Diagnostics, with confirmation) empties both logs and
  records when the build was installed.
- Left-open polling: myQ is now asked as often as the limit (at least every 15 minutes) through a
  chain of one-off WorkManager checks, with the 15-minute periodic job as a backstop. A check
  always falls inside the limit, so wall-button opens are reminded on time. The Home Assistant
  integration polls every 30 s, so 5-minute polling is far below any rate limit. The settings
  screen's "up to 15 minutes later" warning was replaced.

**Not yet verified:** a full ride with the new away watch; the exact-deadline reminder.

---

## Test 2: bike ride, 2026-10-10 about 12:28 to 12:34

**Settings:** open within 125 ft, close beyond 145 ft (changed from 80 ft at 12:28), wake-up zone
3,281 ft. Phone checked afterwards: Unrestricted battery, standby bucket exempt, background
location, activity recognition and notifications all granted.

**What the user saw:** riding away, nothing happened until the app was opened on the Hands-free
settings screen; the door then closed. Coming back, the door never opened, not even when standing
next to it with the app open.

**What Diagnostics showed:**

| Time | Event |
|---|---|
| 12:28:05 | Re-registered (home zone changed to 145 ft); home/away reset, then HOME |
| 12:28:07 | Door opened from app, so a DEPARTURE session should have kept watching for 5 minutes |
| 12:28:09 | The initial geofence ENTER caused by re-registering started an ARRIVAL session. The service **cancelled the running DEPARTURE session** to start it; ARRIVAL ended at once (already HOME). No session left running. |
| 12:31:34 | Home geofence EXIT only fired at 506 ft, when the app was opened. Zone HOME to AWAY, armed, decision CLOSE |
| 12:31:39 | Door CLOSED at 506 ft. NEARBY session started (correct) |
| 12:33:12 | NEARBY session saw the arrival and saved HOME plus decision OPEN. At the same moment the home geofence ENTER started an ARRIVAL session, which **cancelled NEARBY mid-command**. No OPEN, no failure, nothing in the Activity log. ARRIVAL then found HOME already and ended. |
| 12:34:07 | Geofence settings saved again, so home/away reset to UNKNOWN, then HOME without arming. Opening the app next to the door could not count as an arrival. |

**Root causes:**

1. `PresenceTrackingService` replaced the running session whenever a trigger with a different
   purpose arrived. Cancelling it killed both the departure watch (12:28) and an in-flight open
   (12:33).
2. The home geofence used the close radius (145 ft / 44 m). Android geofences under about
   100 to 150 m fire late or only when another app requests location.

**Fixes:**

- A running session is never replaced; the extra trigger is logged as
  "X request ignored: Y session already running".
- Fix processing in `PresenceTracker` (save state, then run the door command) is
  `NonCancellable`, so ending a session or leaving the screen cannot drop a committed decision.
- The system home fence is at least 150 m (492 ft). It only wakes the app; open and close still
  use the user's exact radii.

**Verified so far:** unit tests pass. After install, Diagnostics showed the 12:28 sequence again,
now handled correctly ("ARRIVAL request ignored: DEPARTURE session already running").
**Not yet verified:** a full ride with the app closed and the screen off.

**Lesson:** saving the garage location or radii resets home/away. Do not save just before a test.

---

## Test 1: bike ride, 2026-10-10 about 11:50 to 11:56

**Activity log:**

| Time | Event |
|---|---|
| 11:51:58 | OPENED at 59 ft |
| 11:53:54 | FAILED at 507 ft: "Could not reach myQ: timeout" (Wi-Fi to cellular handoff) |
| 11:55:59 | OPENED at 17 ft |

**Findings:** a single myQ call fails during the Wi-Fi to cellular handoff in the driveway, and
arrival came late on a short trip that never left the wake-up zone.

**Fixes:** myQ commands retry up to 3 times over about 10 seconds (`TransientRetry`), with an
**Open now / Close now** notification if all attempts fail. A NEARBY session was added after
departure so short trips inside the wake-up zone are watched on the way back.

---

## Observations outside the ride tests

- **2026-10-10, widget close:** while the widget showed "Closing…", the app showed "Open · just
  now" until the door was fully closed. The app was only trusting myQ, and this door's myQ status
  (virtual opener with a separate door sensor) appears to go straight from "open" to "closed"
  without reporting "closing". Fix: the movement overlay is now a shared
  `DoorStatusSnapshot` used by both the widget and the app screen, so any accepted command shows
  as moving in both until myQ reports the new state (or 90 s pass).
- **2026-10-10, widget second tap ignored:** Android delivers a widget's taps one at a time, so a
  tap callback must never wait; the 4 s "Tap again" expiry runs in the application scope.

- **2026-10-11, voice recognizer (Vosk) does not hold to whole phrases**: with the grammar
  limited to the open phrases plus "[unk]", synthetic "Open garage door" came out as "[unk] open
  garage door" and "Is the door open" as "[unk] the door open". Matching therefore looks for an
  open phrase as consecutive words and scores only those words. Checked on the phone with six
  `say` samples (`VoskModelOnDeviceTest`): both open phrases open; "Close the garage door", "Is
  the door open", "Can you open the window please" and "There is a garage sale down the street" do
  not. Close was added the same day: "Close garage door" and "Close the garage door" close, and
  "Is the garage door closed" (heard as "[unk] the garage door close") does nothing. Real voices
  outdoors are untested.

## Open risks and ideas not yet tried

- **Do not rely on geofences for arrival.** Test 3 proved they are checked only every 30-40
  minutes with the screen off. A larger wake-up zone would not help. They are wake-up triggers
  and backups only.
- **Battery cost of the away watch** is untested over a full day. Tune `ArrivalCadence`
  (assumed top speed, share of time to home, step list) before changing anything else.
- **An arrival faster than 60 mph on average** (e.g. a long highway stretch) can shorten the
  warning, but the next check always comes before half the 60 mph time to home, and each
  closer check comes sooner.
- **Hourly checks when far away**: a trip home that takes under an hour after the last far-away
  check (e.g. a flight landing close to home) is seen late and is not auto-opened, by design.
- **Phone in a bag** gave ±100 ft fixes (test 3). Opening accepts fixes up to ±131 ft, but
  arming needs ±82 ft. The departure normally arms before the phone goes in the bag.
- **Departure without opening from the app** (car remote or wall button) relies on activity
  recognition (it did not fire during the 3-minute ride in test 2) and the home fence EXIT, which
  can be up to 30-40 minutes late with the screen off.
- **Failed automatic close is not retried later.** If all 5 myQ attempts fail, only the
  notification's **Close now** button remains. A delayed retry would need to check the user is
  still away before closing.
- **Power saving mode** (test 4) disables GPS with the screen off and defers background checks.
  The app now warns about it, but cannot override it.
- **Weak Wi-Fi at the edge of range** (tests 3 and 4): now sent over mobile data first (see test
  4). Not yet ride-tested. If cell signal is weak at the door, each call waits up to 5 s before
  falling back to Wi-Fi.
- **Voice command** is untested outdoors (wind, a bike, the phone in a pocket). Check
  Diagnostics for its confidence scores before changing `MIN_WORD_CONFIDENCE`. It only listens
  while hands-free produces fresh readings near home, so standing still at home it is off.
- **Lock-screen Open button and voice** open the door for anyone holding the phone.
- **Force stop** (Settings, App info, Force stop) removes every geofence until the app is opened
  again. Swiping the app away from Recents does not.
