# Obtainium / GitHub Releases distribution

This fork ships as a separate Android app id so it can sit beside Play Store /
F-Droid AntennaPod:

| Build | `applicationId` |
| --- | --- |
| Release | `xyz.liland.antennapod` |
| Debug | `xyz.liland.antennapod.debug` |

FileProvider authorities use the same prefix (`…provider` / `…debug.provider`).

## One-time: create a release keystore (local)

Do **not** commit the keystore or passwords.

```bash
keytool -genkeypair -v \
  -keystore antennapod-release.keystore \
  -alias antennapod \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 'YOUR_STORE_PASSWORD' \
  -keypass 'YOUR_KEY_PASSWORD' \
  -dname 'CN=AntennaPod Fork, OU=Personal, O=Liland, L=Unknown, ST=Unknown, C=NO'
```

Base64-encode for GitHub Actions:

```bash
base64 -w0 antennapod-release.keystore > antennapod-release.keystore.b64
```

## GitHub Actions secrets

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | contents of `antennapod-release.keystore.b64` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | e.g. `antennapod` |
| `RELEASE_KEY_PASSWORD` | key password |

## Publish a release APK

Tag and push (preferred):

```bash
git tag v3.12.3-fork.1
git push origin v3.12.3-fork.1
```

Or run workflow **Release** via `workflow_dispatch`.

The workflow builds `:app:assembleFreeRelease`, signs with the secrets above, and
uploads `AntennaPod-<version>.apk` to a GitHub Release.

## Install with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add app → source = this GitHub repo (`andersliland/AntennaPod`).
3. Prefer releases / APK filter, e.g. `AntennaPod-.*\.apk` (or `free/release` artifacts if you change the workflow).
4. Updates track new GitHub Release assets with the same `applicationId`.

## Local signed build (optional)

```bash
./gradlew :app:assembleFreeRelease \
  -PreleaseStoreFile=/absolute/path/to/antennapod-release.keystore \
  -PreleaseStorePassword=... \
  -PreleaseKeyAlias=antennapod \
  -PreleaseKeyPassword=...
```
