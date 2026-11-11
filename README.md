# Iman Sangi - Android Hybrid Project

A native Kotlin shell around the existing Iman Sangi web app. The WebView stays the
interface; Kotlin owns audio, alarms, sensors, blocking and storage.

## What is in this repository

    app/src/main/java/com/islamicapp/noor/      Kotlin sources
    app/src/main/res/                           manifest resources, layouts, icons
    webview-bridge/webview-bridge.js            JavaScript SDK for the web app
    .github/workflows/android-build.yml         GitHub Actions build
    docs/TESTING.md                             feature-by-feature test plan

## Build locally

1. Install Android Studio (Ladybug or newer) with JDK 17.
2. Open this folder as a project. Android Studio generates the Gradle wrapper on
   first sync (the wrapper JAR is intentionally not committed).
3. The live web app URL is already set in app/build.gradle.kts:
   buildConfigField("String", "WEB_APP_URL", "https://islamic-1.base44.app")
4. Run the app target on a device (Android 7.0+).

Command line build without a wrapper:

    gradle :app:assembleDebug

The debug APK lands in app/build/outputs/apk/debug/.

## GitHub Actions

Push the project to a repository. Every push and pull request builds a debug APK
and uploads it as an artifact. Manual runs use workflow_dispatch.

The release key made for this app lives in the admin panel (Android source >
Signing key): download noor-release.p12 and keystore.properties. Both are
git-ignored - keep them out of the repository and never publish them.

For a local signed build put keystore.properties next to settings.gradle.kts:

    storeFile=noor-release.p12
    storeType=PKCS12
    storePassword=...
    keyAlias=noor
    keyPassword=...

GitHub Actions signing secrets (Settings > Secrets and variables > Actions):

    KEYSTORE_BASE64      base64 of noor-release.p12
    KEYSTORE_PASSWORD    keystore password
    KEY_ALIAS            noor
    KEY_PASSWORD         key password

Without those secrets the workflow still builds a debug APK and clearly reports
that the release build was skipped. It never claims a release it did not build.

Create the base64 value with:

    base64 -w 0 noor-release.p12 > keystore.txt

## Web app integration

The web app already ships the bridge client (src/lib/noorBridge.js), so the live
site exposes window.NoorBridge as soon as it loads inside this shell - nothing has
to be copied or injected. webview-bridge/webview-bridge.js is the same API for any
other page that is not part of the app bundle. Use it like this:

    const bridge = window.NoorBridge;
    if (bridge && bridge.isAvailable()) {
      const times = await bridge.prayer.times({ latitude: 23.81, longitude: 90.41 });
      const unsub = bridge.on("listeningTick", (state) => setProgress(state.percent));
    }

Native state events pushed into the page:

    ready, audioState, listeningTick, listeningComplete, blocking,
    focus, compass, compassAccuracy, prayerAlarm, permission,
    protection, download

Listening progress is only produced by the Kotlin player sampling the real
Media3 position each second. The page can read it but can never write it, so a
JavaScript message cannot unlock apps.

## Permissions and what Android allows

| Feature | Permission | Honest limit |
| --- | --- | --- |
| App list and usage | Usage Access (special) | User grants it in Settings |
| Blocking apps | Accessibility service | User can always switch it off; there is an emergency unlock |
| Prayer alarms | Exact alarm | Falls back to inexact delivery if not granted |
| Notifications | POST_NOTIFICATIONS (Android 13+) | No notifications until granted |
| Qibla | Location + compass sensor | Some devices have no reliable compass |
| Protection | VPN consent | Only one VPN at a time; DNS filters cannot block everything |

## Islamic customization studio

The web app carries the studio itself (theme presets, the shared 42 icon monoline sheet, the ayah /
prayer / tasbih widget designer and the procedural wallpaper studio). The native side adds:

    Customization/WallpaperHelper.kt   home and lock wallpaper from a base64 PNG
    Customization/ShortcutHelper.kt    pinned home-screen shortcut with the chosen icon
    Customization/DeviceExtras.kt      vibration feedback and last known location
    Customization/ThemeExchange.kt     export and import theme JSON
    Widgets/*                          three App Widget providers, RemoteViews layouts,
                                       provider XML and a 15 minute rotation alarm

Honest limits: Android never lets an app replace another app's icon in place, so icons are applied as
pinned shortcuts; placing a widget needs launcher support; manufacturer lock screens may keep their own
wallpaper; and a full launcher-level icon pack needs a launcher that supports icon packs.

## Content and licences

Quran text, translations, hadith and duas stay in the existing web app and are
served from the same vetted sources it already uses. No religious text is
generated by AI, and source and translator attribution must stay visible.

## Known gaps before release

- Replace the starter adult-domain list with a maintained hostname feed.
- Provide your own adhan audio files; only the system alarm sound is used until
  then, because bundled adhan recordings need clear redistribution rights.
- Continuous-session mode is implemented, but test it on your target devices.
- Release signing must be configured through GitHub secrets.
