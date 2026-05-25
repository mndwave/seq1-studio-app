# Capacitor "Server URL" Pattern — seq1-sessions-app

## What this is

A native Android APK that wraps `sessions.seq1.net` in a Capacitor WebView shell.
This is the **canonical pattern** for converting a Next.js SSR app to Android.

## Architecture

```
APK (Capacitor shell)
 └── WebView → loads https://sessions.seq1.net live
      └── Native plugins bridge: mic, filesystem, vibration
```

### Why Server URL mode (not static bundle)

| Static bundle | Server URL (this project) |
|---|---|
| Requires `next export` (breaks SSR) | Works with SSR — loads live URL |
| APK rebuild on every web change | Web changes = instant, no APK rebuild |
| No server required | App requires internet for core functions anyway |
| Good for truly offline apps | Wrong for apps that are inherently online |

**Rule:** If the web app can't function offline anyway, use Server URL mode.
Only use static bundle if you genuinely need offline-first behaviour.

## When to rebuild the APK

**The rule in one sentence:** rebuild only when the native shell changes, never when the web app changes.

| Change type | APK rebuild needed? |
|---|---|
| `capacitor.config.ts` edited | ✅ Yes |
| `android/` directory edited (permissions, plugins, icons, theme) | ✅ Yes |
| Adding or upgrading a Capacitor plugin | ✅ Yes |
| Keystore / signing config changed | ✅ Yes |
| Any change to `sessions.seq1.net` web code | ❌ No — live instantly |
| New features, UI changes, bug fixes in admin-react | ❌ No |
| Backend seq1-healer changes | ❌ No |
| `admin-react/` code changes (including this guide) | ❌ No |

The GitHub Actions workflow (`build-apk.yml`) is path-scoped to enforce this automatically — it only triggers on `android/**` and `capacitor.config.ts`, never on web code paths.

**Why this matters for day-to-day work on SEQ1 Sessions:**
`sessions.seq1.net` is the live web app. When you fix a bug or add a feature there, the APK users
pick it up immediately on next app open — no rebuild, no redistribution, no Obtainium update prompt.
The APK is just a shell that loads the live URL. The only time you touch the APK is when something
genuinely native changes (a new Android permission, an icon update, a new hardware plugin).

## APK distribution

- **Latest APK:** `https://media.seq1.net/app/seq1-sessions-latest.apk`
- **Download page:** `https://sessions.seq1.net/app`
- **Versioned builds:** `https://media.seq1.net/app/seq1-sessions-{sha}.apk` (90-day retention)

## Local Build — Temporary Method Until GitHub CI Is Wired Up

Until `seq1-sessions-app` is on GitHub and Actions are configured, build the APK locally.

### Requirements

- Java 17: `sudo apt-get install openjdk-17-jdk`
- Android SDK: set `ANDROID_HOME` (GitHub Actions uses `android-actions/setup-android@v3`)
- AWS CLI: `pip install awscli` (for R2 upload)
- R2 credentials from `~/.claude/includes/api-credentials.includes`

### Build and upload (release APK)

```bash
cd ~/seq1-sessions-app

# 1. Sync capacitor config into Android project
npx cap sync android --no-open

# 2. Build release APK (requires keystore at seq1-sessions-release.keystore)
cd android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleRelease --no-daemon
cd ..

# 3. The APK is at:
APK="android/app/build/outputs/apk/release/app-release.apk"

# 4. Upload to R2
R2_ACCOUNT_ID="..."        # from api-credentials.includes
R2_ACCESS_KEY_ID="..."
R2_SECRET_ACCESS_KEY="..."
R2_BUCKET="media"
ENDPOINT="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
VERSION=$(grep 'versionName' android/app/build.gradle | grep -o '"[^"]*"' | tr -d '"')

aws configure set aws_access_key_id "$R2_ACCESS_KEY_ID"
aws configure set aws_secret_access_key "$R2_SECRET_ACCESS_KEY"

aws s3 cp "$APK" "s3://${R2_BUCKET}/app/seq1-sessions-${VERSION}.apk" \
  --endpoint-url "$ENDPOINT" --region auto \
  --content-type "application/vnd.android.package-archive"

aws s3 cp "$APK" "s3://${R2_BUCKET}/app/seq1-sessions-latest.apk" \
  --endpoint-url "$ENDPOINT" --region auto \
  --content-type "application/vnd.android.package-archive"

echo "Uploaded seq1-sessions-${VERSION}.apk and latest"
```

Then update `CURRENT_VERSION` in `admin-react/app/app/page.tsx` to match and push.

### ⚠️ Note on build-apk.yml

The GitHub Actions workflow currently runs `assembleDebug` — a debug build. Once the GitHub repo
is set up, update the workflow to run `assembleRelease` instead (it already has the signing config
in `build.gradle`). Debug APKs cause the Obtainium signing cert trap (see above).

---

## ✅ GitHub Actions — Active (set up 2026-05-09)

`git push origin main` triggers a build automatically. The workflow:
1. Stamps date-based version (YYYY.MM.DD.HHMM) into build.gradle
2. Signs with the release keystore (secrets set 2026-05-15)
3. Creates a GitHub Release with the APK attached
4. Obtainium reads from GitHub Releases to detect updates

**Signing secrets** (all set — do not need to redo):
`KEYSTORE_BASE64` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`

**Release keystore:** `~/seq1-sessions-app/seq1-sessions-release.keystore`
Generated 2026-05-15. Credentials in `~/seq1-healer/global.conf` under `[seq1_sessions_app]`.
**⚠️ Never delete this file — loss means you cannot update the installed app without users uninstalling.**

**Triggering a build manually (no code change):**
```bash
export GH_TOKEN=<mndwave_pat>  # see global.conf [github] mndwave_pat
gh workflow run build-apk.yml --repo mndwave/seq1-sessions-app
gh run list --repo mndwave/seq1-sessions-app --limit 3
```

## GitHub Actions secrets required

| Secret | Value source |
|---|---|
| `R2_ACCOUNT_ID` | Cloudflare R2 account ID |
| `R2_ACCESS_KEY_ID` | R2 API token (read+write on media bucket) |
| `R2_SECRET_ACCESS_KEY` | R2 API token secret |
| `R2_BUCKET` | `media` (the seq1 media R2 bucket) |

## Visual parity

Visual parity is **structural** — the app IS the website. No separate testing needed.

For Playwright regression tests: test `sessions.seq1.net` (web). The app shows the same thing.

The only visual difference: the Android system status bar overlays the top of the app. 
Set `android:windowSoftInputMode` and safe area insets if this causes layout issues.

## Adding this pattern to a new client project

1. `mkdir client-app && cd client-app && npm init -y`
2. `npm install @capacitor/core@5 @capacitor/cli@5 @capacitor/android@5 @capacitor/filesystem@5 --legacy-peer-deps`
3. `npx cap init "App Name" "com.client.app" --web-dir=www`
4. Edit `capacitor.config.ts`: set `server.url` to the live Next.js URL
5. `mkdir -p www && echo '<html><body>Loading...</body></html>' > www/index.html`
6. `npx cap add android`
7. Edit `android/app/src/main/AndroidManifest.xml`: add `RECORD_AUDIO`, `INTERNET` etc.
8. Copy `.github/workflows/build-apk.yml` and update secrets
9. Add R2 secrets to GitHub repo settings
10. Push — GitHub Actions builds and uploads the APK

## Android Icon — Complete Guide

### How Android icon resources work

Android uses two parallel icon systems:

| System | When used | Files |
|---|---|---|
| **Adaptive icon** (API 26+) | Android 8.0+ | `mipmap-anydpi-v26/ic_launcher.xml` → background layer + foreground layer |
| **Legacy PNG** | Android < 8.0 | `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` |

On Android 8+, the `mipmap-anydpi-v26/ic_launcher.xml` takes precedence over all density-specific PNGs for the launcher icon. If this XML is wrong, the PNG icons are completely ignored.

### The adaptive icon foreground trap (anti-pattern)

Capacitor generates the adaptive icon XML as:
```xml
<foreground android:drawable="@mipmap/ic_launcher_foreground"/>
```

This references `mipmap-{density}/ic_launcher_foreground.png` — the old Capacitor robot PNG files.

**If you only update the PNG files, the icon won't change on Android 8+ because the adaptive icon XML still points to the old PNGs.**

The correct approach: change the adaptive icon foreground to reference the vector drawable:
```xml
<!-- mipmap-anydpi-v26/ic_launcher.xml -->
<foreground android:drawable="@drawable/ic_launcher_foreground"/>
```

Then update `drawable-v24/ic_launcher_foreground.xml` with your actual icon design.

### Android launcher icon caching — the invisible blocker

Android launchers (Pixel Launcher, Samsung One UI Home, etc.) cache app icons in their own SQLite database, **independently of the APK**. This cache survives:
- APK uninstall + reinstall of the same package ID
- APK updates
- System reboots (usually)

**Symptom:** You install a new APK with a new icon, but the old icon is still showing on the home screen.

**This is not a build error.** The APK contains the correct icon — the launcher is serving a stale cache entry.

**Fix (on device):**
1. Settings → Apps → [your launcher app] → Storage → Clear Cache
2. Common launcher package names: `com.google.android.apps.nexuslauncher` (Pixel), `com.sec.android.app.launcher` (Samsung)
3. Or: restart the phone (usually clears launcher in-memory cache)
4. Or: long-press home screen → launcher settings → clear icon cache

**Verification (before blaming the build):** Open Settings → Apps → SEQ1 Sessions → the app info icon is read directly from the APK, not from the launcher cache. If the app info shows the correct icon but the home screen doesn't, it's a launcher cache issue.

### How to verify the icon inside the APK (without a device)

```bash
# Find the xxxhdpi launcher PNG inside the APK
for f in $(unzip -l app-release.apk | awk '{print $4}' | grep "\.png$"); do
  unzip -p app-release.apk "$f" > /tmp/chk.png 2>/dev/null
  dims=$(identify /tmp/chk.png 2>/dev/null | awk '{print $3}')
  [ "$dims" = "192x192" ] && echo "Found 192px: $f" && cp /tmp/chk.png /tmp/icon-verify.png && break
done
# Then visually inspect /tmp/icon-verify.png
```

### Signed release build (not debug)

Debug APKs work fine for personal use but show `(debug)` in the installer and have `webContentsDebuggingEnabled` active (Chrome DevTools can attach).

To build a signed release:
```bash
# Generate keystore once (store this file safely — loss = can't update the app)
keytool -genkey -v -keystore seq1-sessions-release.keystore \
  -alias seq1sessions -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=SEQ1 Sessions, OU=SEQ1, O=SEQ1, L=London, S=England, C=GB" \
  -storepass yourpassword -keypass yourpassword

# Configure in android/app/build.gradle signingConfigs block (see build.gradle)
# Then build:
./gradlew clean assembleRelease
# Output: android/app/build/outputs/apk/release/app-release.apk
```

Key differences vs debug:
- Proper certificate (not the debug keystore shared by all Android devs)
- `webContentsDebuggingEnabled: false` in capacitor.config.json
- Smaller APK (debug symbols stripped)
- `versionName` / `versionCode` matter for auto-update detection (bump versionCode on every release)

## Obtainium — update loop and silent install failure

### The debug→release signing trap

Android ties an installed app to the signing certificate of its APK. If you install a **debug APK** (signed with the default Android debug keystore) and then try to update via a **release APK** (signed with your own keystore), Android will **silently reject the install**.

The symptoms:
- Obtainium prompts for update on every check, forever
- The icon never changes despite "updating"
- The old APK version is still what's actually running

**Why it looks like it worked:** The Android package installer UI shows "install complete" in some cases, but the actual installation fails. Obtainium doesn't detect this and re-prompts on the next check.

**Fix (one-time migration):**
1. Settings → Apps → SEQ1 Sessions → Uninstall (full uninstall, not "disable")
2. Install the release APK fresh from `sessions.seq1.net/app`
3. Obtainium will now track the release-signed version and updates will work

**Prevention:** Never publish a debug APK to users. Once users have it installed, you can never push a release APK via update — they must manually uninstall first. Use `assembleRelease` from the start.

### Obtainium version comparison

Obtainium HTML source type:
1. Scrapes the source URL (e.g. `sessions.seq1.net/app`)
2. Finds APK links matching `apkFilterRegEx`
3. Extracts the version string using `versionExtractionRegEx`
4. Compares the extracted string against the installed APK's `versionName`

**The `versionName` in `build.gradle` must match what the regex extracts from the filename.**

| build.gradle `versionName` | APK filename | Regex extracts | Match? |
|---|---|---|---|
| `1.0.2` | `seq1-sessions-1.0.2.apk` | `1.0.2` | ✅ No prompt |
| `1.0` | `seq1-sessions-1.0.2.apk` | `1.0.2` | ❌ Always prompts |
| `2` | `seq1-sessions-1.0.2.apk` | `1.0.2` | ❌ Always prompts |

**Always keep `versionName` and the APK filename in sync.** The `CURRENT_VERSION` constant in `admin-react/app/app/page.tsx` and the `versionName` in `android/app/build.gradle` must be identical.

## Known anti-patterns

### ❌ Using `next export` for SSR apps

`next export` doesn't work if your app uses:
- API routes (`/api/...`)
- Server components
- `getServerSideProps`
- Middleware

None of these can be statically exported. `server.url` is the correct approach.

### ❌ Rebuilding APK on every web push

The whole point of Server URL mode is that you don't need to. If you add a path trigger 
on `app/**` or `src/**`, you're rebuilding unnecessarily. Only trigger on `android/**` and 
`capacitor.config.ts`.

### ❌ Using `webContentsDebuggingEnabled: true` in production

This allows any Chrome DevTools client to attach to the WebView remotely.
The workflow strips this flag on production builds. Don't merge code with it set to `true`
in a release branch.

### ❌ Putting signing keys in the workflow file

The debug build (`.apk`) is fine for internal distribution. For Play Store releases,
you need a signed release build. Store the keystore as a GitHub encrypted secret,
never in the repo.

### ❌ Assuming getUserMedia works the same as browser

Android WebView's `getUserMedia` requires the permission to be granted natively first
(via `AndroidManifest.xml RECORD_AUDIO`). The first launch triggers a native permission
dialog. If the user denies, `getUserMedia` will throw `NotAllowedError` permanently.
Handle this gracefully — show a settings deep-link to let the user re-grant.

## File structure

```
seq1-sessions-app/
├── capacitor.config.ts          ← Server URL + plugin config
├── www/                         ← Stub (not served — server.url overrides)
│   └── index.html
├── android/                     ← Generated by `npx cap add android`
│   └── app/src/main/
│       └── AndroidManifest.xml  ← Permissions — edit this, not the generated files
└── .github/workflows/
    └── build-apk.yml            ← Path-scoped trigger; uploads to R2
```

---

## Verified Canonical State — SEQ1 Sessions v2.0.0 (Capacitor 8 migration 2026-04-18)

**Capacitor 8 migration applied 2026-04-18:** All @capacitor/* packages updated from v7 to v8. Key
changes: `@capacitor/keyboard` added (keyboard UX improvements), `adjustMarginsForEdgeToEdge` removed
(no longer in Cap 8 API — CSS env(safe-area-inset-*) handles insets directly), `proguard-android.txt`
→ `proguard-android-optimize.txt` (AGP 9.0 compatibility). APK not yet rebuilt — pending GitHub
Actions setup (see GitHub section above). v1.0.2 was the last built APK.

This section documents the confirmed-correct configuration. All 7 layers PASS. Do not change these
without understanding the anti-pattern each one fixes.

### Layer 1 — Release signing (NOT debug)

**File:** `android/app/build.gradle`

```groovy
signingConfigs {
    release {
        storeFile file("../../seq1-sessions-release.keystore")
        storePassword System.getenv("KEYSTORE_PASSWORD") ?: ""
        keyAlias System.getenv("KEY_ALIAS") ?: ""
        keyPassword System.getenv("KEY_PASSWORD") ?: ""
    }
}
defaultConfig {
    versionCode 3
    versionName "2.0.0"
    ...
}
buildTypes {
    release {
        signingConfig signingConfigs.release
    }
}
```

Signing credentials are stored as GitHub Actions secrets (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
The keystore itself is base64-encoded as `KEYSTORE_BASE64` and decoded by the CI workflow before the Gradle build.

**Why this matters:** Android ties an installed app to its signing certificate. The debug keystore
(shared across all Android devs) and the release keystore are different certificates. If a user has
the debug APK installed, Android silently rejects the release APK as an update — the installer shows
"complete" but the old APK is still what's running. Obtainium then re-prompts on every check
forever (infinite update loop).

**Keystore location:** `seq1-sessions-app/seq1-sessions-release.keystore`
Generated with: `keytool -genkey -v -keystore seq1-sessions-release.keystore -alias seq1sessions -keyalg RSA -keysize 2048 -validity 10950 ...`
**KEEP THIS FILE. Loss = can never update the app for existing users without full uninstall.**

---

### Layer 2 — Version numbers in sync

Three places must have identical version strings:

| File | Key | Value |
|---|---|---|
| `android/app/build.gradle` | `versionName` | `"2.0.0"` |
| `capacitor.config.ts` | `APP_VERSION` | `'2.0.0'` |
| `admin-react/app/app/page.tsx` | `CURRENT_VERSION` | `'2.0.0'` |

**Why three places:** Obtainium scrapes the download page HTML for an APK link matching the regex
`seq1-sessions-([0-9]+\.[0-9]+\.[0-9]+)\.apk`, extracts the version string, then compares it
against the installed APK's `versionName` from `build.gradle`. If they differ even slightly
(`1.0` vs `1.0.2`), Obtainium always sees a newer version available → permanent update loop.

**`versionCode`** (`3`, an integer) is used by Android internally for update ordering. Increment it
every release even if the patch number doesn't change. It must only ever go up.

---

### Layer 3 — Adaptive icon XML (the critical layer)

**File:** `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

(Same content in `mipmap-anydpi-v26/ic_launcher_round.xml`)

**Why `@drawable/` not `@mipmap/`:** On Android 8.0+ (API 26+), `mipmap-anydpi-v26/ic_launcher.xml`
takes precedence over ALL density-specific PNGs for the launcher icon. Capacitor's default
template generates this XML with `@mipmap/ic_launcher_foreground` — which points to the old
robot PNG files in each `mipmap-{density}/` directory. Replacing those PNGs has ZERO effect
because the XML is never updated to point at the new files. The fix: point the foreground at
`@drawable/ic_launcher_foreground` (the vector XML in `drawable-v24/`).

**Anti-pattern that burned multiple debugging sessions:**
```xml
<!-- ❌ WRONG — overridden by the old Capacitor robot PNGs -->
<foreground android:drawable="@mipmap/ic_launcher_foreground"/>

<!-- ✅ CORRECT — uses the vector drawable you actually want -->
<foreground android:drawable="@drawable/ic_launcher_foreground"/>
```

---

### Layer 4 — Foreground vector drawable

**File:** `android/app/src/main/res/drawable-v24/ic_launcher_foreground.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- >_ terminal prompt — retro-teal on transparent (background layer is stone-900) -->

    <!-- ">" chevron -->
    <path
        android:fillColor="#2dd4bf"
        android:pathData="M16,25 L16,32 L37,54 L16,76 L16,83 L23,83 L47,57.5 L47,50.5 L23,25 Z" />

    <!-- "_" underline bar -->
    <path
        android:fillColor="#2dd4bf"
        android:pathData="M55,74 L92,74 L92,81 L55,81 Z" />
</vector>
```

**Important:** Android vector drawables do NOT support `<rect>` elements — only `<path>`, `<group>`,
`<clip-path>`, and `<gradient>`. The underline bar must be a `<path>` with explicit corner
coordinates, not `<rect x=... y=... width=... height=.../>`. AAPT build error if you use `<rect>`.

The 108×108dp canvas matches Android's adaptive icon safe zone spec. The design stays within
the inner 72×72dp circle to avoid clipping on rounded launchers.

---

### Layer 5 — Background colour

**File:** `android/app/src/main/res/values/ic_launcher_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#1c1917</color>
</resources>
```

`#1c1917` = Tailwind `stone-900` = the retro terminal background. Default was `#FFFFFF` (white).
The `@color/ic_launcher_background` reference in the adaptive icon XML maps to this value.

---

### Layer 6 — Legacy PNG fallback (Android < 8.0)

Five density buckets present and correct:

```
android/app/src/main/res/
├── mipmap-mdpi/ic_launcher.png       (48×48)
├── mipmap-hdpi/ic_launcher.png       (72×72)
├── mipmap-xhdpi/ic_launcher.png      (96×96)
├── mipmap-xxhdpi/ic_launcher.png     (144×144)
└── mipmap-xxxhdpi/ic_launcher.png    (192×192)
```

These are used on Android 7.1 and below where adaptive icons aren't supported. They're also used
in the app drawer on some Android 8+ launchers that fall back to the PNG for certain display modes
(e.g. notification badge, recent apps thumbnail). They should show the `>_` design too.

**Verify the xxxhdpi PNG (192×192) inside any built APK:**
```bash
for f in $(unzip -l app-release.apk | awk '{print $4}' | grep "\.png$"); do
  unzip -p app-release.apk "$f" > /tmp/chk.png 2>/dev/null
  dims=$(identify /tmp/chk.png 2>/dev/null | awk '{print $3}')
  [ "$dims" = "192x192" ] && echo "Found 192px: $f" && break
done
```

---

### Layer 7 — Obtainium configuration

Obtainium scrapes `sessions.seq1.net/app` (the download page) to detect new versions.

| Setting | Value | Why |
|---|---|---|
| Source URL | `https://sessions.seq1.net/app` | SSR-rendered HTML contains APK links |
| Source type | HTML | Not GitHub, not F-Droid |
| APK link regex | `seq1-sessions-([0-9]+\.[0-9]+\.[0-9]+)\.apk` | Matches versioned filenames |
| Version extraction | Same regex, group 1 | Extracts `1.0.2` from filename |

One-tap import via: `https://sessions.seq1.net/app/obtainium.json`

**Why SSR matters:** Obtainium fetches the HTML source and runs the regex against it. If the page
were client-rendered (SPA), Obtainium would see an empty shell with no APK links. The Next.js page
is server-rendered, so the `<a href="...seq1-sessions-1.0.2.apk">` tag is present in the initial
HTML. This is why the source type is HTML, not JavaScript.

**Alternative import path** (for users without Obtainium set up):
```json
// sessions.seq1.net/app/obtainium.json
{
  "id": "net.seq1.sessions",
  "url": "https://sessions.seq1.net/app",
  "author": "SEQ1",
  "name": "SEQ1 Sessions",
  "additionalSettings": "{\"filterRegExp\":\"seq1-sessions-([0-9]+\\\\.[0-9]+\\\\.[0-9]+)\\\\.apk\"}"
}
```

---

## Android Launcher Icon Cache — Definitive Diagnosis

After a fresh install of the release APK (confirmed by permission re-prompts on first launch),
if the home screen still shows the old icon, this is **exclusively a launcher cache issue**.
The APK is correct. This is not a build problem.

### Why it happens

Android launchers (Pixel Launcher, One UI Home, etc.) maintain their own icon cache in a SQLite
database stored inside the launcher app's private data directory. This cache:

- Survives APK uninstall (the launcher retains the cached bitmap)
- Survives reinstall of the same package ID
- Sometimes survives a full device restart

The launcher checks the cache before opening the APK to extract the icon. If the package name
matches a cached entry, it uses the cached bitmap without touching the APK.

### Proof the APK is correct (check before blaming the build)

Settings → Apps → SEQ1 Sessions → the icon shown in the app info screen is read **directly from
the APK**, bypassing the launcher cache. If this shows `>_`, the APK is correct and it's a
launcher cache issue.

### How to fix (on device)

**Option A — Clear launcher cache (recommended):**
1. Settings → Apps → (find your launcher, e.g. "Pixel Launcher" or "One UI Home")
2. Storage → Clear Cache
3. Return to home screen — icon should update immediately

Common launcher package names:
- Pixel / stock Android: `com.google.android.apps.nexuslauncher`
- Samsung: `com.sec.android.app.launcher`
- OnePlus: `net.oneplus.launcher`

**Option B — Restart phone:**
Most launchers reload their icon cache on boot. Usually resolves within one restart.

**Option C — Long-press home screen → launcher settings → clear icon cache**
(Not available on all launchers)

### Resolution timeline

The launcher cache will self-correct through one of:
- First device restart after the fresh install
- Manual cache clear as above
- Some launchers re-validate their cache periodically (days, not weeks)

**This requires no code changes.** The APK at `media.seq1.net/app/seq1-sessions-1.0.2.apk`
has been verified to contain the correct `>_` icon at all layers.

---

## Obtainium Infinite Update Loop — Root Cause & Prevention

### Root cause: debug → release signing cert change

When Obtainium installs a release APK to replace a debug APK (different signing certificate),
Android silently rejects the installation. The package installer UI may show "complete" but
the old APK is still running. Obtainium has no way to detect this failure, so on the next
check it sees the installed version doesn't match the available version → prompts to update
again. This repeats forever.

### One-time fix (migration from debug to release)

1. Settings → Apps → SEQ1 Sessions → Uninstall (full uninstall, not disable)
2. In Obtainium: remove the SEQ1 Sessions entry
3. Go to `sessions.seq1.net/app` in the browser
4. Download and install the APK directly
5. Re-add SEQ1 Sessions in Obtainium
6. Future Obtainium updates will work (consistent release signing)

### Prevention going forward

The release keystore (`seq1-sessions-release.keystore`) must be used for every APK build, forever.
If the keystore is lost, existing users can never receive updates via Obtainium (or any other
update mechanism) — they must uninstall and reinstall manually.

**Store the keystore file safely. It is NOT committed to git (`*.keystore` is in `.gitignore`).
The keystore is stored as a base64-encoded GitHub Actions secret (`KEYSTORE_BASE64`). The
credentials are stored as `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets. Loss of the
keystore = can never update the app for existing users without full uninstall.**

### Version number drift loop

If `versionName` in `build.gradle` (`1.0.2`) doesn't exactly match what Obtainium's regex
extracts from the filename (`1.0.2` from `seq1-sessions-1.0.2.apk`), Obtainium always thinks
there's a newer version. They must be identical strings.

| `versionName` | APK filename | Obtainium behaviour |
|---|---|---|
| `1.0.2` | `seq1-sessions-1.0.2.apk` | ✅ Sees match, no update prompt |
| `1.0` | `seq1-sessions-1.0.2.apk` | ❌ Always prompts (strings differ) |
| `2` | `seq1-sessions-1.0.2.apk` | ❌ Always prompts |

---

## Amber / NIP-55 — APK Auth Loop Anti-Pattern

### The problem

In the APK, `window.nostr` is the Amber bridge. Every call to `window.nostr.signEvent()` shows
a native Android approval dialog. The sessions UI makes 8+ authenticated API calls on page load.
Result: Amber pops approval dialogs in a continuous loop — you approve one, the next fires
immediately, the page never finishes loading.

### The fix

**File:** `admin-react/lib/nostr-client-auth.ts`

```typescript
// Detect APK context — window.Capacitor is set by the Capacitor WebView layer
const isCapacitorContext = typeof window !== 'undefined' && !!(window as any).Capacitor

if (!isCapacitorContext && typeof window !== 'undefined' && (window as any).nostr) {
  // Use browser extension (e.g. Alby) — safe, no popup per request
  try {
    const signedEvent = await (window as any).nostr.signEvent(unsignedEvent)
    ...
  }
}

// In APK context, fall through to nsec-based signing (no popup, no Amber)
```

**Why NIP-55 (Amber) is wrong for NIP-98 HTTP auth:** NIP-55 is designed for high-trust operations
(signing posts, managing follows) where a user approval dialog is appropriate. NIP-98 generates
a short-lived signed event as a bearer token for HTTP auth — it's a technical mechanism, not a
user action. Showing an Amber dialog for every API request is both the wrong UX and practically
unusable. In APK context, the bootstrapped nsec (stored in localStorage by the login flow) is
used directly for HTTP auth signing. Amber is still used for Nostr identity operations.

---

## Release Checklist — Before Publishing a New APK Version

When bumping from e.g. `1.0.2` → `1.1.0`:

- [ ] Update `versionName` in `android/app/build.gradle` (e.g. `"1.1.0"`)
- [ ] Increment `versionCode` in `android/app/build.gradle` (e.g. `3`)
- [ ] Update `APP_VERSION` in `capacitor.config.ts` (e.g. `'1.1.0'`)
- [ ] Update `CURRENT_VERSION` in `admin-react/app/app/page.tsx` (e.g. `'1.1.0'`)
- [ ] Verify all three match exactly
- [ ] Push to `main` branch → GitHub Actions builds + uploads to R2
- [ ] Verify APK appears at `https://media.seq1.net/app/seq1-sessions-1.1.0.apk`
- [ ] Verify download page at `sessions.seq1.net/app` shows new version number
- [ ] Verify Obtainium detects update (scrape test: `curl -s https://sessions.seq1.net/app | grep seq1-sessions`)

**DO NOT** publish a debug APK. Once users have it installed, updates won't work until they
manually uninstall. The GitHub Actions workflow builds `assembleRelease` with the keystore
from the `BUILD_KEYSTORE_BASE64` secret — this produces the correctly signed release APK.

---

## Summary of Anti-Patterns Resolved (2026-04-17)

| Anti-pattern | Symptom | Root cause | Fix |
|---|---|---|---|
| `@mipmap/ic_launcher_foreground` in adaptive XML | Icon unchanged despite PNG regeneration | Adaptive XML overrides PNGs on Android 8+; still pointing at old robot PNGs | Changed to `@drawable/ic_launcher_foreground` |
| `<rect>` in vector drawable | AAPT build error | Android VectorDrawable doesn't support `<rect>` | Replaced with `<path>` coordinates |
| Background `#FFFFFF` (white) | White background behind `>_` design | Capacitor default; never changed | Changed to `#1c1917` (stone-900) |
| Debug APK distributed to users | Obtainium update loop forever | Signing cert mismatch; Android silently rejects update | Migrate: full uninstall → fresh release install |
| `versionName` mismatch | Obtainium always prompts to update | Obtainium compares extracted filename version vs installed versionName | Keep all three version constants identical |
| `window.nostr` used in APK context | Amber approval dialog loop on every page load | NIP-55 designed for user-facing ops, not HTTP auth tokens | Skip `window.nostr` when `window.Capacitor` is set |
| Launcher icon cache | Icon still old after confirmed fresh install | Launcher caches icon bitmaps independently of APK | Clear launcher app cache or restart device |

---

## App Icon — S1s SEQ1 Branding (updated 2026-05-15)

### Design

- **Text:** `S1s` — "S1" italic (matching the S1 badge in boldthin.gs admin), lowercase "s" upright (for Sessions)
- **Font:** Poppins 800 — same as the SEQ1 S1 assignee badge
- **Colour:** `#f0e6c8` warm cream, dark bg `#0F0F15`
- **Glow:** `text-shadow: 0 0 8px rgba(240,230,200,0.55), 0 0 22px rgba(240,230,200,0.22), 0 0 50px rgba(240,230,200,0.08)`
- **Safe for circle crop:** text sits within the inscribed circle at all densities

### Source files

- Design HTML: `/tmp/seq1sessions-final.html` (regenerate from here if changes needed)
- Reference PNG: `~/seq1-healer/seq1sessions-final-512.png` (512×512 master)

### Regenerating icons (if you need to update the design)

```bash
# 1. Edit /tmp/seq1sessions-final.html, start a local server, screenshot via Playwright
# 2. Copy result to ~/seq1-healer/seq1sessions-final-512.png

# 3. Generate all mipmap sizes
RES=~/seq1-sessions-app/android/app/src/main/res
SRC=~/seq1-healer/seq1sessions-final-512.png

for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  case $density in mdpi) s=48;; hdpi) s=72;; xhdpi) s=96;; xxhdpi) s=144;; xxxhdpi) s=192;; esac
  convert "$SRC" -resize ${s}x${s} "$RES/mipmap-${density}/ic_launcher.png"
  convert "$SRC" -resize ${s}x${s} "$RES/mipmap-${density}/ic_launcher_round.png"
done

for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  case $density in mdpi) s=108;; hdpi) s=162;; xhdpi) s=216;; xxhdpi) s=324;; xxxhdpi) s=432;; esac
  convert "$SRC" -resize ${s}x${s} "$RES/mipmap-${density}/ic_launcher_foreground.png"
done

# 4. Commit and push — GitHub Actions builds the APK
cd ~/seq1-sessions-app
git add android/app/src/main/res/mipmap-*/ic_launcher*.png
git commit -m "feat(icon): ..."
git push origin main
```

### Adaptive icon architecture

Android 8+ uses the adaptive icon defined in `mipmap-anydpi-v26/ic_launcher.xml`:
- **Background layer:** `@color/ic_launcher_background` → `#0F0F15` (values/ic_launcher_background.xml)
- **Foreground layer:** `@mipmap/ic_launcher_foreground` → the resized PNG at each density

This replaced the old `@drawable/ic_launcher_foreground` vector (which was the `>_` terminal prompt).
Legacy Android (<8) uses `mipmap-*/ic_launcher.png` directly.
