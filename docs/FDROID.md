# Private F-Droid repo (self-hosted)

Alongside Obtainium + GitHub Releases, this fork can be installed from a **private
F-Droid repository** on the home cluster (Neo Store, Droid-ify, or the official
F-Droid client).

The F-Droid *server* is **not** in this Android repo. It lives in
[`andersliland/alto`](https://github.com/andersliland/alto) (simple binary /
fdroidrepo-style service added in a parallel PR). This document only describes
how AntennaPod release APKs feed that repo.

## Dual distribution

| Channel | Source of truth | Client |
| --- | --- | --- |
| GitHub Releases | `.github/workflows/release.yml` on `v*` (or `workflow_dispatch`) | [Obtainium](./OBTAINIUM.md) |
| Private F-Droid | Copy signed APK from the GitHub Release into the alto F-Droid repo | Neo Store / Droid-ify / F-Droid |

`applicationId` is `xyz.liland.antennapod` so installs do not clash with Play or
public F-Droid AntennaPod. Use the **same signing key** for every release so
F-Droid and Obtainium can update the same installed app.

## After each `v*` release

1. Wait for the **Release** workflow to finish and publish
   `AntennaPod-<version>.apk` on the GitHub Release.
2. Copy that APK into the alto F-Droid binary repo (PVC / drop directory —
   exact path is defined in alto’s F-Droid service docs).
3. Refresh/update the repo index on the F-Droid server (per alto ops).
4. On the phone, the F-Droid client (or Neo Store / Droid-ify) shows the update
   when it next syncs that repo URL (typically Tailscale / home HTTPS).

Until alto’s F-Droid service is ready, step 2–3 are manual or skipped; Obtainium
from GitHub Releases still works.

## Future CI (optional, not blocking)

A later improvement can add a post-release job that downloads the Release asset
and pushes it into the F-Droid PVC/repo (e.g. `scp`/`rclone`/workflow call into
alto). **Do not block** merging this AntennaPod PR on that automation —
`release.yml` remains the artifact source.

## Phone clients

Add the private repo URL (from alto) in one of:

- [Neo Store](https://github.com/NeoApplications/Neo-Store)
- [Droid-ify](https://github.com/Droid-ify/client)
- Official F-Droid client → Repositories → add repo

Prefer HTTPS over Tailscale or another private network. Repo signing key /
fingerprint comes from the alto F-Droid deployment.

## Optional: Shizuku silent installs

On the phone only, [Shizuku](https://shizuku.rikka.app/) can allow some F-Droid
clients / install helpers to perform **silent background installs** without a
confirmation dialog each time (device-dependent; usually needs wireless
debugging or root once). This is optional convenience and unrelated to the
server or this repo’s CI.
