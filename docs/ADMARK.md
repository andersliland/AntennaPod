# AntennaPod ↔ admark integration

## Sync status

Feature branch is based on upstream `AntennaPod/AntennaPod` `develop`.

## Isolation strategy (personal fork)

Almost all admark logic lives outside upstream-owned code paths so rebasing onto
`upstream/develop` stays cheap.

### Dedicated code

| Area | Location |
| --- | --- |
| HTTP client, parse, cache, enqueue, proxy | `:net:admark` (`AdmarkClient`, `AdmarkService`, `AdmarkProxyWorker`, …) |
| Public façade | `AdmarkIntegration` + `AdmarkPlaybackController` |
| Preferences storage | `storage/preferences` → `AdmarkPreferences` + `AdmarkFeedCallbacks` |
| Settings UI | `AdmarkPreferencesFragment` + `preferences_admark.xml` |
| Strings | `ui/i18n` keys prefixed `pref_admark_` / `admark_` |

### Core touchpoints (search `FORK: admark`)

| File | Hook |
| --- | --- |
| `ClientConfigurator` | `AdmarkIntegration.init` + `onAppReady` |
| `DBWriter` | subscribe / unsubscribe via `AdmarkFeedCallbacks` |
| `Media3PlaybackService` | attach/detach controller; position hooks |
| `FeedUpdateWorker` | `onNewFeedItems`; `onFeedRefreshFailed` → revert + retry |
| `HttpDownloader` | capture `X-Admark-Audio-SHA256` into download request args |
| `MediaDownloadedHandler` | `onMediaDownloaded(item, request)` |
| `FeedInfoFragment` | show/copy original feed URL via `getDisplayFeedUrl` |
| `OpmlWriter` | export original URL when proxied |
| Preferences entry points | Settings → admark |

## HTTP contract (alto PR #540 / `docs/ANTENNAPOD_INTEGRATION.md`)

Default base URL: `https://admark.liland.xyz`.

### Auth

| Surface | Auth |
| --- | --- |
| `/api/*` | `X-Alto-Token` (Settings → admark token) |
| `/feeds/*`, `/audio/*` | Query `?token=<subscription access_token>` only (embedded in `proxied_feed_url`) |

### Subscribe

```http
POST /api/subscriptions
X-Alto-Token: <token>
Content-Type: application/json

{ "feed_url": "https://feeds.megaphone.fm/history102" }
```

Response `201`:

```json
{
  "id": "a1b2c3…",
  "feed_url": "https://feeds.megaphone.fm/history102",
  "access_token": "…",
  "proxied_feed_url": "https://admark.liland.xyz/feeds/a1b2c3….xml?token=…",
  "active": true,
  "priority": "hot_new"
}
```

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/subscriptions` | List |
| `GET` | `/api/subscriptions/{id}` | One |
| `DELETE` | `/api/subscriptions?feed_url=` or `?podcast_id=` | Unsubscribe |

Client: swap download URL to `proxied_feed_url`; keep `feed_url` for UI/OPML/sync.

### Lookup

`GET /api/lookup?episode_guid=…&feed_url=…` → segments **and** `audio_sha256`.

Apply skips only when `audio_sha256` matches `X-Admark-Audio-SHA256` (or a re-hash of the local file).

### Audio

Proxied enclosures under `/audio/{id}/{guid}.{ext}?token=…` carry
`X-Admark-Audio-SHA256` and `X-Admark-Canonical: ready|pending`.

## Proxy behaviour

1. Subscribe (any path) → background POST → silent URL swap.
2. One-time migration + Settings → **Route all via admark**.
3. Failure → keep/revert original URL, WorkManager exponential backoff, status in Settings.
4. Unsubscribe → `DELETE ?feed_url=` / `?podcast_id=`.

## How to test

```bash
./gradlew --console=plain :net:admark:test
./gradlew :app:assembleDebug
```
