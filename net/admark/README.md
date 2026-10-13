# `:net:admark`

Isolated HTTP client, proxy subscription routing, and playback/enqueue helpers
for a self-hosted **admark** service. Upstream AntennaPod files should only
contain thin `// FORK: admark` hooks that call `AdmarkIntegration` /
`AdmarkFeedCallbacks` / `AdmarkPreferences`.

See `docs/ADMARK.md` for touchpoints, rebase notes, proxied-feed behaviour, and
the assumed alto API (adjust `AdmarkApiPaths` + parsers if the contract moves).
