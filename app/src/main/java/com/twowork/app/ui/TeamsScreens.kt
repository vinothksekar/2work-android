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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.Team
import com.twowork.core.model.TeamDetailResponse
import com.twowork.core.model.TeamInvitation
import com.twowork.core.model.TeamMember
import com.twowork.core.model.User
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import kotlinx.coroutines.launch

@Composable
fun TeamsScreen(user: User, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var showCreate by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create team")
            }
        }
    ) { innerPadding ->
        ApiContent(loaderKey = reload, loader = { graph.teams.teams() }, modifier = Modifier.padding(innerPadding)) { resp ->
            if (resp.teams.isEmpty()) {
                EmptyState("No teams yet — tap + to create one")
            } else {
                LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                    item { Spacer(Modifier.height(12.dp)) }
                    items(resp.teams) { team ->
                        TeamCard(team, onOpen = { nav.push(Screen.TeamDetail(team.id, team.name)) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTeamDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, desc ->
                scope.launch {
                    when (val r = graph.teams.create(name, desc)) {
                        is ApiResult.Ok -> { toast("Team created"); showCreate = false; reload++ }
                        is ApiResult.Err -> toast(r.message)
                    }
                }
            }
        )
    }
}

@Composable
private fun TeamCard(team: Team, onOpen: () -> Unit) {
    ListCard(onClick = onOpen) {
        Column(Modifier.padding(12.dp)) {
            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (team.description.isNotBlank()) {
                Text(team.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text("${team.memberCount} member${if (team.memberCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreateTeamDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New team") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Team name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Description (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = name.length >= 2, onClick = { onCreate(name.trim(), desc.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Team detail ──────────────────────────────────────────────────────────────

@Composable
fun TeamDetailScreen(teamId: String, teamName: String, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var reload by remember { mutableStateOf(0) }
    var showInvite by remember { mutableStateOf(false) }

    TopBarScaffold(title = teamName, onBack = { nav.pop() }) { m ->
        ApiContent(loaderKey = reload, loader = { graph.teams.detail(teamId) }, modifier = m) { resp ->
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Members (${resp.members.size})",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Button(onClick = { showInvite = true }) { Text("Invite") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(resp.members) { member ->
                    MemberRow(member,
                        onRemove = {
                            scope.launch {
                                when (val r = graph.teams.removeMember(teamId, member.userId)) {
                                    is ApiResult.Ok -> { toast("Removed"); reload++ }
                                    is ApiResult.Err -> toast(r.message)
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
                if (resp.invitations.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text("Pending invitations (${resp.invitations.size})",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(resp.invitations) { inv ->
                        InvitationRow(inv)
                        HorizontalDivider()
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showInvite) {
        InviteDialog(
            onDismiss = { showInvite = false },
            onInvite = { email ->
                scope.launch {
                    when (val r = graph.teams.invite(teamId, email)) {
                        is ApiResult.Ok -> { toast("Invitation sent"); showInvite = false; reload++ }
                        is ApiResult.Err -> toast(r.message)
                    }
                }
            }
        )
    }
}

@Composable
private fun MemberRow(member: TeamMember, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(member.fullName.ifBlank { member.email },
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(member.email, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(member.role, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        if (member.role != "owner") {
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Remove") }
        }
    }
}

@Composable
private fun InvitationRow(inv: TeamInvitation) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(inv.email, style = MaterialTheme.typography.bodyMedium)
            Text("Invited by ${inv.invitedByName}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Pending", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit, onInvite: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite member") },
        text = {
            OutlinedTextField(value = email, onValueChange = { email = it },
                label = { Text("Email address") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(enabled = email.contains("@"), onClick = { onInvite(email.trim()) }) { Text("Send invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
