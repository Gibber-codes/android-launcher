# Gibber Launcher

Your own Android home screen, with Gibber Hub built in.

- **Home:** the clock, then your favorite apps as a big list. Hold one to move it up or down, or take it off.
- **All apps:** every app A to Z, with search and an alphabet strip down the right side. Hold an app to add it to Home.
- **Gibber Hub:** swipe left from Home (or tap "Gibber Hub"). The first time, type the hub's address. Back steps back inside the hub, then returns Home.
- Pressing your phone's Home button always brings you back to the favorites.

Built with Kotlin and Jetpack Compose. Android 8 or newer.

## Get it on your phone

**Option A, Android Studio on your PC**
1. Open this folder in Android Studio and let it sync.
2. Plug in your phone with USB debugging on, then press Run. Or use Build > Build APK(s) and copy `app/build/outputs/apk/debug/app-debug.apk` to your phone.

**Option B, GitHub builds it for you**
Push this folder to a GitHub repo. The "Build APK" action builds the app on every push; download `gibber-launcher-apk` from the run's page and open it on your phone.

Then on the phone: allow installing from that source, install, press Home, and pick **Gibber Launcher** as your home app (Settings > Apps > Default apps > Home app on Samsung).

To go back to Samsung's home screen at any time, pick One UI Home in that same setting.

## Reaching Gibber Hub from the phone

The hub runs on your PC (port 5175). For the phone to load it:
- **Same Wi-Fi:** use the PC's local address, like `http://192.168.1.20:5175`. The hub's server has to listen on your network, not only on localhost, and Windows Firewall has to allow the port.
- **From anywhere:** a private network like Tailscale, or putting the hub online, is a later step.
