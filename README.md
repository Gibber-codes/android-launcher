# Gibber Launcher

Your own Android home screen, with Gibber Hub built in.

- **Home:** the clock, then your favorite apps as a big list. Hold one to move it up or down, or take it off.
- **All apps:** every app A to Z, with search and an alphabet strip down the right side. Hold an app to add it to Home.
- **Gibber Hub:** swipe left from Home (or tap "Gibber Hub"). The first time, type the hub's address. Back steps back inside the hub, then returns Home.
- Pressing your phone's Home button always brings you back to the favorites.

Built with Kotlin and Jetpack Compose. Android 8 or newer.

## Get it on your phone

Every push to main builds a new version and posts it on this repo's **Releases** page as `gibber-launcher.apk`.

**First install:** on your phone, open the newest release, tap `gibber-launcher.apk`, and install it. Then press Home and pick **Gibber Launcher** (Settings > Apps > Choose default apps > Home app on Samsung).

**Updates after that:** tap **Update** at the bottom of the home screen. It reads "Update ready" when a newer build is out. The first time, Android asks you to let Gibber Launcher install apps; turn that on, come back and tap Update again.

Every build is signed with the same key (`signing/launcher.p12`, locked with the repo secret `LAUNCHER_KEY_PASSWORD`), so a new version installs right over the old one and keeps your favorites. Without that secret the build still runs but isn't posted as a release.

To go back to Samsung's home screen at any time, pick One UI Home in that same setting.

## Reaching Gibber Hub from the phone

The hub runs on your PC (port 5175). For the phone to load it:
- **Same Wi-Fi:** use the PC's local address, like `http://192.168.1.20:5175`. The hub's server has to listen on your network, not only on localhost, and Windows Firewall has to allow the port.
- **From anywhere:** a private network like Tailscale, or putting the hub online, is a later step.
