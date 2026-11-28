// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.os.Bundle
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
    }

    /** Debug builds: every rotary event that reaches the window is logged, to tell "not delivered" from "not handled". */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (BuildConfig.DEBUG && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            android.util.Log.i("RBSBInput", "ROTARY window action=${event.action} scroll=${event.getAxisValue(MotionEvent.AXIS_SCROLL)}")
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /** Debug builds: hardware keys (the Back button) are logged, to tell "not delivered" from "not handled". */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val handled = super.dispatchKeyEvent(event)
        if (BuildConfig.DEBUG) android.util.Log.i("RBSBInput", "KEY code=${event.keyCode} action=${event.action} handled=$handled")
        return handled
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
