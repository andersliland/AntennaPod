# `:net:admark`

Isolated HTTP client and playback/enqueue helpers for a self-hosted **admark**
service. Upstream AntennaPod files should only contain thin `// FORK: admark`
hooks that call `AdmarkIntegration` / `AdmarkPlaybackController`.

See `docs/ADMARK.md` for touchpoints, rebase notes, and the VERIFIED alto API.
