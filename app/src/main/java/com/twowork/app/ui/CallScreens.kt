package com.twowork.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.twowork.core.di.Graph
import com.twowork.core.di.LocalGraph
import com.twowork.core.net.ApiResult
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface CallUi {
    data object Idle : CallUi
    data class Outgoing(val name: String) : CallUi
    data class Incoming(val id: String, val name: String) : CallUi
    data class Active(val name: String, val muted: Boolean, val connectedAtMs: Long) : CallUi
}

/** Holds the single active call + LiveKit room. Driven by CallLayer + Call buttons. */
object CallManager {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appContext: Context? = null
    private var graph: Graph? = null
    private var room: Room? = null
    private var sessionId: String? = null
    private var muted = false
    private var statusJob: Job? = null
    val state = kotlinx.coroutines.flow.MutableStateFlow<CallUi>(CallUi.Idle)

    fun init(ctx: Context, g: Graph) { appContext = ctx.applicationContext; graph = g }

    private fun toast(m: String) { appContext?.let { Toast.makeText(it, m, Toast.LENGTH_LONG).show() } }

    fun start(contactId: String, name: String) {
        val g = graph ?: return
        if (state.value !is CallUi.Idle) return
        state.value = CallUi.Outgoing(name)
        scope.launch {
            when (val r = g.calls.start(contactId)) {
                is ApiResult.Ok -> { sessionId = r.data.sessionId; connect(r.data.url, r.data.token, name) }
                is ApiResult.Err -> { toast(r.message); reset() }
            }
        }
    }

    fun onIncoming(id: String, name: String) {
        if (state.value is CallUi.Idle) state.value = CallUi.Incoming(id, name)
    }

    fun accept() {
        val g = graph ?: return
        val s = state.value as? CallUi.Incoming ?: return
        sessionId = s.id
        state.value = CallUi.Outgoing(s.name)
        scope.launch {
            when (val r = g.calls.accept(s.id)) {
                is ApiResult.Ok -> connect(r.data.url, r.data.token, s.name)
                is ApiResult.Err -> { toast(r.message); reset() }
            }
        }
    }

    fun decline() {
        val g = graph ?: return
        val s = state.value as? CallUi.Incoming ?: return
        scope.launch { g.calls.decline(s.id) }
        reset()
    }

    fun hangUp() {
        val g = graph; val id = sessionId
        if (g != null && id != null) scope.launch { g.calls.end(id) }
        reset()
    }

    fun toggleMute() {
        val r = room ?: return
        muted = !muted
        scope.launch { runCatching { r.localParticipant.setMicrophoneEnabled(!muted) } }
        (state.value as? CallUi.Active)?.let { state.value = it.copy(muted = muted) }
    }

    private suspend fun connect(url: String, token: String, name: String) {
        val ctx = appContext ?: return
        try {
            val r = LiveKit.create(ctx)
            room = r
            // Hang up instantly if the SFU drops us or the other participant leaves.
            scope.launch {
                r.events.collect { e ->
                    if (e is RoomEvent.Disconnected || e is RoomEvent.ParticipantDisconnected) reset()
                }
            }
            r.connect(url, token)
            r.localParticipant.setMicrophoneEnabled(true)
            muted = false
            state.value = CallUi.Active(name, false, System.currentTimeMillis())
            startStatusPolling()
        } catch (e: Exception) {
            toast(e.message ?: "Call failed to connect")
            reset()
        }
    }

    // Backup to the SFU events: poll the call session so we tear down promptly
    // when the other side ends/declines (covers cases the SFU is slow to report).
    private fun startStatusPolling() {
        if (statusJob != null) return
        statusJob = scope.launch {
            while (true) {
                delay(2500)
                val id = sessionId ?: break
                val g = graph ?: break
                val s = (g.calls.status(id) as? ApiResult.Ok)?.data
                if (s == "ended" || s == "declined" || s == "missed") { reset(); break }
            }
        }
    }

    private fun reset() {
        statusJob?.cancel(); statusJob = null
        room?.let { runCatching { it.disconnect() } }
        room = null; sessionId = null; muted = false
        state.value = CallUi.Idle
    }
}

fun hasMicPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Global call overlay: polls for incoming calls and renders the active-call UI. */
@Composable
fun CallLayer() {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val toast = rememberToaster()
    val state by CallManager.state.collectAsState()

    LaunchedEffect(Unit) { CallManager.init(context, graph) }

    // Poll for incoming calls while idle.
    LaunchedEffect(Unit) {
        while (true) {
            if (CallManager.state.value is CallUi.Idle) {
                (graph.calls.incoming() as? ApiResult.Ok)?.data?.incoming?.let { CallManager.onIncoming(it.id, it.callerName) }
            }
            delay(4000)
        }
    }

    val micForAccept = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) CallManager.accept() else { CallManager.decline(); toast("Microphone permission is required for calls") }
    }

    when (val s = state) {
        is CallUi.Outgoing -> CallOverlay("Calling ${s.name}…", muted = false, showMute = false, onMute = {}, onHang = { CallManager.hangUp() })
        is CallUi.Active -> CallOverlay("In call · ${s.name}", muted = s.muted, showMute = true, connectedAtMs = s.connectedAtMs, onMute = { CallManager.toggleMute() }, onHang = { CallManager.hangUp() })
        is CallUi.Incoming -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Incoming call") },
            text = { Text("${s.name} is calling…") },
            confirmButton = {
                Button(onClick = {
                    if (hasMicPermission(context)) CallManager.accept() else micForAccept.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Accept") }
            },
            dismissButton = {
                OutlinedButton(onClick = { CallManager.decline() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Decline") }
            }
        )
        CallUi.Idle -> {}
    }
}

private fun formatCallDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun CallOverlay(
    title: String,
    muted: Boolean,
    showMute: Boolean,
    connectedAtMs: Long? = null,
    onMute: () -> Unit,
    onHang: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("📞 Audio call") },
        text = {
            Column {
                Text(title)
                if (connectedAtMs != null) {
                    var now by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(connectedAtMs) {
                        while (true) { now = System.currentTimeMillis(); delay(1000) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatCallDuration(now - connectedAtMs),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onHang, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Hang up") }
        },
        dismissButton = { if (showMute) OutlinedButton(onClick = onMute) { Text(if (muted) "Unmute" else "Mute") } }
    )
}
