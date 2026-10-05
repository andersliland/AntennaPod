# AntennaPod ↔ admark integration

## Sync status

Feature branch is based on upstream `AntennaPod/AntennaPod` `develop`.

## Isolation strategy (personal fork)

Almost all admark logic lives outside upstream-owned code paths so rebasing onto
`upstream/develop` stays cheap.

### Dedicated code

| Area | Location |
| --- | --- |
| HTTP client, parse, cache, enqueue | `:net:admark` (`AdmarkClient`, `AdmarkService`, …) |
| Public façade | `AdmarkIntegration` + `AdmarkPlaybackController` |
| Preferences storage | `storage/preferences` → `AdmarkPreferences` |
| Settings UI | `AdmarkPreferencesFragment` + `preferences_admark.xml` |
| Strings | `ui/i18n` keys prefixed `pref_admark_` / `admark_` |

### Core touchpoints (search `FORK: admark`)

Keep these diffs tiny — call the façade only; never put HTTP/business logic here.

| File | Hook |
| --- | --- |
| `ClientConfigurator` | `AdmarkIntegration.init(context)` |
| `Media3PlaybackService` | attach/detach controller; `onPlayableChanged` / `onPositionMs` |
| `FeedUpdateWorker` | `AdmarkIntegration.onNewFeedItems(...)` after `updateFeed` |
| `MediaDownloadedHandler` | `AdmarkIntegration.onMediaDownloaded(item)` |
| `FeedItemlistFragment` / `FeedInfoFragment` | `AdmarkIntegration.onFeedSubscribed(feed)` on subscribe |
| `PreferenceActivity` / `MainPreferencesFragment` | register/open admark screen |
| `ui/preferences/.../preferences.xml` | one Settings entry |
| `settings.gradle` + module `build.gradle` deps | `:net:admark` |

Packaging-only markers use `FORK: packaging` (`applicationId`, package-hash allowlist).

### Re-apply after upstream sync

```bash
git fetch upstream develop
git rebase upstream/develop
# If a touchpoint file conflicts: keep upstream body, re-insert the // FORK: admark
# one-liner(s) calling AdmarkIntegration / AdmarkPlaybackController.
rg 'FORK: admark' -n
./gradlew :app:assembleDebug :net:admark:test
```

Do **not** re-merge admark business logic into player/download classes.

## Upstream investigation (AntennaPod)

| Feature | Location | Notes |
| --- | --- | --- |
| Skip intro/outro | `SkipUtils`, feed prefs | Fixed seconds per podcast |
| Skip silence | ExoPlayer | Not range-based |
| Chapters | Podlove / Podcast Index / media tags | No auto-skip of ad chapters |
| FF/RW intervals | `UserPreferences` | Manual buttons |

admark ranges are handled like skip-ending: position observer → façade seek.

## HTTP contract (VERIFIED against alto)

Aligned with `andersliland/alto` `services/admark/ANTENNAPOD_INTEGRATION.md`.
Paths/headers/JSON below match that contract. The live service at the default
base URL may still be undeployed; client code is ready when it comes up.

Default base URL: `https://admark.liland.xyz` (overridable in Settings → admark).

### Auth

Optional `X-Alto-Token: <token>` when set in Settings → admark (token string
stored on device; not sent as `Authorization: Bearer`).

### Lookup

`GET {base}/api/lookup?episode_guid=...&feed_url=...`

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

### Subscriptions

`POST {base}/api/subscriptions` with `{feed_url, podcast_title}` when the user
subscribes to a podcast.

## Known gaps

1. Live service may still be undeployed at the default host.
2. No webhook receiver (client-initiated enqueue on refresh/download).
3. In-memory marks cache only.
4. Optional future: CI copy of Release APK into private F-Droid (see `FDROID.md`).

## How to test

1. Settings → admark → enable, confirm base URL (+ token), auto-skip / auto-enqueue.
2. Subscribe / refresh feed / download → logcat `AdmarkService` / `AdmarkPlayback`.
3. Play episode with `succeeded` + `segments` → seek past ads + snackbar.
4. `./gradlew --console=plain :net:admark:test`
