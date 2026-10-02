package com.launchdarkly.observability.replay.capture

import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.launchdarkly.observability.context.ObserveLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Behavioral tests for [FrameSynchronizer]: geometry has to be read inside the draw pass that
 * produces a frame, and the caller must not copy pixels until that frame has been rendered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrameSynchronizerTest {
    private val logger = mockk<ObserveLogger>(relaxed = true)
    private val drawListener = slot<ViewTreeObserver.OnDrawListener>()

    private val observer = mockk<ViewTreeObserver>(relaxed = true).also {
        every { it.isAlive } returns true
        every { it.addOnDrawListener(capture(drawListener)) } returns Unit
    }

    private val rootView = mockk<View>(relaxed = true).also {
        every { it.viewTreeObserver } returns observer
        // Listener removal is posted out of the draw pass; run it inline so tests can verify it.
        every { it.post(any()) } answers { firstArg<Runnable>().run(); true }
    }

    private val window = mockk<Window>(relaxed = true)

    private fun synchronizer(
        observeFrameRendered: FrameRenderedObserver = { _, _ -> null },
        postFrameCallback: (onFrame: () -> Unit) -> Unit = { it() },
    ) = FrameSynchronizer(
        logger = logger,
        postFrameCallback = postFrameCallback,
        observeFrameRendered = observeFrameRendered,
    )

    @Test
    fun `samples from inside the draw pass`() = runTest {
        var drawing = false
        var sampledWhileDrawing = false
        val synchronizer = synchronizer()

        val sampling = async {
            synchronizer.sampleAtDraw(rootView) {
                sampledWhileDrawing = drawing
                "masks"
            }
        }
        runCurrent()

        drawing = true
        drawListener.captured.onDraw()
        drawing = false
        runCurrent()

        assertEquals("masks", sampling.await())
        assertTrue(sampledWhileDrawing, "sample must run inside the draw pass, not around it")
        verify { observer.removeOnDrawListener(drawListener.captured) }
    }

    @Test
    fun `reads the tree directly when nothing draws`() = runTest {
        var samples = 0
        val synchronizer = synchronizer()

        val sampling = async { synchronizer.sampleAtDraw(rootView) { samples++; "masks" } }
        runCurrent()
        assertEquals(0, samples, "an idle tree must not be read before the draw wait times out")

        advanceTimeBy(200)
        runCurrent()

        assertEquals("masks", sampling.await())
        assertEquals(1, samples)
    }

    @Test
    fun `reads the tree directly when draws cannot be observed`() = runTest {
        every { observer.isAlive } returns false
        val synchronizer = synchronizer()

        assertEquals("masks", synchronizer.sampleAtDraw(rootView) { "masks" })
        verify(exactly = 0) { observer.addOnDrawListener(any()) }
    }

    @Test
    fun `a sampling failure stays out of the draw pass`() = runTest {
        var attempts = 0
        val synchronizer = synchronizer()

        val sampling = async {
            synchronizer.sampleAtDraw(rootView) {
                attempts++
                if (attempts == 1) throw IllegalStateException("boom")
                "masks"
            }
        }
        runCurrent()

        // Would propagate into View's draw pass and take down the app if it weren't caught.
        drawListener.captured.onDraw()
        runCurrent()

        assertEquals("masks", sampling.await())
        assertEquals(2, attempts)
    }

    @Test
    fun `waits for the sampled frame to finish rendering`() = runTest {
        var signalFrameRendered: (() -> Unit)? = null
        var unregistered = false
        val synchronizer = synchronizer(
            observeFrameRendered = { _, onFrameRendered ->
                signalFrameRendered = onFrameRendered
                { unregistered = true }
            }
        )

        val sampling = async { synchronizer.sampleAtRenderedFrame(rootView, window) { "masks" } }
        runCurrent()

        assertNotNull(
            signalFrameRendered,
            "frames must be observed before the draw, otherwise the sampled frame is missed"
        )

        // A frame that finished before the sample was taken isn't the frame we sampled from.
        signalFrameRendered?.invoke()
        drawListener.captured.onDraw()
        runCurrent()
        assertFalse(sampling.isCompleted, "must keep waiting for the frame the sample came from")

        signalFrameRendered?.invoke()
        runCurrent()

        assertEquals("masks", sampling.await())
        assertTrue(unregistered)
    }

    @Test
    fun `gives up waiting on a window that renders nothing`() = runTest {
        val synchronizer = synchronizer(observeFrameRendered = { _, _ -> {} })

        val sampling = async { synchronizer.sampleAtRenderedFrame(rootView, window) { "masks" } }
        runCurrent()
        drawListener.captured.onDraw()
        runCurrent()
        assertFalse(sampling.isCompleted)

        advanceTimeBy(200)
        runCurrent()

        assertEquals("masks", sampling.await())
    }

    @Test
    fun `skips the frame wait when nothing draws`() = runTest {
        var unregistered = false
        val synchronizer = synchronizer(observeFrameRendered = { _, _ -> { unregistered = true } })

        val sampling = async { synchronizer.sampleAtRenderedFrame(rootView, window) { "masks" } }
        runCurrent()
        // Past the draw timeout but well inside the frame timeout: an idle tree has no frame in
        // flight, so waiting for one would only stall the capture.
        advanceTimeBy(40)
        runCurrent()

        assertTrue(sampling.isCompleted, "an idle tree must not wait for a frame that isn't coming")
        assertEquals("masks", sampling.await())
        assertTrue(unregistered)
    }

    @Test
    fun `skips the wait when frame completion cannot be observed`() = runTest {
        val synchronizer = synchronizer(observeFrameRendered = { _, _ -> null })

        val sampling = async { synchronizer.sampleAtRenderedFrame(rootView, window) { "masks" } }
        runCurrent()
        drawListener.captured.onDraw()
        runCurrent()

        assertEquals("masks", sampling.await())
    }

    @Test
    fun `awaitVsync resumes on the next frame`() = runTest {
        var frameCallback: (() -> Unit)? = null
        var resumed = false
        val synchronizer = synchronizer(postFrameCallback = { frameCallback = it })

        val waiting = async {
            synchronizer.awaitVsync()
            resumed = true
        }
        runCurrent()
        assertFalse(resumed)

        frameCallback?.invoke()
        runCurrent()

        assertTrue(resumed)
        waiting.await()
    }
}
