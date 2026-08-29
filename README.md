[![Buy me a coffee](https://cdn.buymeacoffee.com/buttons/v2/default-red.png)](https://www.buymeacoffee.com/adegard)

# Radio Streamer

A simple, ad-free internet radio app for Android. Stream any radio station from the web, keep listening with the screen off, and manage your own station list.

[![Build APK](https://github.com/adegard/RadioStreamer/actions/workflows/build.yml/badge.svg)](https://github.com/adegard/RadioStreamer/actions/workflows/build.yml)

## Features

- 📻 **Internet radio streaming** powered by ExoPlayer / Media3 (supports MP3, AAC, HLS/m3u8, http and https streams)
- 🌙 **Background playback** — audio keeps playing with the screen off, with lock-screen and notification controls
- ➕ **Add your own stations** — tap the `+` button and enter a name plus a stream URL (e.g. `http://example.com:8000/stream`)
- 🗑️ **Delete stations** — tap the trash icon on any row
- 💾 **Reinstall-proof list** — your stations are automatically backed up to `Download/RadioStreamer/stations.json` and restored after a reinstall
- 🚫 No ads, no tracking

## Default stations

The app ships with 10 well-known stations: BBC World Service, BBC Radio 1, FIP Paris, Deutschlandfunk, KEXP Seattle, WFMU New York, SomaFM Groove Salad, Radio Paradise, Radio Swiss Jazz and Jazz24.

## Install

Download `RadioStreamer-debug.apk` (or the signed `-release.apk`) from the [latest release](../../releases/latest) or from any successful [Actions run](../../actions) artifact, then install it on your phone. You may need to allow "install unknown apps" for your browser/file manager.

## Build it yourself

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # signed release APK
```

APKs end up in `app/build/outputs/apk/<variant>/`. Requires JDK 17 and an Android SDK (API 34).

Pushing a tag like `v1.2` makes GitHub Actions build signed APKs and publish them as a GitHub Release automatically.

## Tech

Kotlin · AndroidX Media3 (ExoPlayer + MediaSessionService foreground service) · Material 3 · minSdk 24 / targetSdk 34
