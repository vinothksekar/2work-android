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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.Freelancer
import com.twowork.core.model.Project
import com.twowork.core.model.User
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EllipsisText
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.Pill
import com.twowork.core.ui.StatusChip
import com.twowork.core.ui.TagRow
import com.twowork.core.ui.formatMoney
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(user: User, nav: Nav, modifier: Modifier = Modifier) {
    var talent by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Discover", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !talent, onClick = { talent = false }, label = { Text("Projects") })
            FilterChip(selected = talent, onClick = { talent = true }, label = { Text("Talent") })
        }
        if (talent) TalentList(user) else ProjectList(nav)
    }
}

@Composable
private fun ProjectList(nav: Nav) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("newest") }
    var page by remember { mutableStateOf(1) }
    var items by remember { mutableStateOf<List<Project>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(reset: Boolean) {
        scope.launch {
            loading = true; error = null
            val p = if (reset) 1 else page + 1
            when (val r = graph.discovery.projects(q = query, sort = sort, page = p)) {
                is ApiResult.Ok -> {
                    items = if (reset) r.data.projects else items + r.data.projects
                    total = r.data.total; hasMore = r.data.hasMore; page = p
                }
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(true) }

    SearchBar(query, { query = it }, onSearch = { load(true) })
    SortChips(sort, listOf("newest" to "Newest", "budget_high" to "Budget ↓", "budget_low" to "Budget ↑")) { sort = it; load(true) }
    Text(if (total > 0) "$total open" else "", style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.fillMaxWidth()) {
        items(items, key = { it.id }) { ProjectCard(it) { nav.push(Screen.ProjectDetail(it)) } }
        item {
            when {
                loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                error != null -> Column { Text(error!!, color = MaterialTheme.colorScheme.error); TextButton({ load(true) }) { Text("Retry") } }
                items.isEmpty() -> EmptyState("No open projects yet.")
                hasMore -> TextButton({ load(false) }, Modifier.fillMaxWidth()) { Text("Load more") }
            }
        }
    }
}

@Composable
private fun TalentList(user: User) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var skillFilter by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<Freelancer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var inviteHandle by remember { mutableStateOf<String?>(null) }
    val toast = rememberToaster()

    fun load() {
        scope.launch {
            loading = true; error = null
            when (val r = graph.discovery.freelancers(q = query, skills = skillFilter.ifBlank { null })) {
                is ApiResult.Ok -> items = r.data.freelancers
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    SearchBar(query, { query = it }, onSearch = { load() })
    SkillCatalogPicker(selectedCsv = skillFilter, onChange = { skillFilter = it; load() })
    LazyColumn(Modifier.fillMaxWidth()) {
        items(items, key = { it.handle ?: it.fullName }) { f ->
            FreelancerCard(
                f,
                canInvite = user.isClient && f.handle != null,
                onInvite = { inviteHandle = f.handle },
                canSave = f.handle != null,
                onSave = {
                    scope.launch {
                        when (val r = graph.messages.addContact(handle = f.handle)) {
                            is ApiResult.Ok -> toast("Saved to your contacts")
                            is ApiResult.Err -> toast(r.message)
                        }
                    }
                }
            )
        }
        item {
            when {
                loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                error != null -> Column { Text(error!!, color = MaterialTheme.colorScheme.error); TextButton({ load() }) { Text("Retry") } }
                items.isEmpty() -> EmptyState("No public talent profiles yet.")
            }
        }
    }
    inviteHandle?.let { handle -> InviteDialog(handle, onDismiss = { inviteHandle = null }) { inviteHandle = null } }
}

@Composable
fun SearchBar(value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text("Search") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = { TextButton(onSearch) { Text("Go") } },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SortChips(current: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = current == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
fun ProjectCard(project: Project, onOpen: () -> Unit) {
    ListCard(onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(formatMoney(project.budgetPaise, project.currency), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        Text(
            buildString {
                append(project.clientName ?: "Project host")
                if (project.clientRatingCount > 0) append(" · ★${project.clientRating} (${project.clientRatingCount})")
                append(if (project.isSealed) " · Sealed" else " · ${project.proposalCount ?: 0} proposals")
            },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        EllipsisText(project.description, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        TagRow(project.requiredSkills.take(6))
    }
}

@Composable
private fun FreelancerCard(
    freelancer: Freelancer, canInvite: Boolean, onInvite: () -> Unit,
    canSave: Boolean = false, onSave: () -> Unit = {}
) {
    ListCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(freelancer.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (freelancer.completedRatings > 0) "★ ${freelancer.rating}" else "New",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        freelancer.handle?.let { Text("@$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
        Text(freelancer.headline ?: "Available specialist", style = MaterialTheme.typography.bodyMedium)
        freelancer.hourlyRatePaise?.let { Text("${formatMoney(it)}/hr", style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.height(8.dp))
        TagRow(freelancer.skills.take(6))
        if (freelancer.badges.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("✓ Certified", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            TagRow(freelancer.badges.map { "${it.skill} · L${it.level}" })
        }
        if (canInvite || canSave) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canInvite) androidx.compose.material3.OutlinedButton(onClick = onInvite) { Text("Invite to project") }
                if (canSave) androidx.compose.material3.OutlinedButton(onClick = onSave) { Text("Save contact") }
            }
        }
    }
}
