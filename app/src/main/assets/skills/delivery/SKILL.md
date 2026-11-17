---
name: delivery
description: How finished work reaches the user - deliver the thing itself, never only code, a screenshot, or a description.
---

# Delivery

How finished work reaches the user on Telegram. A build is not delivered until the user can open it on his phone. Never deliver only code, only a screenshot, or only a description — always deliver the thing itself in the form below.

## Website — always send the DIRECT LINK

- Serve the built site on this server so the user's phone can reach it: `npm run preview -- --host 0.0.0.0 --port <port>` (or the framework's equivalent preview/dev server bound to `0.0.0.0`).
- Open the port in BOTH firewalls, or the link will not open on his phone:
  1. Server firewall: `sudo iptables -I INPUT <n> -p tcp -m tcp --dport <port> -j ACCEPT` (insert BEFORE the final REJECT rule), then `sudo netfilter-persistent save` so it survives reboot.
  2. Oracle Cloud security list: add an ingress rule for TCP `<port>` from `0.0.0.0/0` (OCI CLI: `oci network security-list update` with the full existing rule list plus the new rule — never replace the list with only the new rule).
- Verify the link returns 200 from OUTSIDE the server (curl from another machine, not localhost) before sending it.
- Send the plain link in Telegram, on its own line, nothing else on that line: `http://130.210.34.84:<port>/`. The user copies it and it must open on his phone.
- Never send just the code, just a screenshot, or just a description instead of the link. A screenshot may accompany the link, never replace it.

## App (Android APK) — always send the APK as a FILE on Telegram

- Build the APK first, then send the APK FILE itself in Telegram (document attachment), not just a link and not just code.
- PREREQUISITE — this server has NO Java, NO Android SDK, and NO Gradle installed, so it cannot build an APK yet. One-time setup is required before the first APK build: install JDK 17, the Android SDK (cmdline-tools, platform-tools, build-tools, one platform), and Gradle (or the Gradle wrapper from the project). Verify with `java -version` and `sdkmanager --list` before claiming the setup is done.
- A debug APK is fine for testing. A release APK needs the user's keystore — never invent, regenerate, or share keystore passwords; ask the user for the keystore only through the secure channel he uses for secrets.
- Verify the APK before sending: package id, versionCode, versionName, SHA-256, and that it is signed (`apksigner verify`). Never send an unsigned or unverified APK.
- If the Telegram bot cannot send files, the fallback is a direct download link to the APK file served from this server (same port procedure as websites), and say so plainly — do not pretend the file was sent.

## Telegram file sending

- The Hermes Telegram bot CAN send files: the Telegram plugin supports document attachments, so prefer sending the APK as a file.
- If a file send ever fails, retry once, then fall back to the direct download link and say which one was used.
