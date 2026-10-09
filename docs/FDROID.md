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

## After each release (automatic)

1. A push to `develop` runs **Release** and publishes
   `AntennaPod-<version>-fork.<N>.apk` on
   https://github.com/andersliland/AntennaPod/releases.
2. Within 6h the alto CronJob `fdroid-prod-update` downloads the newest
   Release APK, runs `fdroid update`, and re-signs the index (force it now with
   `kubectl -n fdroid-prod create job --from=cronjob/fdroid-prod-update now-$(date +%s)`).
3. The phone's F-Droid client sees the higher versionCode and offers the update.

Repo signing key fingerprint (SHA-256):
`722C1BBFC9F127BAA562CC66489861115BC9B25A766C8A17A43DEB0227F12033`

## Optional: Shizuku silent installs

On the phone only, [Shizuku](https://shizuku.rikka.app/) can allow some F-Droid
clients / install helpers to perform **silent background installs** without a
confirmation dialog each time (device-dependent; usually needs wireless
debugging or root once). This is optional convenience and unrelated to the
server or this repo’s CI.
