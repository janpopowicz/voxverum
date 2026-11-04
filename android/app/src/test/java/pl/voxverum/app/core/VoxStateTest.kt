package pl.voxverum.app.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class VoxStateTest {
    @Test
    fun `parallel audio updates preserve every change`() {
        VoxState.updateAudio { AudioStatus() }
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val updates = (1..4).map {
                executor.submit {
                    start.await()
                    repeat(10_000) { VoxState.updateAudio { it.copy(windows = it.windows + 1) } }
                }
            }
            start.countDown()
            updates.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(40_000, VoxState.audio.value.windows)
        } finally {
            executor.shutdownNow()
            VoxState.updateAudio { AudioStatus() }
        }
    }
}
