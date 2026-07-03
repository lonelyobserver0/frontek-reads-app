# Frontek Reads — Android

A native Android RSS/Atom reader, and the mobile counterpart of the
[`frontek-reads`](https://github.com/lonelyobserver0/frontek-reads) web app
(`feeds.frontek.dev`). You search for the sites you like, subscribe, and the
Home shows the latest articles from all your subscriptions aggregated together.
No account, no server-side storage: subscriptions and a small article cache live
on the device.

## Why native (and no proxy)

The web app has to route every feed request through a **CORS proxy**, because a
browser can't fetch other domains directly. A native Android client is **not
subject to CORS**, so feeds are fetched straight from their origin with OkHttp —
the proxy is gone entirely. Everything else (curated catalog, subscriptions,
reader, OPML) is ported one-to-one.

## Features

- **Discover** — search the curated catalog (`assets/catalog.json`) by name,
  category or site, or **paste any URL**: the app recognises whether it's already
  a feed, otherwise it auto-discovers one (`<link rel="alternate">`, then common
  paths `/feed`, `/rss`, `/atom.xml`, …).
- **Home** — articles from every subscription, newest first, with a per-source
  filter.
- **Favorites & Read later** — save any article to two on-device collections via
  the heart / bookmark buttons on each card (and in the reader). Saved articles
  keep their full content, so they stay readable even after the feed cache
  evicts them.
- **Reader** — tapping an article opens an in-app reader rendered in a WebView
  with **JavaScript disabled**. It shows the feed content immediately; the
  **"Leggi articolo intero"** button fetches the original page and extracts the
  readable text (readability-lite). Untrusted HTML is **sanitized** with an
  allowlist before display (no `script`, `iframe`, `on*`, `javascript:`).
- **OPML / JSON** — import and export your subscriptions via the system file
  picker (SAF) for backup and portability.
- **Settings** — clear the article cache or wipe all local data.

## Tech stack

- **Jetpack Compose** + Material 3, brand palette from the web app
  (teal `#2A9D8F`, coral `#E76F51`, dark `#264653`, cream `#F8F6EF`).
- **Kotlin 2.3** on **AGP 9.2** (built-in Kotlin) + the Compose compiler plugin.
- **OkHttp** for networking, **Jsoup** for feed/HTML parsing, sanitizing and
  readability extraction.
- **org.json** (bundled with Android) for the catalog and persistence — no extra
  serialization dependency. Subscriptions and cache are stored as JSON files in
  the app's `filesDir`.
- `minSdk 24`, `targetSdk 36`, `applicationId` / namespace `dev.frontek.feeds`.

## Project layout

```
app/src/main/
├── assets/catalog.json                 # curated feed catalog (shared with the web app)
└── java/dev/frontek/feeds/
    ├── MainActivity.kt
    ├── model/         # data classes
    ├── net/Http.kt    # direct OkHttp fetching
    ├── feed/          # FeedParser, FeedDiscovery, DateParser, HtmlUtils, UrlUtils
    ├── data/          # CatalogRepository, Store (persistence), Opml
    └── ui/            # AppViewModel + Compose screens (Home, Discover, Reader, Settings)
```

## Build & run

```bash
# Debug APK
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device / emulator
./gradlew installDebug

# Unit tests (feed/date/sanitize/URL logic, runs on the JVM)
./gradlew testDebugUnitTest
```

Requires the Android SDK (platform 36, build-tools 36+) and a JDK. Copy your SDK
path into `local.properties` (`sdk.dir=…`) — that file is git-ignored.

## Updating the catalog

Add entries to `app/src/main/assets/catalog.json`:

```json
{ "title": "Name", "site": "https://site", "feed": "https://site/feed", "category": "Tech" }
```

## Privacy

No account, no tracking. Subscriptions and the article cache never leave the
device; feeds are fetched directly from their publishers.
