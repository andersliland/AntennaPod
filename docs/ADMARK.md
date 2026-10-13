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

Keep these diffs tiny — call the façade / preferences helpers only; never put
HTTP/business logic here.

| File | Hook |
| --- | --- |
| `ClientConfigurator` | `AdmarkIntegration.init` + `onAppReady` |
| `DBWriter` | subscribe / unsubscribe via `AdmarkFeedCallbacks` (covers search, URL, OPML, sync) |
| `Media3PlaybackService` | attach/detach controller; `onPlayableChanged` / `onPositionMs` |
| `FeedUpdateWorker` | `onNewFeedItems` after update; `onFeedRefreshFailed` → revert + retry |
| `HttpDownloader` | capture `X-Admark-Audio-Sha256` into download request args |
| `MediaDownloadedHandler` | `onMediaDownloaded(item, request)` (sha verify + enqueue) |
| `FeedInfoFragment` | show/copy **original** feed URL via `getDisplayFeedUrl` |
| `OpmlWriter` | export original URL when proxied |
| `PreferenceActivity` / `MainPreferencesFragment` | register/open admark screen |
| `ui/preferences/.../preferences.xml` | one Settings entry |
| `settings.gradle` + module `build.gradle` deps | `:net:admark` |

Packaging-only markers use `FORK: packaging` (`applicationId`, package-hash allowlist).

### Re-apply after upstream sync

```bash
git fetch upstream develop
git rebase upstream/develop
# If a touchpoint file conflicts: keep upstream body, re-insert the // FORK: admark
# one-liner(s) calling AdmarkIntegration / AdmarkFeedCallbacks / AdmarkPreferences.
rg 'FORK: admark' -n
./gradlew :app:assembleDebug :net:admark:test
```

Do **not** re-merge admark business logic into player/download classes.

## HTTP contract (assumed; adjust in `AdmarkApiPaths` / parser)

Alto docs (`andersliland/alto` `services/admark/docs/ANTENNAPOD_INTEGRATION.md`)
were not reachable when this was written. Paths below match the stated contract
and accept a few JSON key aliases so small server renames are cheap.

Default base URL: `https://admark.liland.xyz` (overridable in Settings → admark).

### Auth

Optional `X-Alto-Token: <token>` when set in Settings → admark (token string
stored on device; not sent as `Authorization: Bearer`).

### Lookup

`GET {base}/api/lookup?episode_guid=...&feed_url=...`

`feed_url` is always the **original** publisher URL (not the proxied one).
Episode identity stays on the original GUID (proxied feeds preserve GUIDs).

```json
{
  "status": "succeeded",
  "segments": [
    { "start": 120.0, "end": 150.0, "label": "ad" }
  ]
}
```

Statuses: `succeeded` → READY (skip), `running` → PENDING, plus `missing` /
`failed`. Segment times are seconds.

### Enqueue analysis

`POST {base}/api/analyze` with
`{enclosure_url, episode_guid, feed_url, podcast_title, episode_title}`.

Response may include `job_id` and `status: running`.

### Job polling

`GET {base}/api/jobs/{job_id}` until `succeeded` (or failure). Fallback:
re-lookup when no job id is present.

### Proxied subscriptions

`POST {base}/api/subscriptions` with `{feed_url}` (+ optional `podcast_title`):

```json
{
  "podcast_id": "…",
  "proxied_feed_url": "https://admark…/feeds/…",
  "original_feed_url": "https://publisher…/feed.xml"
}
```

On success the client stores the original URL for UI/OPML/sync, swaps the
feed's download URL to `proxied_feed_url`, and refreshes so episode enclosures
come from the same files admark analysed (DAI-safe).

`DELETE {base}/api/subscriptions/{podcast_id}` (fallback:
`DELETE …/subscriptions?feed_url=…`) on unsubscribe.

`GET /api/subscriptions` is reserved server-side; the app does not list yet.

### Audio integrity (optional)

Proxied audio responses may carry `X-Admark-Audio-Sha256`. Downloads store the
header; before applying skips to a local file the client verifies the hash and
withholds skips on mismatch.

## Proxy behaviour

1. **Subscribe** (any path): background `POST /api/subscriptions`, then silent
   URL swap. UI keeps showing the original URL.
2. **Migrate**: one-time WorkManager job on first enable / app ready; manual
   **Route all via admark** in Settings.
3. **Fallback**: on register/refresh failure keep or revert to the original URL,
   queue the feed id, retry with exponential backoff (`AdmarkProxyWorker`).
   Subtle status line in Settings → admark.
4. **Unsubscribe**: `DELETE` then clear local mapping.

## Known gaps

1. Live service may still be undeployed / behind oauth at the default host.
2. No webhook receiver (client-initiated enqueue on refresh/download).
3. In-memory marks cache only.
4. Optional future: CI copy of Release APK into private F-Droid (see `FDROID.md`).

## How to test

1. Settings → admark → enable, confirm base URL (+ token), auto-skip / auto-enqueue.
2. Subscribe / refresh feed / download → logcat `AdmarkService` / `AdmarkPlayback`.
3. Confirm Feed info still shows the original URL while downloads hit the proxy.
4. Play episode with `succeeded` + `segments` → seek past ads + snackbar.
5. `./gradlew --console=plain :net:admark:test`
