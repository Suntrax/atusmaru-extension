# Oni: Atsumaru

A headless background scraper extension for the **Oni** manga client.
Queries the atsu.moe API and returns the image URLs for a single
user-specified chapter — always including the total chapter count so the UI
can show the available range.

## How it works

1. **Discovery** — The Oni main app finds this extension via the
   `com.blissless.mangaclient.EXTENSION_BEACON` broadcast receiver and the
   `"Oni: "` label prefix.
2. **Query** — The main app calls this extension's `ContentProvider` with the
   URI
   `content://com.blissless.atsumaru.provider/scrape?manga=<name>&anilistId=<id>&chapter=<required>`.
   The `chapter` parameter is **required** — the extension does not support a
   "list all chapters" mode. If `chapter` is missing, the extension returns an
   error immediately.
3. **Scrape** — A three-step pipeline over the atsu.moe API:

   **Step 1 — Search.** A single HTTP GET finds the best-matching manga:

   ```
   GET https://atsu.moe/api/search/manga?q=<url-encoded-name>&query_by=title&per_page=5
   ```

   The response is Typesense-backed and returns `{"hits": [{"document": {...}}, ...]}`.
   The top hit's `document.id` (a short string like `"CM0wz"`) is used for the
   next call.

   **Step 2 — List chapters.** A single HTTP GET enumerates every chapter
   (metadata only — no image URLs yet):

   ```
   GET https://atsu.moe/api/manga/info?mangaId=<id>
   ```

   The response's `chapters` array contains objects with `id`, `title`,
   `number`, `index`, `pageCount`, and `scanId`. The array length is recorded
   as `totalChapters`.

   **Step 3 — Find + fetch the requested chapter.** The extension finds the
   matching chapter via a three-pass matcher:

   1. **Exact number match** — normalized chapter number equals the request
   2. **Case-insensitive title match** — chapter's `title` field equals the
      request (lets users type "Prologue 1" instead of "1")
   3. **Numeric equality** — `number` parsed as a double equals the request
      parsed as a double (handles `"1"` matching `1.0`)

   Once matched, the extension fetches that chapter's page manifest:

   ```
   GET https://atsu.moe/api/read/chapter?mangaId=<id>&chapterId=<chapterId>
   ```

   The response's `readChapter.pages` array contains objects with an `image`
   field holding relative URLs like `/static/pages/CM0wz/E5PXRSUC/0.webp`. The
   extension prefixes these with the base URL to produce absolute
   `https://atsu.moe/...` URLs.

   Chapter keys are normalized chapter numbers (`"1"` instead of `"1.0"`).

4. **Return** — The result is serialized to JSON and returned to the main app.

No `WebView`, no JavaScript rendering, no auth, no token flow — every call is a
plain HTTP GET. atsu.moe serves chapter pages as static WebP files with no
authentication.

## Data format returned

### Success (chapter found)

```json
{
  "totalChapters": 402,
  "chapter": {
    "number": "1",
    "title": "Prologue 1",
    "group": "",
    "images": [
      "https://atsu.moe/static/pages/CM0wz/E5PXRSUC/0.webp",
      "https://atsu.moe/static/pages/CM0wz/E5PXRSUC/1.webp"
    ]
  }
}
```

### Error — no chapter provided

```json
{ "error": "No chapter provided. This extension requires a chapter number (e.g. '1', '1.5') or a chapter title (e.g. 'Prologue 1')." }
```

### Error — chapter not found (totalChapters still included so the UI can show the valid range)

```json
{ "totalChapters": 402, "error": "Chapter '999' not found. Available range: 1–402." }
```

### Error — other failures

```json
{ "error": "No manga name provided." }
{ "error": "No manga found for '<name>'." }
{ "error": "Failed to list chapters: <network error>" }
{ "error": "Failed to fetch chapter <n> pages: <network error>" }
{ "error": "Chapter <n> returned no pages." }
```

## Technical details

| | |
|---|---|
| **Dependencies** | Zero. Uses only `java.net.HttpURLConnection` + `org.json`. |
| **HTTP calls per scrape** | 3 (search + manga/info + read/chapter) |
| **APK size** | ~40-50 KB after R8 shrinking |
| **Min Android** | API 26 |
| **Parameters read** | `manga` (English or Romaji title), `chapter` (**required** — chapter number, decimal, or title) |

## Architecture

| File | Purpose |
|------|---------|
| `AtsumaruScraper.kt` | All HTTP + JSON parsing logic, including the three-pass chapter matcher. Returns a `Map<String, *>` with `totalChapters` and either `chapter` (success) or `error` (failure). |
| `ScraperProvider.kt` | `ContentProvider` entry point. Reads the `chapter` URI parameter, passes it through, serializes the scraper result to JSON. Also logs every request and result to Logcat under tag `Atsumaru`. |
| `ExtensionBeaconReceiver.kt` | Empty `BroadcastReceiver` for discovery. |

## Notes

- The scraper is **synchronous** and called from a binder thread. The main
  app is expected to call `query()` from a background coroutine.
- The `chapter` parameter is **required**. The extension does not support a
  "list all chapters" mode — if you don't know the chapter number, guess
  `"1"` and the error response will tell you the available range
  (`"Available range: 1–402."`).
- The `chapter` parameter accepts plain numbers (`"1"`), decimal chapters
  (`"1.5"`), or full chapter titles (`"Prologue 1"`) — the matcher tries
  number first, then title, then numeric equality.
- When the requested chapter isn't found, the error response still includes
  `totalChapters` so the UI can tell the user the valid range.
- Chapter-image URLs are returned as **absolute** `https://atsu.moe/...`
  URLs so the main app can load them directly.
- The `group` field is always empty (`""`) — atsu.moe's chapter object
  doesn't expose a scanlation group name, only a `scanId` (an internal ID).

## Building

1. Place your release keystore at `app/release.jks` and add its credentials to
   `local.properties` (gitignored):

   ```properties
   storeFile=/absolute/path/to/release.jks
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```

   (Or copy `local.properties.example` to `local.properties` and fill in the
   values. If you skip this, release builds fall back to the debug signing
   key — fine for testing, not for distribution.)

2. Build the shrunk, signed APK:

   ```bash
   ./gradlew assembleRelease
   ```

   Output: `app/build/outputs/apk/release/app-release.apk`

3. Install alongside the Oni main app:

   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
