# Distribution: Obtainium + private F-Droid

This fork supports **two** install/update channels that share the same signed
APK built by GitHub Actions:

1. **Obtainium** ← GitHub Releases (this doc)
2. **Private F-Droid** ← copy that APK into the alto binary repo — see
   [FDROID.md](./FDROID.md)

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

The workflow (`.github/workflows/release.yml`) builds `:app:assembleFreeRelease`,
signs with the secrets above, and uploads `AntennaPod-<version>.apk` to a GitHub
Release. That asset is the source for both Obtainium and (after copy) the private
F-Droid repo.

## Install with Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add app → source = this GitHub repo (`andersliland/AntennaPod`).
3. Prefer releases / APK filter, e.g. `AntennaPod-.*\.apk`.
4. Updates track new GitHub Release assets with the same `applicationId`.

## Private F-Droid

After each release, copy the APK into the self-hosted F-Droid repo in
`andersliland/alto`. Client setup (Neo Store / Droid-ify / F-Droid) and optional
Shizuku silent installs: [FDROID.md](./FDROID.md).

## Local signed build (optional)

```bash
./gradlew :app:assembleFreeRelease \
  -PreleaseStoreFile=/absolute/path/to/antennapod-release.keystore \
  -PreleaseStorePassword=... \
  -PreleaseKeyAlias=antennapod \
  -PreleaseKeyPassword=...
```
