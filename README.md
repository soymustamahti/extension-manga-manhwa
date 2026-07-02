# Extension Manga/Manhwa

Extension repository for a single Spanish source: **ManhwaWeb**.

This project is a minimal Keiyoushi-compatible source tree containing only the `src/es/manhwaweb` extension and the Gradle build logic required to compile it. The extension runs inside compatible readers such as Tachimanga/Mihon-style clients; it does not require a VPS by itself.

## What Runs Where

```text
Tachimanga / compatible reader
  -> installs this extension
  -> calls the source on demand
  -> displays search, details, chapters and pages
```

No backend service is required for direct reading. A VPS is only useful if you want a 24/7 downloader/indexer flow with Suwayomi and Komga.

## Build Locally

Requirements:

- JDK 17
- Android SDK

Build the extension:

```bash
./gradlew :src:es:manhwaweb:assembleRelease
```

The APK is generated under:

```text
src/es/manhwaweb/build/outputs/apk/release/
```

## Publish For Tachimanga

The GitHub Action `.github/workflows/build-and-publish.yml` builds the APK and publishes a `repo` branch containing:

- `index.min.json`
- `index.html`
- `apk/*.apk`
- `icon/*.png`

After the workflow succeeds, add this repository URL in Tachimanga:

```text
https://raw.githubusercontent.com/soymustamahti/extension-manga-manhwa/repo/index.min.json
```

Then install the **ManhwaWeb** extension from the app.

## Legal / Usage Note

Use this only for content you are authorized to access. This repository does not include any manga/manhwa content and should not be used for mass scraping, bypassing protections, paywalls, CAPTCHAs, DRM, or site restrictions.

## Upstream Attribution

The extension source and Gradle build structure are derived from Keiyoushi extensions source:

- Upstream: https://github.com/keiyoushi/extensions-source
- Source snapshot: `450e505cf097b5c6d33782f91d4debbbe7c65eb4`
- License: Apache-2.0
