package com.twowork.app.ui

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.Conversation
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EllipsisText
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var draft by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    TopBarScaffold(title = conversation.projectTitle ?: "Messages", onBack = { nav.pop() }) { m ->
        Column(m.fillMaxSize()) {
            ApiContent(loaderKey = reload, loader = { graph.messages.messages(conversation.id) }, modifier = Modifier.weight(1f)) { resp ->
                if (resp.messages.isEmpty()) EmptyState("No messages yet — say hello.")
                else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(resp.messages, key = { it.id }) { msg ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(msg.senderName ?: "User", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                Text(msg.body, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, label = { Text("Message") }, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val text = draft.trim()
                    if (text.length >= 2) {
                        scope.launch {
                            val r = graph.messages.send(conversation.id, text)
                            if (r is ApiResult.Ok) { draft = ""; reload++ } else if (r is ApiResult.Err) toast(r.message)
                        }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}
