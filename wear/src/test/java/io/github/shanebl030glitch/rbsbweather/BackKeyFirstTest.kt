// The watch's Back button reaches the app's back handling first; every other event is passed on
// to the activity unchanged. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackKeyFirstTest {
    private val passedOn = mutableListOf<String>()
    private val inner = Proxy.newProxyInstance(Window.Callback::class.java.classLoader, arrayOf(Window.Callback::class.java)) { _, m, _ ->
        passedOn += m.name
        if (m.returnType == java.lang.Boolean.TYPE) true else null
    } as Window.Callback
    private val backs = mutableListOf<Int>()
    private val wrapped = BackKeyFirst(inner) { backs += it.action }

    @Test fun backButtonGoesToBackHandlingNotTheScreen() {
        assertTrue(wrapped.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertTrue(wrapped.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), backs)
        assertEquals(emptyList<String>(), passedOn)
    }

    @Test fun otherKeysAndTouchesArePassedOnUnchanged() {
        wrapped.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_STEM_1))
        val touch = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        wrapped.dispatchTouchEvent(touch); touch.recycle()
        wrapped.onWindowFocusChanged(true)
        assertEquals(listOf("dispatchKeyEvent", "dispatchTouchEvent", "onWindowFocusChanged"), passedOn)
        assertEquals(emptyList<Int>(), backs)
    }
}
