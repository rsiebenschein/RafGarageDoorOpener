# Raf's Garage

Personal Android app (built for a Galaxy S23 Ultra) for a Chamberlain myQ garage door:

- Live open/closed status and one-tap open/close
- **Home-screen widget** (1x1 up to a 4x1 strip) with status and a tap-twice open/close button
- **Hands-free**: opens the door as you arrive and closes it after you leave
- **Left-open monitor**: reminds you, or closes the door, when it has been open too long
- **Voice**: say "Open the garage" or "Close the garage" with the phone locked, only within
  500 ft of the garage
- **Lock-screen Open button** on the away notification

Not affiliated with Chamberlain Group. myQ has no public API. This app uses the same unofficial
cloud API as the community Home Assistant integration, so a myQ update can break it at any time
(see [When myQ changes something](#when-myq-changes-something)).

---

## How hands-free works

Precise GPS drains a battery fast, so the app only runs it close to home. There are two kinds of
session:

- **Departure check** (up to 5 minutes, GPS): is the user leaving? Closes the door once they have.
- **Away watch** (from departure until home): watches for the return with low-power Wi-Fi/cell
  positioning (no GPS). The next check comes after about half the time you would need to get home
  at 60 mph: every 10 minutes at 20 mi, every 20 minutes at 40 mi, at most hourly however far away
  (hourly still catches the drive home after a flight). It checks more often as you get closer and
  switches to GPS every 2 seconds within ~1,000 ft. It opens the door on arrival and ends.

Field testing showed that Android's geofences cannot be relied on to notice an arrival: with the
screen off, this phone checked them only every 30-40 minutes (see
[the field test log](docs/FIELD_TEST_LOG.md)). So the away watch, not a geofence, is what catches
the return. Geofences and activity detection remain as wake-up triggers and backups:

| Trigger (low power, app closed) | What the app then does |
|---|---|
| You **open the door from the app** while home | Departure check |
| Android notices you **start walking, cycling or driving** while home | Departure check |
| You **leave the home fence** (your close radius, but at least ~490 ft) or the wake-up zone (about 1 km / 0.6 mi) | Departure check (catches departures missed above) |
| You **enter** either zone while no session is running | Away watch |
| Phone **restarts** or the app is **updated** while you are away | Away watch resumes |

While a session runs, Android requires a visible notification ("Away: watching for your return"
or "Checking whether you're leaving"). Only one session runs at a time: a trigger that arrives
while one is running is ignored (and noted in Diagnostics), so it can never interrupt a door
command that is already under way.

### Opening and closing rules

The app keeps track of whether you are **Home** or **Away**:

- **Arriving opens the door** when you cross the *Open within* radius, but only if you were
  genuinely away first: an accurate reading (±80 ft or better) at least ~165 ft beyond the
  *Close beyond* radius. GPS drift while you are inside the house can therefore never open
  the door, and neither can the first reading after hands-free is switched on.
- The approach must also have been **watched**: if the previous reading is more than 5 minutes
  old (for example, the first check after an hour-long gap finds you already home), you are
  marked Home without opening, since you may have been inside for a while.
- **Leaving closes the door** once you are confidently past the *Close beyond* radius: two
  readings in a row, or one when you are moving at cycling or driving speed.
- An action is skipped if the door is already in that state, or (optionally) if your phone is not
  connected to your car. Closing is also skipped within 90 seconds of another automatic action, so
  GPS jitter in the driveway cannot close the door that just opened for you.
- Automatic myQ commands are retried up to 5 times over about a minute, so a weak Wi-Fi signal or
  a Wi-Fi to cellular handoff at the edge of the property does not lose the command. If all
  attempts fail, a notification offers **Open now** / **Close now**.
- **Use mobile data for the door** (on by default, in Hands-free settings): door commands, and
  the status read just before each, go over mobile data even while Wi-Fi is connected, with Wi-Fi
  as the backup, so weak home Wi-Fi that is connected but has no internet cannot block a command.
  Routine status checks (the left-open check every few minutes, the widget refresh, the app
  screen) go the other way round, Wi-Fi first, so mobile data is not woken every few minutes at
  home (field test 5). Never used while roaming; a request is only a few KB. Diagnostics records
  which network door commands use and why.
- With a countdown set, a notification lets you cancel. For opening, the countdown starts early
  based on your speed, so the door still begins moving at your radius.

### Home-screen widget

Long-press the home screen → **Widgets** → **Raf's Garage** → drag **Garage door** onto a screen.
It starts at 1x1; long-press it and drag the handles to widen it up to a 4x1 strip.

- **1x1**: garage icon colored by state (green closed, amber open, blue moving) and one word.
  Tapping anywhere toggles the door.
- **2x1 and wider**: also shows when the state last changed ("Open since 5:32 PM") and a separate
  Open/Close button; tapping the rest opens the app.
- **Tap twice**: the first tap shows "Tap again" for 4 seconds; the second sends the command, so
  a stray touch cannot move the door. No unlock or app launch needed.
- Opening from the widget starts the departure check and left-open timer, like the app button.
- **Freshness**: the widget is fed by every myQ read (every command, the left-open check every few
  minutes, the app screen) and redraws only when the door's state or last-change time actually
  changed (field test 5: redrawing on every read was the app's largest battery cost). It is
  followed every 3 seconds while the door moves after a command, and Android refreshes it every
  30 minutes. A wall-button change can take a few minutes to show.

### Lock-screen Open button

While the away watch runs ("Away: watching for your return"), its notification has an **Open
garage** button. It shows on the lock screen, so one tap opens the door without unlocking.
Anyone holding your phone can use it, the same as the voice command.

### Voice: "Open / Close the garage" (optional)

Turn on *Voice: "Open / Close the garage"* in Hands-free settings. When it asks for the
microphone, choose **While using the app** (Android has no "always" for the microphone; the
listener is started while the app is open, and that is enough for it to keep listening with the
phone locked). Avoid "Only this time", which Android takes back soon after you leave the app.

Saying "Open the garage" or "Close the garage" ("the" and "door" are both optional) then opens or
closes the door, with the phone locked and the app closed, but only within 500 ft of the garage:

- **When it listens** (only with hands-free on):
  - **Arriving or leaving**: while a hands-free location reading from the last 2 minutes puts you
    within 500 ft (the away watch uses GPS every 2 s near home; also the departure check and the
    Hands-free screen).
  - **At home**: for **1 minute after you wake the screen or open the app**, if hands-free has you
    at home and one quick Wi-Fi/cell reading puts you within 500 ft. Wake the phone, then speak; it
    keeps listening for the minute even if the lock screen turns itself off.
  - Otherwise, with the phone lying still, it does not listen, so a TV or a conversation indoors
    cannot open the door and the microphone does not run all day.
- **Is it listening?** The ongoing notification's title says **Voice: listening now** or **Voice:
  not listening now**, and Android shows its microphone indicator while it listens.
- **On the phone only**: speech is recognized offline (Vosk, small English model, about 70 MB
  unpacked). No audio is sent anywhere or saved.
- **Feedback**: a short tone, then the usual result notification. Saying the same command again
  within 30 s is ignored; the other command always goes through.
- **After a phone restart or app update**, Android does not let an app reopen the microphone in
  the background. A "Voice command paused" notification appears; tap it (or open the app) once.
- **Tuning**: Diagnostics logs "Voice: listening (within 500 ft: reason)" / "stopped listening",
  every phrase it acts on and near misses, each with a confidence score. A command needs each of
  its words heard with confidence 0.6 or more (`VoicePhrases.MIN_WORD_CONFIDENCE`).

### Car Bluetooth (optional)

With *Only auto-open in my car* on, the app opens the door only while your phone is connected to
the car you picked from your paired Bluetooth devices. It matches the device's hardware address,
so headphones or a watch never count. Closing on departure still works on foot.

### Left-open monitor (independent of hands-free)

*If the door is left open* can be **Off**, **Remind me** or **Close it**, after a chosen number of
minutes. It works with the app closed and hands-free off, and lands on time:

- myQ never notifies this app, so it asks myQ as often as the limit, and at least every 15 minutes
  (5-minute limit: every 5 minutes). That is one small request each time; the Home Assistant
  integration asks every 30 seconds.
- The open time comes from myQ, so it counts from when the door actually opened. Since a check
  always falls inside the limit, the first check that sees the open door queues one for the exact
  deadline. Opening from the app, the widget, a notification, by voice or by hands-free queues it
  straight away.
- Each check is one small request over Wi-Fi (mobile data only if Wi-Fi fails).
- Reminders show **Close** and **Ignore**. Ignore silences reminders until the door next closes.
  Otherwise a reminder repeats every 30 minutes while the door stays open.

---

## Setting up on a phone

### 1. Build machine (once)

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo 'MAPS_API_KEY=your-key-here' > .env    # see "Google Maps key" below
./gradlew testDebugUnitTest                 # unit tests
./gradlew installDebug                      # phone connected with USB debugging on
```

Android Studio's Run button works too and picks up `.env` automatically.

The first build downloads the voice model (41 MB, SHA-256 checked) into `.gradle/vosk/`; later
builds reuse it. The debug APK is about 124 MB because of it.

On-device tests (speech model and phrase recognition on the real phone) must be run like this,
**not** with `connectedAndroidTest`, which uninstalls the app afterwards and wipes its sign-in
and settings:

```sh
./gradlew installDebug installDebugAndroidTest
adb shell am instrument -w com.raf.garagedoor.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall com.raf.garagedoor.test
```

### 2. On the phone

1. **USB debugging** (only needed to install from the Mac): Settings → About phone → Software
   information → tap *Build number* 7 times, then Developer options → USB debugging.
2. Open the app and tap **Sign in with myQ**. myQ's own page opens in a Chrome tab (Google or
   email sign-in). If Android asks which app should open the myQ link, choose **Raf's Garage**.
3. On the **Auto open & close** card, tap **Set up**, place the garage on the map, set the radii
   and tap **Save**. Suggested starting point: *Open within* 80–150 ft, *Close beyond* 150–200 ft.
4. Tap **Hands-free settings** and complete **Phone setup**:
   - *Precise location*
   - *Allow all the time* (in the settings page Android opens, choose "Allow all the time")
   - *Physical activity*
   - *Notifications*
   - *Battery: Unrestricted*, which stops Samsung from putting the app to sleep
   - **Power saving off** (Settings → Battery → Power saving). While it is on, Android turns GPS
     off whenever the screen is off and the door opens late; the app shows a red card if it is on.
5. Turn on **Auto open & close**. Optionally set countdowns, car Bluetooth and the left-open monitor.
6. Optional: turn on **Voice: "Open / Close the garage"** and allow the microphone **While using
   the app**. *Use mobile data for the door* is on by default; leave it on unless you want myQ
   requests to use Wi-Fi only.

### Google Maps key

The map needs a **Maps SDK for Android** key in the gitignored `.env` file. Mobile map loads cost
$0 (unlimited), but Google requires a billing account on the project.

1. Google Cloud Console → APIs & Services → enable **Maps SDK for Android**.
2. Credentials → Create credentials → API key.
3. Restrict it: *Android apps* with package `com.raf.garagedoor` and the SHA-1 of the signing
   key, and *API restrictions* → Maps SDK for Android only. Debug SHA-1:
   `keytool -list -v -keystore ~/.android/debug.keystore -storepass android | grep SHA1`
4. A **new computer** has a different debug keystore, so add its SHA-1 to the same key and
   create `.env` there too.

Without a key the app still builds; the map is just blank.

---

## Troubleshooting

> **Before changing hands-free behavior, read [docs/FIELD_TEST_LOG.md](docs/FIELD_TEST_LOG.md).**
> It records every field test: what Diagnostics showed, the root cause, what was fixed, and the
> open risks, so the same fixes are not tried twice. Add an entry after each test. It also shows
> how to pull the logs off the phone over USB.

- **Before each field test**, tap the trash icon on Activity log or Diagnostics (**Clear logs**).
  It empties both and leaves one entry saying when the current build was installed, so the logs
  show only that attempt. Record the findings in `docs/FIELD_TEST_LOG.md` first.
- **Activity log** (Hands-free settings → Activity log): what the door did, and why it skipped.
- **Diagnostics** (Hands-free settings → Diagnostics): every wake-up, tracking session (number of
  readings, best accuracy, why it ended), zone change, decision and retry. Use **Share** to send it.
  No coordinates are stored, only distances.
- *Nothing happens at all*: check Diagnostics for "Registered wake-up zone". If it is missing,
  re-check every Phone setup item, then toggle hands-free off and on.
- *"Android refused to start tracking"*: battery is not set to Unrestricted, or location is not
  "Allow all the time".
- *Repeated myQ timeouts*: myQ itself may be down or rate limiting (HTTP 429 means wait an hour).
- *"Power saving is on" card on the Hands-free screen*: turn Power saving off. While it is on,
  Android turns GPS off with the screen off and arrivals open late (field test 4). Diagnostics
  also notes it on every tracking session.
- *Which network myQ used*: Diagnostics logs "myQ requests now go over mobile data" or "... over
  Wi-Fi (or the phone's default network): reason" whenever it changes for door commands, and "myQ
  over mobile data failed, trying Wi-Fi" on a failure. Routine status checks are not logged.
- *Voice does nothing*: look at the voice notification's title first. "Not listening now" at home
  means no screen wake in the last minute (wake the screen, then speak). Then check Diagnostics
  for "Voice: listening". If it never appears, you were not within 500 ft (hands-free must be on
  and have you at home), the microphone permission is missing, or the "Voice command paused"
  notification needs a tap. If it shows "heard ... not sure enough to act", the confidence was
  below 0.6.
- *Battery*: Samsung's per-app percentage is the app's share of the battery *used* since the last
  charge, not of the whole battery. For real numbers, `adb shell dumpsys batterystats` (UID
  u0a859; see the field test log). In field test 5, a full day off the charger, the app used 52 mAh
  (about 1% of the battery) while the phone used 2,804 mAh; that day's waste (widget redraws,
  mobile data woken every 5 minutes) is fixed.

## When myQ changes something

All values myQ might change (OAuth client, app version, user agent, App Check profile, API hosts)
live in `app/src/main/java/com/raf/garagedoor/myq/config/MyQApiConfig.kt`. Compare them with the
latest [bvdcode/myq-home-assistant](https://github.com/bvdcode/myq-home-assistant) `const.py` first.

Door control sits behind `GarageDoorController`, so a local controller (for example a ratgdo
board wired to the opener) can replace the myQ cloud without touching the UI or automation.

## Code map

| Package | Responsibility |
|---|---|
| `myq` | Sign-in (PKCE + App Check), token refresh, device API |
| `door` | Backend-agnostic door model, controller interface, retry policy |
| `geofence` | Garage location, radii, distance math |
| `automation` | Home/away engine, tracking service, triggers, executor, logs |
| `automation/leftopen` | Left-open monitor and its background worker |
| `automation/voice` | Voice command: 500 ft gate (hands-free readings or a screen wake at home), phrase matching, offline recognizer, microphone service |
| `location` | Fused location: GPS/low-power streams for sessions, one-shot readings (map pin, voice screen wake) |
| `bluetooth` | Car connection tracking |
| `network` | Door commands over mobile data first and routine checks over Wi-Fi first, each with the other as backup; never roaming |
| `storage` | Encrypted myQ token storage (Android Keystore) |
| `widget` | Home-screen widget (Glance), its status snapshot and tap handling |
| `ui` | Compose screens |

## Roadmap

1. Manual control: sign-in, door status, open/close ✅
2. Geofence editor: map pin plus adjustable open/close radius in feet ✅
3. Hands-free open and close, left-open monitor, diagnostics ✅ (field testing)
4. Home-screen widget ✅ (field testing), Quick Settings tile
5. Voice open/close within 500 ft, lock-screen Open button, myQ over mobile data ✅ (field testing;
   battery waste and voice at home fixed after field test 5)
6. Next candidates: keep the away watch running when one reading right after leaving lands back
   inside the close radius (field test 5, 16:37); end departure checks early when the user stays
   home (saves GPS, risks the close). Ideas, not started: Quick Settings tile; wall-button detection without myQ (ratgdo); Tesla
   (myQ's own Tesla integration, or a browser page with a small server). See the field test log
   for what was looked into.
