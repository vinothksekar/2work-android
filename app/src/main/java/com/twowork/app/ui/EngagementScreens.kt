package com.twowork.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.formatMoney
import com.twowork.core.ui.shortDate
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var reload by remember { mutableIntStateOf(0) }
    TopBarScaffold(
        title = "Notifications",
        onBack = { nav.pop() },
        actions = {
            TextButton(onClick = {
                scope.launch { if (graph.engagement.markRead() is ApiResult.Ok) { toast("Marked read"); reload++ } }
            }) { Text("Mark all read") }
        }
    ) { m ->
        ApiContent(loaderKey = reload, loader = { graph.engagement.notifications() }, modifier = m) { feed ->
            if (feed.notifications.isEmpty()) EmptyState("No notifications yet.")
            else LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                items(feed.notifications, key = { it.id }) { n ->
                    ListCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (n.readAt == null) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Text(shortDate(n.createdAt), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(n.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun InvitationsScreen(nav: Nav, modifier: Modifier = Modifier) {
    TopBarScaffold(title = "Invitations", onBack = { nav.pop() }) { m -> InvitationsList(nav, m) }
}

@Composable
fun InvitationsList(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    ApiContent(loader = { graph.engagement.invitations() }, modifier = modifier) { resp ->
        if (resp.invitations.isEmpty()) EmptyState("No invitations yet.")
        else LazyColumn(Modifier.fillMaxWidth()) {
            items(resp.invitations, key = { it.id }) { inv ->
                ListCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(inv.projectTitle ?: "Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(formatMoney(inv.budgetPaise, inv.currency), color = MaterialTheme.colorScheme.primary)
                    }
                    inv.clientName?.let { Text("from $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (inv.message.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(inv.message, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
