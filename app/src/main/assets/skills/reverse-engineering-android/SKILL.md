---
name: reverse-engineering-android
description: Reverse-engineer Android APKs and web/app APIs with evidence-first methods — aapt2 manifest and resource inspection, jadx/apktool decompilation, native-library analysis, runtime inspection, and API traffic analysis. Legal, authorized use only.
version: 1.0.0
author: User custom skill (written for Hermes)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reverse-engineering, android, apk, decompile, api, jadx, apktool, frida, security]
    related_skills: [reverse-engineering, local-android-dev, systematic-debugging, local_verify_before_recommending]
---

# Reverse Engineering — Android APKs and APIs

## Legal use only

- Reverse-engineer only what you are allowed to: your own apps, apps you
  have permission to analyze, open-source apps, CTF/learning targets.
- NEVER lift code from a proprietary or restrictively licensed app into
  another product. A license that bars distributing a component inside
  another product (or bars white-labelling) is a hard stop, even if the code
  is readable. Behaviour and ideas may be re-implemented; the code itself
  must be original or permissively licensed.
- Never use reversing to bypass a license check, a payment, or a device lock
  on someone else's product. Never exfiltrate credentials, tokens, or user
  data found inside an app.

## APK analysis — static

Work on a COPY of the APK, never the installed app's live data.

1. **Identify**: `aapt2 dump badging <app.apk>` — package name, versionCode,
   versionName, permissions, launch activity, SDK targets.
2. **Manifest and resources**: `aapt2 dump xmltree <app.apk> AndroidManifest.xml`
   for the manifest; list assets with `unzip -l`. Note every permission and
   every exported component (activity/service/receiver/provider).
3. **Signing**: `apksigner verify --print-certs <app.apk>` — signer
   certificate fingerprints; compare against the known release signer before
   trusting an update (install-over only when the signer matches).
4. **Decompile**: `apktool d <app.apk>` for resources/smali, or
   `jadx <app.apk>` for Java source. Read the code that matters (network
   layer, auth, storage, update checks) — do not dump the whole tree into a
   report.
5. **Native libraries**: list `lib/<abi>/*.so`; inspect with
   `readelf -h/-d`, `strings`, `nm -D`. Check which ABIs ship
   (arm64-v8a only vs armeabi-v7a) and whether the bundle matches the
   reference release (assets + native libs parity — a hand-built APK missing
   the runtime bundle or .so files will crash on launch).
6. **Strings and secrets**: `strings` over the APK and .so files. Report the
   PRESENCE of a hardcoded secret (endpoint, key pattern) — never reproduce
   the secret value itself in any report, log, or message.

## APK analysis — runtime

- `adb logcat` for the app's own logs while reproducing a behaviour; filter
  by the app's PID/package.
- Frida for tracing method calls and inspecting live objects — only on your
  own device and your own/authorised apps.
- Network: route the device through a proxy (mitmproxy) to record API
  traffic; note certificate pinning when the app refuses the proxy, and do
  not break pinning on apps you do not own.

## Web and app API inspection

- Use browser devtools / curl to record real requests and responses. Map the
  endpoints, methods, auth scheme, and response schemas into an interface
  map before writing any client code.
- Discover the API from the app's own traffic and public docs, never by
  guessing URLs. A guessed endpoint is not evidence.
- Record rate limits and auth failures exactly as observed (status code,
  body, headers) — they decide whether a route is usable.

## Evidence-first workflow

- State a hypothesis, then test it with one concrete check (a command, a
  decompiled method, a captured request). Record the check and its result.
- Name what could NOT be verified instead of folding it into a conclusion.
- Prefer the general `reverse-engineering` skill (evidence ladders, interface
  maps, falsifiable hypotheses) for the overall method; this skill adds the
  Android/APK-specific tools and checks.
- Keep the final report short: what the app does, how (with file/method
  references), what is still unknown, and what must not be copied.
