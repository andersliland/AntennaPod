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

Releases are automatic: every push to `develop` (code changes, not docs)
runs **Release**, which picks the next `v<versionName>-fork.<N>` tag, builds
`:app:assembleFreeRelease` with `-PforkReleaseNumber=N`, verifies the APK is
signed with the fork key (cert SHA-256 `f866a4a8…1d140767`), and uploads
`AntennaPod-<versionName>-fork.<N>.apk` to
https://github.com/andersliland/AntennaPod/releases.

`versionCode = upstream versionCode * 100 + N` (e.g. 3.12.3-fork.2 →
`312039502`), so each release is strictly newer than the last and Android,
Obtainium and F-Droid all treat it as an update. `PreferenceUpgrader` maps the
code back to the upstream scale so upstream migrations still run after rebases.

Manual options: push a `vX.Y.Z-fork.N` tag, or run **Release** via
`workflow_dispatch` (`publish=false` builds + verifies only and uploads the APK
as an artifact).

## Private F-Droid

No manual step: the alto `fdroid-prod-update` CronJob (every 6h) pulls the
newest Release APK into the repo and re-signs the index. Client repo URL:
`https://fdroid.liland.xyz/fdroid/repo` (see [FDROID.md](./FDROID.md)).

## Local signed build (optional)

```bash
./gradlew :app:assembleFreeRelease \
  -PreleaseStoreFile=/absolute/path/to/antennapod-release.keystore \
  -PreleaseStorePassword=... \
  -PreleaseKeyAlias=antennapod \
  -PreleaseKeyPassword=...
```
