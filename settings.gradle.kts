pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // JitPack: sherpa-onnx (Piper engine) publishes its Android AAR there
    // (com.github.k2-fsa:sherpa-onnx), not on Maven Central.
    repositories { google(); mavenCentral(); maven { url = uri("https://jitpack.io") } }
}
rootProject.name = "X"
include(":app")
