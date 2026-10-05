# Private F-Droid repo (self-hosted)

Alongside Obtainium + GitHub Releases, this fork can be installed from a **private
F-Droid repository** on the home cluster (Neo Store, Droid-ify, or the official
F-Droid client).

The F-Droid *server* is **not** in this Android repo. It lives in
[`andersliland/alto`](https://github.com/andersliland/alto). This document only
describes how AntennaPod release APKs feed that repo.

## Dual distribution

| Channel | Source of truth | Client |
| --- | --- | --- |
| GitHub Releases | `.github/workflows/release.yml` on `v*` (or `workflow_dispatch`) | [Obtainium](./OBTAINIUM.md) |
| Private F-Droid | Publish signed APK from the GitHub Release into the alto fdroid bucket | Neo Store / Droid-ify / F-Droid |

`applicationId` is `xyz.liland.antennapod` so installs do not clash with Play or
public F-Droid AntennaPod. Use the **same signing key** for every release so
F-Droid and Obtainium can update the same installed app.

## Client repo URL

| Setting | Value |
| --- | --- |
| F-Droid repo URL | `https://fdroid.liland.xyz/fdroid/repo` |
| Package | `xyz.liland.antennapod` |

Add that URL in Neo Store, Droid-ify, or F-Droid → Repositories. Prefer HTTPS
over Tailscale / home network if the public hostname is not yet live. Repo
signing key / fingerprint comes from the alto F-Droid deployment.

## After each `v*` release

1. Wait for the **Release** workflow to finish and publish
   `AntennaPod-<version>.apk` on
   https://github.com/andersliland/AntennaPod/releases.
2. Publish into the alto fdroid bucket (from the alto repo / ops host):

```bash
scripts/publish-from-github-release.sh \
  --repo andersliland/AntennaPod \
  --package xyz.liland.antennapod
```

3. Confirm the package appears under `https://fdroid.liland.xyz/fdroid/repo`.
4. On the phone, sync the private repo; the client shows the update.

Until alto’s F-Droid service and Signing Secret on the cluster are ready, skip
steps 2–4; Obtainium from GitHub Releases still works.

## Future CI (optional, not blocking)

A later improvement can add a post-release job that downloads the Release asset
and pushes it into the F-Droid PVC/repo. **Do not block** AntennaPod merges on
that automation — `release.yml` remains the artifact source.

## Optional: Shizuku silent installs

On the phone only, [Shizuku](https://shizuku.rikka.app/) can allow some F-Droid
clients / install helpers to perform **silent background installs** without a
confirmation dialog each time (device-dependent; usually needs wireless
debugging or root once). This is optional convenience and unrelated to the
server or this repo’s CI.
