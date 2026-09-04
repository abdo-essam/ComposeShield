package io.github.composeshield.internal

import android.app.Activity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the contract and thread safety of Android's [WindowRegistry].
 */
@RunWith(RobolectricTestRunner::class)
class WindowRegistryTest {
    @Test
    fun `registered window resolves to stable key and reuses it on subsequent registration`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window

        val key1 = registerWindow(window, activity)
        val key2 = registerWindow(window, activity)

        assertEquals(key1, key2)
        assertEquals(window, windowFor(key1))
        assertEquals(activity, activityFor(key1))
        assertEquals(key1, keyForActivity(activity))
    }

    @Test
    fun `registering window with null activity updates when activity becomes available`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window

        val keyWithoutActivity = registerWindow(window, null)
        assertNull(activityFor(keyWithoutActivity))

        val keyWithActivity = registerWindow(window, activity)
        assertEquals(keyWithoutActivity, keyWithActivity)
        assertEquals(activity, activityFor(keyWithActivity))
    }

    @Test
    fun `concurrent window registrations assign unique IDs without collisions`() {
        val threadCount = 4
        val windowsPerThread = 3
        val totalWindows = threadCount * windowsPerThread

        val activities =
            List(totalWindows) {
                Robolectric.buildActivity(Activity::class.java).get()
            }

        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val registeredKeys = ConcurrentHashMap.newKeySet<WindowKey>()

        for (t in 0 until threadCount) {
            val slice = activities.subList(t * windowsPerThread, (t + 1) * windowsPerThread)
            executor.submit {
                try {
                    for (activity in slice) {
                        val key = registerWindow(activity.window, activity)
                        registeredKeys.add(key)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent registrations should finish in time")
        executor.shutdown()

        assertEquals(totalWindows, registeredKeys.size, "Every registered window must receive a unique key")
    }

    @Test
    fun `anyRegisteredActivity returns an activity when one is registered`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        registerWindow(activity.window, activity)

        val resolved = anyRegisteredActivity()
        assertNotNull(resolved)
    }
}
