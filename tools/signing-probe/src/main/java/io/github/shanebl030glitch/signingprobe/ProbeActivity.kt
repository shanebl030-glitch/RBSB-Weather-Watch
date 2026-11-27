// Signing gate 1 probe (Stage 0 test tool, never released). Created By Shane BlackbuRn
package io.github.shanebl030glitch.signingprobe

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Open this app on BOTH the paired phone and watch, then tap "Run test" on either one.
 * It sends a message to every connected node, expects a reply, and writes a data item the
 * other side should receive. Google Play services only delivers these between copies of the
 * same package signed with matching signatures, so a two-way exchange means gate 1 passes.
 */
class ProbeActivity : Activity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    private lateinit var log: TextView
    private lateinit var result: TextView
    private val role by lazy { if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) "watch" else "phone" }
    private var gotReply = false
    private var gotData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(if (role == "watch") 28 else 16)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        result = TextView(this).apply {
            textSize = 16f; setTextColor(Color.YELLOW); gravity = Gravity.CENTER
            text = "GATE 1: NOT RUN"
        }
        val run = Button(this).apply { text = "Run test"; setOnClickListener { runTest() } }
        log = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE) }
        column.addView(result); column.addView(run); column.addView(log)
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(column) })

        say("Signing probe on the $role")
        say("Package: $packageName")
        describeSigning()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    private fun describeSigning() {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            if (info == null) { say("Signing info: unavailable"); Log.i(TAG, "IDENTITY {\"error\":\"no signingInfo\"}"); return }
            val current = info.apkContentsSigners.toList()
            val history = if (info.hasPastSigningCertificates()) info.signingCertificateHistory.toList() else emptyList()
            say("Current signer SHA-256: ${current.joinToString { short(sha(it.toByteArray())) }}")
            say(if (history.isEmpty()) "Rotation history: none (single key)"
                else "Rotation history (oldest first): ${history.joinToString(" -> ") { short(sha(it.toByteArray())) }}")
            // Machine-readable line for CI (signing gate 1). "hash" is Signature.hashCode() in hex,
            // which is how `dumpsys package` lists signatures, so the two can be compared directly.
            val json = JSONObject()
                .put("current_sha256", JSONArray(current.map { sha(it.toByteArray()) }))
                .put("current_hash", JSONArray(current.map { Integer.toHexString(it.hashCode()) }))
                .put("history_sha256", JSONArray(history.map { sha(it.toByteArray()) }))
                .put("history_hash", JSONArray(history.map { Integer.toHexString(it.hashCode()) }))
            Log.i(TAG, "IDENTITY $json")
        } catch (e: Exception) {
            say("Signing info error: ${e.message}")
            Log.i(TAG, "IDENTITY {\"error\":\"${e.javaClass.simpleName}\"}")
        }
    }

    private fun runTest() {
        gotReply = false; gotData = false
        showResult()
        say("--- Test started ${now()} ---")
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    say("No connected devices. Check the pairing, then retry.")
                    result.text = "GATE 1: NO CONNECTED DEVICE"; result.setTextColor(Color.RED)
                    return@addOnSuccessListener
                }
                nodes.forEach { n ->
                    say("Connected: ${n.displayName} (${n.id})")
                    Wearable.getMessageClient(this).sendMessage(n.id, PING, "$role ${now()}".toByteArray())
                        .addOnSuccessListener { say("Ping sent to ${n.displayName}") }
                        .addOnFailureListener { e -> say("Ping FAILED to ${n.displayName}: ${e.message}") }
                }
            }
            .addOnFailureListener { e -> say("Could not list devices: ${e.message}") }

        val req = PutDataMapRequest.create("$DATA/$role").apply {
            dataMap.putString("from", role)
            dataMap.putLong("time", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(req)
            .addOnSuccessListener { say("Data item written: $DATA/$role") }
            .addOnFailureListener { e -> say("Data item FAILED: ${e.message}") }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val text = String(event.data)
        when (event.path) {
            PING -> {
                say("Ping received from ${event.sourceNodeId}: $text")
                Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, PONG, "$role ${now()}".toByteArray())
                    .addOnSuccessListener { say("Reply sent") }
                    .addOnFailureListener { e -> say("Reply FAILED: ${e.message}") }
            }
            PONG -> {
                say("Reply received from ${event.sourceNodeId}: $text")
                gotReply = true; showResult()
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            val path = e.dataItem.uri.path ?: return@forEach
            if (e.type == DataEvent.TYPE_CHANGED && path.startsWith(DATA) && !path.endsWith("/$role")) {
                say("Data item received: $path")
                gotData = true; showResult()
            }
        }
    }

    private fun showResult() {
        when {
            gotReply && gotData -> { result.text = "GATE 1: PASS"; result.setTextColor(Color.GREEN) }
            gotReply || gotData -> { result.text = "GATE 1: PARTIAL (reply=$gotReply, data=$gotData)"; result.setTextColor(Color.YELLOW) }
            else -> { result.text = "GATE 1: WAITING"; result.setTextColor(Color.YELLOW) }
        }
    }

    private fun say(s: String) = runOnUiThread { log.append(s + "\n") }
    private fun now() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun short(h: String) = h.take(8) + "…" + h.takeLast(8)

    companion object {
        const val PING = "/probe/ping"
        const val PONG = "/probe/pong"
        const val DATA = "/probe/data"
        const val TAG = "RBSBProbe"
    }
}
