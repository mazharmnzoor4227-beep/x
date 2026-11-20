package com.mazharansari.x

import com.mazharansari.x.linux.NativeSpawn
import com.mazharansari.x.local.LlamaNative
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Regression test for the 2026-11-20 Developer-tools outage: pocket_spawn.c declared the JNI
 * functions under the OLD package (Java_com_mazharansari_axion_linux_NativeSpawn_*), so the JVM
 * never found them and every toolchain install failed with "No implementation found for int[] …".
 * The library itself loaded fine, so the "library missing" error never fired — the failure only
 * showed up when the first linux.run call tried to spawn. The expected symbol names are derived
 * from the real Kotlin declarations, so a future package or method rename fails this test instead
 * of failing on the user's phone. Plain JVM test: it never calls the native methods, so no .so
 * is needed (reading the class literal and its declared methods does not run NativeSpawn's init).
 */
class NativeSpawnJniTest {
    private fun cSource(name: String): File {
        val here = File(System.getProperty("user.dir"))
        val dirs = sequenceOf(here) + generateSequence(here.parentFile) { it.parentFile }.take(4)
        // error() returns Nothing, so the Elvis keeps the File type (JUnit's fail() is Unit, which would not compile here).
        return dirs.flatMap { d -> listOf(File(d, "src/main/cpp/$name"), File(d, "app/src/main/cpp/$name")) }
            .firstOrNull { it.isFile }
            ?: error("$name not found under ${here.absolutePath} (looked in src/main/cpp and app/src/main/cpp up the tree)")
    }

    @Test fun jniSymbolsMatchKotlinDeclarations() {
        val src = cSource("pocket_spawn.c").readText()
        // Expected prefix from the real Kotlin class: com.mazharansari.x.linux.NativeSpawn.
        val jniPrefix = "Java_" + NativeSpawn::class.java.name.replace('.', '_') + "_"
        assertEquals("Java_com_mazharansari_x_linux_NativeSpawn_", jniPrefix)
        // The native methods the Kotlin object actually declares.
        val methods = NativeSpawn::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isNative(it.modifiers) }.map { it.name }.sorted()
        assertEquals(listOf("kill", "spawn", "waitFor"), methods)
        for (m in methods) assertTrue("missing JNI symbol $jniPrefix$m in pocket_spawn.c", src.contains("$jniPrefix$m"))
        assertFalse("stale Axion-package JNI symbols in pocket_spawn.c", src.contains("Java_com_mazharansari_axion_"))
    }

    @Test fun llamaJniSymbolsMatchKotlinDeclarations() {
        // Same bug class as pocket_spawn.c (2026-11-20): axion_llama.cpp declared the 9
        // LlamaNative functions under the OLD package, so loading a local model threw
        // "No implementation found for ..." on the phone. Derived from the real Kotlin
        // declarations, so a future package or method rename fails here, not on-device.
        // Reading the class literal and its declared methods does not run LlamaNative's
        // init block, so no .so is needed.
        val src = cSource("axion_llama.cpp").readText()
        val jniPrefix = "Java_" + LlamaNative::class.java.name.replace('.', '_') + "_"
        assertEquals("Java_com_mazharansari_x_local_LlamaNative_", jniPrefix)
        val methods = LlamaNative::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isNative(it.modifiers) }.map { it.name }.sorted()
        assertEquals(listOf("applyTemplate", "contextSize", "free", "generate", "init", "lastError", "load", "stop", "systemInfo"), methods)
        for (m in methods) assertTrue("missing JNI symbol $jniPrefix$m in axion_llama.cpp", src.contains("$jniPrefix$m"))
        assertFalse("stale Axion-package JNI symbols in axion_llama.cpp", src.contains("Java_com_mazharansari_axion_"))
    }
}
