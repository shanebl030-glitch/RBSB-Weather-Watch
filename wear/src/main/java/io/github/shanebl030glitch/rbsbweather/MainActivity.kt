// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.os.Bundle
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.ViewConfigurationCompat
import io.github.shanebl030glitch.rbsbweather.sky.RotaryInput
import io.github.shanebl030glitch.rbsbweather.ui.DebugOptions
import io.github.shanebl030glitch.rbsbweather.ui.WatchApp

/**
 * The weather app (Stage 3): the first screen scrolls from the animated sky through the forecast
 * (decision D1). Debug builds accept launch options for the emulator tests (see DebugOptions);
 * release builds ignore them.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debug = DebugOptions.from(intent)
        setContent { WatchApp(BuildConfig.VERSION_NAME, debug) }
        window.callback = BackKeyFirst(window.callback, ::onBackKey)
    }

    /** Back button released (see BackKeyFirst): close the open detail screen, or leave from the first screen. */
    private fun onBackKey(event: KeyEvent) {
        if (BuildConfig.DEBUG) android.util.Log.i("RBSBInput", "KEY back action=${event.action}")
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
    }

    /** Debug builds: every rotary event that reaches the window is logged, to tell "not delivered" from "not handled". */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (BuildConfig.DEBUG && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            android.util.Log.i("RBSBInput", "ROTARY window action=${event.action} scroll=${event.getAxisValue(MotionEvent.AXIS_SCROLL)}")
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /** Rotary (bezel or crown) events that no view used go to the open list (see RotaryInput). */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val factor = ViewConfigurationCompat.getScaledVerticalScrollFactor(ViewConfiguration.get(this), this)
            RotaryInput.send(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * factor)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}

/**
 * Gives the watch's Back button to [onBack] before the screen's input focus sees it; every other
 * event goes to the activity unchanged. Found on the Wear OS 4 emulator: otherwise the first press
 * only cleared the scrolling list's focus and did nothing visible. Handling it inside the Compose
 * screen instead left a blank screen on Wear OS 6, and overriding the activity's own key dispatch
 * calls a restricted library method (lint RestrictedApi), so it sits on the window callback.
 */
internal class BackKeyFirst(
    private val inner: android.view.Window.Callback,
    private val onBack: (KeyEvent) -> Unit,
) : android.view.Window.Callback by inner {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return inner.dispatchKeyEvent(event)
        onBack(event)
        return true
    }
    override fun onPointerCaptureChanged(hasCapture: Boolean) = inner.onPointerCaptureChanged(hasCapture)
    override fun onProvideKeyboardShortcuts(
        data: MutableList<android.view.KeyboardShortcutGroup>?, menu: android.view.Menu?, deviceId: Int,
    ) = inner.onProvideKeyboardShortcuts(data, menu, deviceId)
}
