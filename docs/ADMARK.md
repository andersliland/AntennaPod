# AntennaPod ↔ admark integration

## Sync status

Feature branch is based on upstream `AntennaPod/AntennaPod` `develop` (merged into
the fork before implementation).

## Upstream investigation (AntennaPod)

Existing skip/chapter machinery reused conceptually:

| Feature | Location | Notes |
| --- | --- | --- |
| Skip intro/outro | `SkipUtils`, feed prefs `feed_skip_intro` / `feed_skip_ending` | Fixed seconds per podcast; toast/snackbar on skip |
| Skip silence | ExoPlayer `setSkipSilenceEnabled` | Audio-level, not ranges |
| Chapters | RSS Podlove SC, Podcast Index JSON URL, ID3/Vorbis/M4A | Loaded via `ChapterUtils`; no auto-skip of ad chapters |
| FF/RW intervals | `UserPreferences` fast-forward/rewind secs | Manual skip buttons |

**Hypothesis check:** chapters can represent ad boundaries, but AntennaPod does **not**
auto-skip chapter ranges today. admark ranges are handled separately (like skip-ending)
inside `Media3PlaybackService`'s 1s position observer.

## Provisional HTTP contract (UNVERIFIED)

`andersliland/alto` was inaccessible to the agent. Paths below are provisional and
centralized in `AdmarkApiPaths`. Adjust after reading `services/admark`.

### Auth

Optional `Authorization: Bearer <token>` when a token is set in Settings → admark.

### Get marks

`GET {base}/v1/episodes/marks?media_url=...&guid=...&feed_url=...`

Success JSON (flexible parser also accepts `ads`/`skips`, second-based fields):

```json
{
  "status": "ready",
  "ranges": [
    { "start_ms": 120000, "end_ms": 150000, "label": "ad" }
  ]
}
```

Statuses: `ready`, `pending`, `missing`, `failed`.

### Enqueue analysis

`POST {base}/v1/episodes/analyze`

```json
{
  "media_url": "https://cdn.example/ep.mp3",
  "guid": "episode-guid",
  "feed_url": "https://example.com/feed.xml",
  "title": "Episode title"
}
```

Expected `200`/`202` with `status` of `pending` or `ready` (and optional `ranges`).

### Polling

When status is `pending`, the client polls `GET .../marks` a few times with backoff
during playback / ensure calls. There is no separate job-id endpoint in this
provisional contract (gap if alto uses job IDs).

## Known gaps

1. **Paths/query/body field names unverified** against alto OpenAPI.
2. No webhook receiver in AntennaPod (mobile clients typically cannot expose one);
   new-episode trigger is client-initiated on feed refresh / download.
3. No persistent on-disk marks cache yet (in-memory + refetch).
4. APK install/run may be unavailable in the cloud agent if no emulator/device.

## How to test

1. Settings → admark → set base URL (+ token if required), enable auto-skip and
   auto-enqueue.
2. Refresh a feed with a new episode → logcat `AdmarkService` should show analyze
   enqueue when marks are missing.
3. Play an episode that already has `ready` ranges → playback should seek past each
   range and show a snackbar.
4. If HTTP 404 on `/v1/...`, update `AdmarkApiPaths` to match alto and rebuild.
