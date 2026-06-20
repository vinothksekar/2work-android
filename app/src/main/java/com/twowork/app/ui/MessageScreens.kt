package com.twowork.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.twowork.core.di.Graph
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.Attachment
import com.twowork.core.model.Conversation
import com.twowork.core.model.Message
import com.twowork.core.model.User
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EllipsisText
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.Pill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ContactsScreen(user: User, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var reload by remember { mutableIntStateOf(0) }
    var handle by remember { mutableStateOf("") }
    var pendingCall by remember { mutableStateOf<Pair<String, String>?>(null) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCall?.let { (id, name) ->
            if (granted) CallManager.start(id, name) else toast("Microphone permission is required for calls")
        }
        pendingCall = null
    }
    fun callContact(id: String, name: String) {
        if (hasMicPermission(context)) CallManager.start(id, name)
        else { pendingCall = id to name; micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
    }

    TopBarScaffold(title = "My contacts", onBack = { nav.pop() }) { m ->
        Column(m.fillMaxWidth().padding(16.dp)) {
            Text(
                "Save people to work with again. Clients can message saved freelancers anytime; freelancers get notified when a saved client posts a project.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(handle, { handle = it }, label = { Text("Add anyone by @handle") },
                    singleLine = true, modifier = Modifier.weight(1f))
                Button(enabled = handle.trim().isNotEmpty(), onClick = {
                    val h = handle.trim().removePrefix("@")
                    scope.launch {
                        when (val r = graph.messages.addContact(handle = h)) {
                            is ApiResult.Ok -> { handle = ""; toast("Contact added"); reload++ }
                            is ApiResult.Err -> toast(r.message)
                        }
                    }
                }) { Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            ApiContent(loaderKey = reload, loader = { graph.messages.contacts() }) { resp ->
              Column {
                resp.myHandle?.let {
                    Text("Your handle: @$it — share it so others can add you",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                if (resp.contacts.isEmpty()) EmptyState("No contacts yet. Add anyone by @handle, or tap Save on a talent card.")
                else LazyColumn {
                    items(resp.contacts, key = { it.contactId }) { c ->
                        ListCard {
                            Text(c.fullName.ifBlank { "User" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(c.role.ifBlank { null }, c.handle?.let { "@$it" }).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            c.headline?.let { if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch {
                                        when (val r = graph.messages.openContactThread(c.contactId)) {
                                            is ApiResult.Ok -> nav.push(Screen.Thread(Conversation(
                                                id = r.data,
                                                clientName = if (user.isClient) user.fullName else c.fullName,
                                                freelancerName = if (user.isClient) c.fullName else user.fullName
                                            )))
                                            is ApiResult.Err -> toast(r.message)
                                        }
                                    }
                                }) { Text("Message") }
                                OutlinedButton(onClick = { callContact(c.contactId, c.fullName.ifBlank { "User" }) }) { Text("📞 Call") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            if (graph.messages.removeContact(c.contactId) is ApiResult.Ok) { toast("Removed"); reload++ }
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Remove") }
                            }
                        }
                    }
                }
              }
            }
        }
    }
}

@Composable
fun ConversationsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Inbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ApiContent(loader = { graph.messages.conversations() }) { resp ->
            if (resp.conversations.isEmpty()) EmptyState("No conversations yet. Messaging opens when you propose on a project.")
            else LazyColumn {
                items(resp.conversations, key = { it.id }) { c ->
                    ListCard(onClick = { nav.push(Screen.Thread(c)) }) {
                        Text(c.projectTitle ?: "Conversation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(c.clientName, c.freelancerName).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        c.latestMessage?.let { Spacer(Modifier.height(4.dp)); EllipsisText(it, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreadScreen(conversation: Conversation, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var pickedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickedNames by remember { mutableStateOf<List<String>>(emptyList()) }

    fun mergeNew(incoming: List<Message>) {
        if (incoming.isEmpty()) return
        val seen = messages.mapTo(HashSet()) { it.id }
        messages = messages + incoming.filter { it.id !in seen }
    }

    // Initial load, then poll every 4s for new messages (lightweight "live" chat).
    LaunchedEffect(conversation.id) {
        when (val r = graph.messages.messages(conversation.id)) {
            is ApiResult.Ok -> messages = r.data.messages
            is ApiResult.Err -> toast(r.message)
        }
        loading = false
        while (true) {
            delay(4000)
            val after = messages.lastOrNull()?.createdAt
            val r = graph.messages.messages(conversation.id, after)
            if (r is ApiResult.Ok) mergeNew(r.data.messages)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = queryFileName(context, uri)
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            when {
                bytes == null || bytes.isEmpty() -> toast("Could not read the file")
                bytes.size > 12 * 1024 * 1024 -> toast("File too large (max 12 MB)")
                else -> when (val r = graph.messages.uploadAttachment(bytes, mime, name)) {
                    is ApiResult.Ok -> { pickedIds = pickedIds + r.data.id; pickedNames = pickedNames + r.data.fileName }
                    is ApiResult.Err -> toast(r.message)
                }
            }
        }
    }

    fun doSend() {
        val text = draft.trim()
        if ((text.isEmpty() && pickedIds.isEmpty()) || sending) return
        scope.launch {
            sending = true
            when (val r = graph.messages.send(conversation.id, text, pickedIds)) {
                is ApiResult.Ok -> {
                    draft = ""; pickedIds = emptyList(); pickedNames = emptyList()
                    val r2 = graph.messages.messages(conversation.id, messages.lastOrNull()?.createdAt)
                    if (r2 is ApiResult.Ok) mergeNew(r2.data.messages)
                }
                is ApiResult.Err -> toast(r.message)
            }
            sending = false
        }
    }

    TopBarScaffold(title = conversation.projectTitle ?: "Messages", onBack = { nav.pop() }) { m ->
        Column(m.fillMaxSize()) {
            when {
                loading -> Column(Modifier.fillMaxSize().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(40.dp)); CircularProgressIndicator()
                }
                messages.isEmpty() -> EmptyState("No messages yet — say hello.", Modifier.weight(1f))
                else -> LazyColumn(Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp)) {
                    items(messages, key = { it.id }) { msg ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(msg.senderName ?: "User", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            if (msg.body.isNotBlank()) {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                    Text(msg.body, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            msg.attachments.forEach { att ->
                                TextButton(onClick = { scope.launch { openAttachment(context, graph, att, toast) } }) {
                                    Text("📎 ${att.fileName}")
                                }
                            }
                        }
                    }
                }
            }
            if (pickedNames.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pickedNames.forEach { Pill(it) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Filled.AttachFile, contentDescription = "Attach") }
                OutlinedTextField(draft, { draft = it }, label = { Text("Message") }, modifier = Modifier.weight(1f))
                IconButton(onClick = { doSend() }, enabled = !sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

internal fun queryFileName(context: Context, uri: Uri): String {
    var name = "file"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index)?.let { name = it }
        }
    }
    return name
}

/** Downloads an attachment with the session cookie, then opens it via the system viewer. */
internal suspend fun openAttachment(context: Context, graph: Graph, attachment: Attachment, toast: (String) -> Unit) {
    when (val r = graph.messages.downloadAttachment(attachment.id)) {
        is ApiResult.Ok -> {
            try {
                val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
                val file = File(dir, attachment.fileName.ifBlank { "file" })
                withContext(Dispatchers.IO) { file.writeBytes(r.data) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, attachment.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                toast("No app can open ${attachment.fileName}")
            }
        }
        is ApiResult.Err -> toast(r.message)
    }
}
