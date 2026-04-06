package io.github.devcrocod.simdjson

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

class NativeLibLoaderTest {

    @Test
    fun `concurrent ensureLoaded from multiple threads does not crash`() {
        val threadCount = 16
        val barrier = CyclicBarrier(threadCount)
        val errors = AtomicInteger(0)

        val threads = (1..threadCount).map {
            Thread {
                try {
                    barrier.await() // all threads start simultaneously
                    NativeLibLoader.ensureLoaded()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    errors.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }

        errors.get() shouldBe 0
    }

    @Test
    fun `detectOs throws NativeLibraryException for unsupported OS`() {
        val original = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "FreeBSD")
            // Reset loaded flag to force re-detection
            val field = NativeLibLoader::class.java.getDeclaredField("loaded")
            field.isAccessible = true
            field.setBoolean(NativeLibLoader, false)

            val exception = try {
                NativeLibLoader.ensureLoaded()
                null
            } catch (e: NativeLibraryException) {
                e
            }

            exception.shouldNotBeNull()
            exception.message shouldContain "Unsupported platform"
            exception.message shouldContain "Supported platforms"
        } finally {
            // Restore and reset so other tests work
            System.setProperty("os.name", original)
            val field = NativeLibLoader::class.java.getDeclaredField("loaded")
            field.isAccessible = true
            field.setBoolean(NativeLibLoader, false)
        }
    }

    @Test
    fun `simdjson native dir property loads from specified directory`() {
        // This test verifies the error path when the dir doesn't contain the lib
        val tempDir = java.nio.file.Files.createTempDirectory("simdjson-test-native-dir")
        try {
            System.setProperty("simdjson.native.dir", tempDir.toAbsolutePath().toString())
            val field = NativeLibLoader::class.java.getDeclaredField("loaded")
            field.isAccessible = true
            field.setBoolean(NativeLibLoader, false)

            val exception = try {
                NativeLibLoader.ensureLoaded()
                null
            } catch (e: NativeLibraryException) {
                e
            }

            exception.shouldNotBeNull()
            exception.message shouldContain "simdjson.native.dir"
        } finally {
            System.clearProperty("simdjson.native.dir")
            val field = NativeLibLoader::class.java.getDeclaredField("loaded")
            field.isAccessible = true
            field.setBoolean(NativeLibLoader, false)
            tempDir.toFile().deleteRecursively()
        }
    }
}
