plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // New series (2026-11-17): google-services plugin NOT applied — no Firebase project yet.
    // No code uses Firebase (no Crashlytics/FCM); re-add with the X project's google-services.json later.
    // Do NOT reuse the donor axion-d5461 config.
}
