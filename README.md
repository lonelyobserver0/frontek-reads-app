# Frontek Reads — Android

Native Android version of [feeds.frontek.dev](https://feeds.frontek.dev) (source in
`~/Code/frontek.dev/feeds.frontek.dev`): a private, no-account RSS/Atom reader.
Same features, same catalog, same four languages (EN/IT/ES/FR), same palette.

Kotlin + Jetpack Compose (Material 3), single activity. No backend, no tracking:
subscriptions, favorites, read state and a 15-min article cache live in the app's
private storage.

## Support

Frontek Reads is free, has no ads and collects no data. If you find it useful, you can
[buy me a coffee on Ko-fi](https://ko-fi.com/lonelyobserver0). Donations are entirely
optional: they don't unlock anything, and the app is the same for everyone.

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

### Release

Release signing reads `keystore.properties` in the project root (git-ignored):

```properties
storeFile=/path/to/frontek-reads.jks
storePassword=…
keyAlias=frontek-reads
keyPassword=…
```

Without it the release APK is built unsigned. To produce the file to publish:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew dist   # → dist/frontek-reads-<version>.apk
```

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every release.
Updates must be signed with the same key, or Android refuses to install them over
the previous version.

compileSdk 37 · targetSdk 36 · minSdk 26 · AGP 9.4 · Kotlin 2.4 · Gradle 9.8.

## License

Copyright © 2026 lonelyobserver0

Frontek Reads is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software Foundation,
either version 3 of the License, or (at your option) any later version.

It is distributed in the hope that it will be useful, but **without any warranty**;
without even the implied warranty of merchantability or fitness for a particular
purpose. See [LICENSE](LICENSE) for the full text.
