# Relaydo privacy notes

Independent fork of [pppscn/SmsForwarder](https://github.com/pppscn/SmsForwarder), retaining upstream attribution and `LICENSE`. Application ID: **`tools.franks.relaydo`**, so it can be installed alongside `cn.ppps.forwarder`. Source namespaces remain unchanged intentionally. No automatic updates from upstream; the app updates itself only from this fork's own GitHub Releases (see [In-app updater](#in-app-updater)).

## Languages

English is the Android fallback and first-run language. Settings offers English, Nederlands and Follow system; explicit legacy Chinese selections migrate to English before resource-caching singletons initialize. Chinese locale resources and language controls are removed. Comments, documentation, errors and examples are translated into English, with Dutch application resources. Stored rule syntax and template identifiers stay language-independent. Chinese is a language, not a tracking mechanism.

Legacy Chinese template aliases and localized mail-provider values are no longer supported. Recreate old Chinese templates/settings using the current English identifiers and reselect the mail provider before forwarding; saved user data is not deleted or silently rewritten. Bark automatic code detection now recognizes English and Dutch keywords; explicit copy templates still work. Third-party DEX/resource text is inventoried separately by the APK language gate. Charset tables are functional data, not UI translations. Git history and LICENSE are preserved.

## Privacy architecture and traffic inventory

| Surface | Fork behavior |
| --- | --- |
| Umeng common/asms/uyumao SDK, app initialization, fragment page analytics | Removed, including build keys and ProGuard rules. APK gate checks DEX and native libraries. |
| Upstream update/preview API and APK downloader | Removed with XUpdate dependency, helpers and activity; `xupdate.ppps.cn` stays banned by the privacy gate. Replaced, at the owner's request, by the fork's own updater below. |
| In-app updater (owner's request) | Reads the public release list of `frankhommers/relaydo` on GitHub at most about once a day and on **Check now**. See [In-app updater](#in-app-updater). Off switch: About → Updates → *Check automatically once a day*. |
| Downloaded FRPC `libgojni.so` and writable-storage native loader | Downloader and activation removed. `FrpclibInited` is immutable false. Native payload excluded from APK; FRPC unavailable, not replaced with another unverified binary. AAR bindings remain for legacy screens/data compatibility. |
| Phone-area lookup | Local `Unknown Area`; phone numbers never sent to a lookup provider. |
| Public-IP discovery | **At the owner's explicit request:** `https://ip.hommers.it/`, replacing ipify. Existing network-change triggers retained; one bounded HTTPS GET per worker execution, no redirects, 5-second connect/read timeouts, literal-address validation and bounded response. Only the returned address family is populated; simultaneous IPv4 and IPv6 discovery is not promised. Server sees source IP/timing, not message payloads or device IDs. |
| Privacy notice / startup tips | Bundled local text/JSON; no remote fetching or images. |
| Donation feed / WeChat mini-program image | Remote defaults and UI actions removed/hidden. |
| Markdown view | No remote document fetch; WebView network loading blocked before local markdown rendering (including remote stylesheet/font/image/script requests). |
| General HTTP library default base URL | Loopback sentinel instead of Gitee. Actual forwarding uses explicit configured URLs. |
| Reverse geocoding | Removed: coordinates are not sent to the Android geocoder. Address-keyword location conditions cannot resolve addresses; coordinate conditions remain. OS location providers may independently use networking. |
| Forwarding providers | Retained as opt-in configured channels, including Chinese providers. Email/SMTP, HTTP(S), MQTT, sockets, Telegram, Bark, Gotify, DingTalk, Feishu, WeCom, ServerChan, PushPlus and SMS may send user-selected data. |
| Client/server/automation | Retained; user-selected addresses, local API, WOL and automation can generate traffic. Review credentials, API exposure and permissions before enabling. |
| Web/help/project links | Only user navigation loads them; websites can load external subresources. No embedded web page is loaded at startup. Upstream GitHub attribution link retained. |
| App icons / attachments / exports | App list icons use installed-app data. Local imports/exports remain; exported backups and debug logs may contain secrets. |

The approval for the owner's IP endpoint is explicit, not a claim of zero background traffic. DNS, TLS, operating-system services and opted-in destinations remain. Static scanning is a regression guard, not a proof that every third-party dependency is telemetry-free. No blanket network firewall is installed: that would break configured forwarding. Existing optional certificate-validation bypass settings for user-configured servers are not broadened; do not enable them on untrusted networks.

## Email encryption

With S/MIME, OpenPGP or OpenKeychain selected, a recipient whose email cannot be encrypted (no key, a key that cannot be read or used, or an encryption error) gets **no** email by default; the delivery log says why per recipient, and the other recipients still get theirs. The target option *Send unencrypted if encryption fails* (default off, also for targets saved before it existed) sends those recipients a normal readable email instead, marked "sent UNENCRYPTED" in the log. A mail-server error after encryption is retried as the same encrypted message and never becomes an unencrypted copy. A configured own signing key that cannot be used (wrong file or password) stops the delivery instead of sending unsigned. The *Unencrypted* mode is unchanged.

## In-app updater

At the owner's explicit request the app updates itself from GitHub Releases of this fork. The fork is public.

**What it contacts.** Only these, over HTTPS with normal certificate validation:
1. `https://api.github.com/repos/frankhommers/relaydo/releases?per_page=10` (the release list; releases are pre-releases, so the "latest release" endpoint is not used);
2. `https://github.com/frankhommers/relaydo/releases/download/build-N/smsforwarder-update.json` (the small manifest of a newer release, at most three per check);
3. `https://github.com/frankhommers/relaydo/releases/download/build-N/SmsForwarder-N-debug.apk`, only when a newer valid version exists. GitHub redirects this to a `*.githubusercontent.com` download host; redirects to any other host, or to plain HTTP, are refused.

**How often.** On *Check now* in About, and automatically about once a day (WorkManager periodic work, only with a network connection, survives reboot; the worker also refuses to contact GitHub when the previous automatic check started less than 24 hours ago, also after the clock is changed). Offline: nothing is sent and it retries quietly later. Any automatic check that started, including one that failed halfway or was rate-limited by GitHub (60 unauthenticated requests per hour per IP address), counts as that day's check. Nothing runs when automatic checks are off, except a manual *Check now*.

**What GitHub can see.** Your IP address, the time, and that someone fetched this repository's release list and assets (so: that this app checked for updates). The requests carry no account, token, cookie, device identifier, installed version or any app data; only the default headers of the HTTP library (OkHttp's `User-Agent: okhttp/3.12.13`, `Accept-Encoding`, `Host`, `Connection`). Nothing is uploaded.

**Before installing, all of these must hold** (otherwise the download is deleted and the specific reason is shown in About): the file size and SHA-256 equal the manifest; the APK's package is `tools.franks.relaydo`; its versionCode is higher than the installed one and equals the manifest; its signing certificate equals the certificate of the *running* app, read from Android's PackageManager (not from the manifest, not hardcoded). Releases without a valid manifest are ignored.

**Installing.** Through Android's package installer. Android 8 and later asks once to allow *Install unknown apps* for this app. On Android 12 and later the app asks Android to update itself without confirmation (`UPDATE_PACKAGES_WITHOUT_USER_ACTION`); Android decides whether that is allowed (it requires the app to be the installer of record of the installed copy and a recent enough target SDK); otherwise the normal system confirmation appears, or a notification that opens it when the app is in the background. Android 4.4 cannot install from within the app and only reports that an update exists. After an update Android restarts the forwarding service through `MY_PACKAGE_REPLACED`, the same way as after boot.

**Turn it off.** About → Updates → untick *Check automatically once a day*. The daily job is cancelled; *Check now* still works when you press it. The builds are debug test builds signed with the same debug key as earlier installs.

**Release format.** Tag `build-N` (pre-release), universal versionCode `100000 + N`, assets `SmsForwarder-N-debug.apk`, `smsforwarder-update.json` (`format`, `packageName`, `buildNumber`, `versionCode`, `versionName`, `apk`, `apkSize`, `apkSha256`, `certSha256`, `testBuild`) and `SmsForwarder-N-debug.apk.sha256`. Produced by `python3 scripts/release.py build`, which refuses to stage an APK whose signing certificate SHA-256 is not `546f8d56f4f13beec65be2ff882cef169e87f03a6c7a715bdef79d060aeec123`; uploaded separately by `python3 scripts/release.py publish --build-number N`.

## Build and verification

Use JDK 17, Android SDK 33 / build-tools 33.0.1 and the checked-in Gradle 7.3.3 wrapper:

```bash
source ~/toolchains/android/env.sh  # if using this machine's existing toolchain
python3 scripts/rebuild_english_dependencies.py  # reviewed pinned-source replacements
bash ./gradlew clean :app:assembleDebug :app:testDebugUnitTest \
  --no-daemon --max-workers=4 -PisNeedPackage=false -PisNeedClean=false \
  '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8'
python3 scripts/check_privacy.py
python3 scripts/check_privacy.py --apk /absolute/path/to/universal-debug.apk
python3 scripts/check_languages.py
# APK inventory requires Python with androguard installed (tested with androguard 4.x).
python3 scripts/check_languages.py --apk /absolute/path/to/universal-debug.apk \
  --report /absolute/path/to/language-inventory.json --strict
```

Debug builds use the Android debug key, not upstream release credentials. Do not distribute a debug APK as a production release. Release signing still requires your own supplied keystore. Build dependencies may contact Google/Maven/JitPack and existing build-time mirrors; these are not runtime app services. The old toolchain and snapshot UI dependency are retained rather than disguising a major dependency migration as a privacy change.

Regression coverage includes disabled SDK/endpoints/native loader, English fallback and supported locales, unchanged license, local privacy/tips, approved IP request configuration and negative scan fixtures. JVM tests exercise literal IPv4/IPv6 parsing, malformed/oversized responses, timeout configuration, no redirects, close/disconnect and failures. APK scan rejects known telemetry classes/native libraries and FRPC native payloads. Device SMS delivery and every optional provider require real accounts/permissions and are not implied by build success.

## Dependency-language cleanup

The first-party source and resources have no Chinese text, including legacy aliases. Build-time `resConfigs` keeps only default/English/Dutch Android resources; 35 upstream default-language strings are overridden in English and Dutch. Java cron/Paho/Joda ResourceBundle translations require separate packaging exclusions; cron descriptions use English. Two upstream layouts with hardcoded labels are overridden. Resource-key, format-placeholder and template-token parity are checked automatically.

The remaining 240 dependency DEX natural-language strings are removed by compiling 56 reviewed source files from the original pinned releases and substituting their class entries in 12 AAR modules. See [source receipts, licenses, exact rebuild recipe and behavior notes](third_party/english-dependencies/README.md). No dependency versions were upgraded, no compiled strings were hex-edited, and no language gate was disabled. Gradle refuses upstream fallback for those modules and verifies generated artifact hashes before building. The APK gate now rejects dependency prose even without `--strict`; exactly 32 functional Unicode/URL/regex entries are preserved through an exact-hash-and-owner allowlist.

English diagnostics replace upstream-only Chinese diagnostics; Dutch application UI/resources remain. Lunar calculations are unchanged while labels use numbers/English/romanized names. DeviceCompat marketing-friendly aliases are replaced with manufacturer/model diagnostic identifiers; device/OS/permission detection is unchanged. External device names, user text and provider responses are not censored.

Current local evidence is in ignored `privacy-verification/dependency-cleanup/`, including the actual clean test APK, full language inventory, build/JUnit/privacy/signature evidence, dependency source receipts and emulator Dutch persistence. The earlier `english-dutch-candidate/` is superseded, not silently overwritten. Dependency raster contact sheets cover 212 unique images with no visible Chinese found; this is not exhaustive OCR or native-library text decoding. Real SMS delivery, provider-account testing and new packet capture remain unperformed.

## License ambiguity

`LICENSE` is preserved byte-for-byte. GitHub reports BSD-2-Clause metadata, but upstream README also states noncommercial restrictions. These statements are inconsistent; this fork does not resolve the ambiguity, remove attribution or grant new rights. Seek clarification before commercial distribution. Upstream legal notices and the full user-agreement asset are retained in English, with original wording in the upstream Git history; the app displays a separate fork usage notice and accurate local privacy notice.
