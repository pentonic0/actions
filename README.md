# Network Stream Player

A simple Android Network Stream player built with Kotlin, Jetpack Compose and Media3.

## What changed

- Server JSON/channel grid removed from the main flow.
- Home screen now lets you paste a stream URL and optional ClearKey DRM KID/KEY.
- Optional request headers added: Cookie, Referer, Origin and User-Agent.
- Saved Streams page added so URLs/KID/KEY/headers can be saved locally.
- Settings page added with Light/Dark mode.
- Existing video player UI/controls kept unchanged.

## Build

```bash
gradle assembleDebug
```

APK will be generated in:

```text
app/build/outputs/apk/debug/app-debug.apk
```
