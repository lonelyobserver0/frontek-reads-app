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

- **Discover** — a **dynamic feed search over the web** via the Feedly Cloud
  search API (no API key). Type "tecnologia" and it finds tech feeds from across
  the web; type "hdblog" and it finds HDblog and its sub-feeds — not limited to a
  hand-curated list. When the query is empty (or the search service is
  unreachable) it falls back to a bundled catalog (`assets/catalog.json`, 45+
  feeds) for suggestions/offline use. You can also **paste any URL**: the app
  recognises whether it's already a feed, otherwise it auto-discovers one from the
  page's `<link rel="alternate">` tags, feed-looking links, and common paths
  (`/feed`, `/rss`, `/atom.xml`, `?feed=rss2`, …) — each candidate is verified by
  actually parsing it before subscribing.
- **Home** — articles from every subscription, newest first, with a per-source
  filter. Article cards show a thumbnail (from `media:thumbnail`/`enclosure` or
  the first inline image) to the left of the title and summary when one is found.
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
- **Localization** — English, Italian, Spanish and French. The app defaults to
  **English** and automatically follows the system language when it is one of the
  supported locales. A language selector in Settings (System / Italiano / English
  / Español / Français) lets you override it per-app; the choice persists across
  restarts and the app also appears in the Android 13+ system language picker.
- **Adjustable text size** — a stepper in Settings scales the app font from 80%
  to 180% (on top of the system font scale). It applies everywhere, including the
  in-app reader (WebView `textZoom`), and persists across restarts.
- **Settings** — text size, language, clear the article cache, or wipe all local
  data.

## Tech stack

- **Jetpack Compose** + Material 3, brand palette from the web app
  (teal `#2A9D8F`, coral `#E76F51`, dark `#264653`, cream `#F8F6EF`).
- **Kotlin 2.3** on **AGP 9.2** (built-in Kotlin) + the Compose compiler plugin.
- **OkHttp** for networking, **Jsoup** for feed/HTML parsing, sanitizing and
  readability extraction, **Coil** for async thumbnail loading.
- **org.json** (bundled with Android) for the catalog and persistence — no extra
  serialization dependency. Subscriptions and cache are stored as JSON files in
  the app's `filesDir`.
- **Localization** via string resources: English is the default (`values/`),
  with `values-it/`, `values-es/`, `values-fr/`. Per-app language override uses
  AppCompat locales (`AppCompatDelegate.setApplicationLocales`), persisted
  automatically; `generateLocaleConfig` wires up the system language picker.
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

No account, no tracking. Subscriptions, saved articles and the cache never leave
the device, and feeds are fetched directly from their publishers. The one
exception is **Discover search**: the text you type there is sent to the Feedly
Cloud search API to find matching feeds across the web. Browsing your Home,
reading, and everything else stay fully local.
