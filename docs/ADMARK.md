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

## Provisional HTTP contract (UNVERIFIED)

`andersliland/alto` was inaccessible to the agent. Paths are provisional in
`AdmarkApiPaths`.

### Auth

Optional `Authorization: Bearer <token>` when set in Settings → admark.

### Get marks

`GET {base}/v1/episodes/marks?media_url=...&guid=...&feed_url=...`

```json
{
  "status": "ready",
  "ranges": [
    { "start_ms": 120000, "end_ms": 150000, "label": "ad" }
  ]
}
```

Statuses: `ready`, `pending`, `missing`, `failed`. Parser also accepts `ads`/`skips`
and second-based fields.

### Enqueue analysis

`POST {base}/v1/episodes/analyze` with `{media_url,guid,feed_url,title}`.

### Polling

On `pending`, client polls `GET .../marks` with short backoff (no job-id API in
this provisional contract).

## Known gaps

1. Paths unverified against alto OpenAPI.
2. No webhook receiver (client-initiated enqueue on refresh/download).
3. In-memory marks cache only.
4. Optional future: CI copy of Release APK into private F-Droid (see `FDROID.md`).

## How to test

1. Settings → admark → enable, set base URL (+ token), auto-skip / auto-enqueue.
2. Refresh feed / download → logcat `AdmarkService` / `AdmarkPlayback`.
3. Play episode with `ready` ranges → seek past ads + snackbar.
4. `./gradlew --console=plain :net:admark:test`
