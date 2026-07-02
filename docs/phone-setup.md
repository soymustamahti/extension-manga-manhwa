# Phone Setup

## Direct Tachimanga Flow

1. Open Tachimanga.
2. Open extension repositories.
3. Add:

   ```text
   https://raw.githubusercontent.com/soymustamahti/extension-manga-manhwa/repo/index.min.json
   ```

4. Install the **ManhwaWeb** extension.
5. Go to sources, choose **ManhwaWeb**, then search or browse.

This keeps reading on the phone. Automatic background updates depend on what the app and iOS/Android allow.

## Server Flow

Use the VPS flow only if you want server-side automation:

```text
Suwayomi on VPS
  -> installs extension
  -> updates library every few hours
  -> downloads CBZ
  -> Komga indexes CBZ
  -> reader connects to Komga
```

For always-on updates, the server flow is more reliable than a phone-only extension.
