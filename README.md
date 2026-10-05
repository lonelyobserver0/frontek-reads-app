# Frontek Reads — Android

Native Android version of [feeds.frontek.dev](https://feeds.frontek.dev) (source in
`~/Code/frontek.dev/feeds.frontek.dev`): a private, no-account RSS/Atom reader.
Same features, same catalog, same four languages (EN/IT/ES/FR), same palette.

Kotlin + Jetpack Compose (Material 3), single activity. No backend, no tracking:
subscriptions, favorites, read state and a 15-min article cache live in the app's
private storage.

## Differences from the web app

- **No CORS proxy setting.** A native app fetches feeds directly. Only when a direct
  request fails (anti-bot 403, TLS fingerprinting) does it retry through
  `https://feeds.frontek.dev/proxy`.
- **Reader** renders sanitized HTML in a WebView with JavaScript disabled; links open
  in Custom Tabs.
- **Share to subscribe:** share any page URL to the app → it opens Discover with the
  URL ready for "Find & subscribe".
- **Deep links:** `https://feeds.frontek.dev/?read=<url>&t=<title>&s=<source>` opens
  the article in the reader (unverified link: Android asks which app to use).
- Language uses the per-app locale API (also in system settings on Android 13+).

## Layout

```
app/src/main/java/dev/frontek/reads/
├── data/      Models, Store (JSON files + prefs)
├── feed/      Http, FeedParser, Html (sanitize/clean/extract), Discovery, Opml
└── ui/        AppViewModel, App (shell), screens/, components/, theme/
app/src/main/assets/catalog.json   # copied from the web app
```

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest   # live tests, need network
```

compileSdk 37 · targetSdk 36 · minSdk 26 · AGP 9.4 · Kotlin 2.4 · Gradle 9.8.
