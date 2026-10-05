# Distribution: Obtainium + private F-Droid

Phone install for this fork uses **two** channels that share the same signed
APK from GitHub Actions:

1. **Obtainium** ← GitHub Releases (fastest path for a first phone install)
2. **Private F-Droid** ← publish that APK into the alto fdroid bucket — see
   [FDROID.md](./FDROID.md)

| Build | `applicationId` |
| --- | --- |
| Release | `xyz.liland.antennapod` |
| Debug | `xyz.liland.antennapod.debug` |

## Obtainium (phone-ready)

| Setting | Value |
| --- | --- |
| GitHub Releases | https://github.com/andersliland/AntennaPod/releases |
| Repo to add in Obtainium | `https://github.com/andersliland/AntennaPod` |
| App ID | `xyz.liland.antennapod` |
| APK filter | `AntennaPod-.*\.apk` |

### Steps on the phone

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add App → paste `https://github.com/andersliland/AntennaPod`.
3. Prefer releases; set APK filter to `AntennaPod-.*\.apk`.
4. Install when a Release asset exists (after secrets + first `v*` tag below).

Updates track new GitHub Release assets with the same `applicationId`.

## One-time: release keystore + GitHub secrets

Do **not** commit the keystore or passwords. Create a keystore locally if you
do not already have one:

```bash
keytool -genkeypair -v \
  -keystore antennapod-release.keystore \
  -alias antennapod \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'YOUR_STORE_PASSWORD' \
  -keypass 'YOUR_KEY_PASSWORD' \
  -dname 'CN=AntennaPod Fork, OU=Personal, O=Liland, L=Unknown, ST=Unknown, C=NO'
base64 -w0 antennapod-release.keystore > antennapod-release.keystore.b64
```

Add these **four** repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | contents of `antennapod-release.keystore.b64` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | e.g. `antennapod` |
| `RELEASE_KEY_PASSWORD` | key password |

## Publish first phone APK

After the four secrets are set:

```bash
git tag v3.12.3-fork.1
git push origin v3.12.3-fork.1
```

Or run workflow **Release** via `workflow_dispatch`.

`.github/workflows/release.yml` builds `:app:assembleFreeRelease`, signs with
those secrets, and uploads `AntennaPod-<version>.apk` to
https://github.com/andersliland/AntennaPod/releases. That asset is the source
for Obtainium and for F-Droid publish.

## Private F-Droid

After each release, publish the signed APK into the alto fdroid bucket (see
[FDROID.md](./FDROID.md)):

```bash
scripts/publish-from-github-release.sh \
  --repo andersliland/AntennaPod \
  --package xyz.liland.antennapod
```

Client repo URL: `https://fdroid.liland.xyz/fdroid/repo`.

## What's left for Anders (first install)

1. Create/store the release keystore and set the **4** GitHub Actions secrets
   (do not invent values in CI/docs).
2. Tag `v*` (or `workflow_dispatch` Release) and wait for the APK on
   https://github.com/andersliland/AntennaPod/releases.
3. Add the repo in Obtainium (table above) and install.
4. Optional: after cluster Signing Secret / alto F-Droid is up, run the publish
   script and add `https://fdroid.liland.xyz/fdroid/repo` in Neo Store /
   Droid-ify / F-Droid.

## Local signed build (optional)

```bash
./gradlew :app:assembleFreeRelease \
  -PreleaseStoreFile=/absolute/path/to/antennapod-release.keystore \
  -PreleaseStorePassword=... \
  -PreleaseKeyAlias=antennapod \
  -PreleaseKeyPassword=...
```
