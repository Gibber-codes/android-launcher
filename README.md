# Gibber Launcher

Your own Android home screen, with Gibber Hub built in, in the approved dark design: a night
background with slowly drifting gradient circles behind frosted glass.

- **Tabs along the top:** Search, Home, Dev and Mechanic. Swipe left and right between them, or tap one. No clock and no bar at the bottom.
- **Home:** what's playing (tap it for the music screen), a month calendar that fills the page, and eight apps.
- **Search:** the apps you use most, then your games. **All ›** opens every app.
- **Dev:** Talk to Claude (hold it to change the chat), the Ideas widget, your games and dev tools. The gear opens Gibber Settings.
- **Mechanic:** Jobs, Parts and Mechanic tools.
- **All apps:** swipe up anywhere, swipe down to close. Every app A to Z, with search and the alphabet down the right side. Back closes it too.
- **Music screen:** the song, its artist and cover (from Spotify or any player, once notification access is on) and its controls, an Open Spotify button, your speaker and headphones, and a bass boost (a switch and a strength). The boost is made on the phone, so it reaches any speaker, linked JBL speakers too; the curve is kept centred so volume stays even.
- **Your speaker and headphones:** under Playing on, a tile for the JBL Xtreme 5 and one for the JBL Endurance, each saying Connected (with a green dot) or Not connected (`Speakers.kt`). The Xtreme 5 is known by "Gabes JBL" or "Xtreme", the Endurance by "Endurance", matched against the phone's sound outputs, and by a paired device's address once Nearby devices is allowed. A tap opens JBL One or JBL Headphones. The square Bluetooth button beside them opens a Bluetooth pop-up over the screen: Samsung's (paired devices and a scan) on a Galaxy, else Android's pop-up for picking where sound plays (Android 11 and later), else Bluetooth settings, or turns Bluetooth on if it's off.
- **Hold an app:** Move to a section, Merge, Remove from this card, Uninstall. **Hold a section's name:** edit mode, where you tick apps and remove or merge them at once.
- **Tap a game:** its pop-up, with Play, the dev server switch, Dev build, Style, Talk and older versions.
- **Gibber Hub, Gibber Settings and Ideas** are apps in the list and open full screen.
- Pressing your phone's Home button in another app brings the launcher back on the page you left (music, the app list, Gibber Hub, Dev, a game). Pressing it again on the launcher goes to Home.

Built with Kotlin and Jetpack Compose. Android 8 or newer.

### Things to allow once

- **Notification access** (the music screen offers it): shows what's playing and the number badges on Home's apps. On Android 13 and later a sideloaded app's switch is greyed out ("Restricted setting"): the music screen then says to open App info, tap ⋮, choose Allow restricted settings, and try again.
- **Calendar** (tap "Show your events" under the month): shows your events.

## Get it on your phone

Every push to `main` builds a new version and posts it on this repo's **Releases** page as
`gibber-launcher.apk`. A job's branch builds too, only to check it compiles; it reaches the phone once
it's merged into `main`. A commit on `main` whose message holds `[no release]` builds without posting.

**First install:** on your phone, open the newest release, tap `gibber-launcher.apk`, and install it. Then press Home and pick **Gibber Launcher** (Settings > Apps > Choose default apps > Home app on Samsung).

**Updates after that:** a new build posts a "Gibber Launcher update ready" notification (tap it to install), downloads by itself and tries to install when you leave the launcher; or open Gibber Settings and tap **Check** on the Launcher row. The launcher looks at most every half hour, when you return to Home. If GitHub's hourly limit for the API is used up, it reads the latest release's web page instead. The first time, Android asks you to let Gibber Launcher install apps; turn that on, come back and tap Check again.

Every build is signed with the same key (`signing/launcher.p12`, locked with the repo secret `LAUNCHER_KEY_PASSWORD`), so a new version installs right over the old one and keeps your favorites. Without that secret the build still runs but isn't posted as a release.

To go back to Samsung's home screen at any time, pick One UI Home in that same setting.

## Reaching Gibber Hub from the phone

The hub runs on your PC (port 5175). The launcher looks for it at `http://100.112.100.42:5175`, your PC's Tailscale address, which stays the same on any network, until you set another address in Gibber Settings. Turn Tailscale on on the phone. A hub address saved with the PC's old Wi-Fi address (192.168.1.216) is replaced by the Tailscale one.
- **Same Wi-Fi:** the hub's server has to listen on your network, not only on localhost (`HUB_HOST=lan`), and Windows Firewall has to allow the port.
- **From anywhere:** Tailscale is on both the PC and the phone, so the hub is reachable on mobile data too.

**Ideas** reads `GET /api/ideas` from the hub (`{ "ideas": [{ "key", "title", "kind" }] }`, newest first). Until the hub sends it, the widget says the ideas weren't read.
