# admark client

HTTP client for a self-hosted **admark** service that provides skippable ad ranges
for podcast episodes and accepts analysis enqueue requests for new episodes.

## API access status

`https://github.com/andersliland/alto` (`services/admark`) was **not readable** from
this Cloud Agent environment (private repo → GitHub 404). The path constants in
`AdmarkApiPaths` are therefore a **provisional client contract** derived from the
product requirements (fetch ranges by media URL / guid / feed URL; enqueue analysis
when missing). They must be verified/replaced against the real alto OpenAPI before
production use.

## Configured at runtime

- Base URL (Tailscale / home cluster), e.g. `https://admark.example.ts.net`
- Optional bearer token
- Auto-skip during playback
- Auto-enqueue analysis on new episodes / downloads

See `docs/ADMARK.md` in the repo root for request/response shapes and known gaps.
