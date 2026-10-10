# Raf's Garage

Personal Android app (Galaxy S23 Ultra) for a Chamberlain myQ garage door: live open/closed status,
manual open/close, and (coming) location-based auto-open and auto-close.

Not affiliated with Chamberlain Group. myQ has no public API; this uses the same unofficial cloud API
as the community Home Assistant integration, so a myQ update can break it at any time.

## Build and install

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest      # unit tests
./gradlew installDebug           # phone connected with USB debugging enabled
```

## Google Maps key

The garage-location map needs a Maps SDK for Android key in a gitignored `.env` file:

```sh
echo 'MAPS_API_KEY=your-key-here' > .env
```

Create it in Google Cloud Console (APIs & Services): enable **Maps SDK for Android**, create an
API key, and restrict it to Android apps with package `com.raf.garagedoor` plus the SHA-1 of the
signing keystore (`keytool -list -v -keystore ~/.android/debug.keystore -storepass android` for
debug builds). Without a key the app still builds; the map just stays blank.

## Signing in

The app opens myQ's own sign-in page in a Chrome tab (Google sign-in refuses embedded web views).
When myQ finishes, it redirects to `com.myqops://android?code=...`, which this app receives and
exchanges for tokens. If the official myQ app is installed, Android may ask which app should open
that link: choose **Raf's Garage**. Your password is only ever typed into myQ's page.

Tokens are stored encrypted with an Android Keystore key and refreshed automatically.

## When myQ changes something

All values myQ might change (OAuth client, app version, user agent, App Check profile, API hosts)
live in `MyQApiConfig`. Compare them with the latest
[bvdcode/myq-home-assistant](https://github.com/bvdcode/myq-home-assistant) `const.py` first.

Door control sits behind `GarageDoorController`, so a local controller (for example ratgdo) can
replace the myQ cloud without touching the UI.

## Roadmap

1. Manual control: sign-in, door status, open/close ✅
2. Geofence editor: map pin plus adjustable open/close radius in feet ✅
3. Auto-open on arrival and auto-close on departure, with safeguards and an activity log
4. Home-screen widget and Quick Settings tile
