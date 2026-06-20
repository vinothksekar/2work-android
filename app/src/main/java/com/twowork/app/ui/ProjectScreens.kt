package com.twowork.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.*
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.StatusChip
import com.twowork.core.ui.TagRow
import com.twowork.core.ui.TitleValue
import com.twowork.core.ui.Pill
import com.twowork.core.ui.formatMoney
import com.twowork.core.ui.prettyStatus
import com.twowork.core.ui.shortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun paiseToRupees(paise: Long): String = "%d.%02d".format(paise / 100, paise % 100)

@Composable
fun ProjectDetailScreen(user: User, project: Project, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var saved by remember { mutableStateOf(false) }
    var showProposal by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    LaunchedEffect(project.id) {
        (graph.projects.saved() as? ApiResult.Ok)?.let { saved = it.data.savedIds.contains(project.id) }
    }

    TopBarScaffold(title = "Project", onBack = { nav.pop() }) { m ->
        Column(m.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(project.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(project.clientName ?: "Project host", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (project.clientRatingCount > 0) {
                Text("Client rating ★ ${project.clientRating} (${project.clientRatingCount})",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(if (project.projectType == "hourly") "hourly" else "fixed_price")
                StatusChip(project.experienceLevel.ifBlank { "intermediate" })
                if (project.isSealed) StatusChip("sealed")
                if (project.ndaRequired) StatusChip("nda")
            }
            Spacer(Modifier.height(12.dp))
            TitleValue("Budget", formatMoney(project.budgetPaise, project.currency))
            project.duration?.takeIf { it.isNotBlank() }?.let { TitleValue("Duration", it) }
            project.deadline?.takeIf { it.isNotBlank() }?.let { TitleValue("Deadline", shortDate(it)) }
            Spacer(Modifier.height(8.dp))
            Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(project.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Text("Skills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            TagRow(project.requiredSkills)
            Spacer(Modifier.height(20.dp))

            if (user.isFreelancer) {
                Button(onClick = { showProposal = true }, modifier = Modifier.fillMaxWidth()) { Text("Send proposal") }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val r = if (saved) graph.projects.unsave(project.id) else graph.projects.save(project.id)
                            if (r is ApiResult.Ok) { saved = r.data.saved; toast(if (saved) "Saved" else "Removed") }
                            else if (r is ApiResult.Err) toast(r.message)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (saved) "★ Saved" else "☆ Save") }
                OutlinedButton(onClick = { showReport = true }, modifier = Modifier.weight(1f)) { Text("Report") }
            }
        }
    }

    if (showProposal) ProposalDialog(project, onDismiss = { showProposal = false }, onSent = { showProposal = false; toast("Proposal sent") })
    if (showReport) ReportDialog(entityType = "project", entityId = project.id, onDismiss = { showReport = false }, onSent = { showReport = false; toast("Report submitted") })
}

@Composable
private fun ProposalDialog(project: Project, onDismiss: () -> Unit, onSent: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var cover by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(paiseToRupees(project.budgetPaise)) }
    var days by remember { mutableStateOf("7") }
    var busy by remember { mutableStateOf(false) }
    var pickedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickedNames by remember { mutableStateOf<List<String>>(emptyList()) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send proposal") },
        text = {
            Column {
                OutlinedTextField(cover, { cover = it }, label = { Text("Cover letter (20+ chars)") },
                    minLines = 4, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (INR)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Delivery days") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text("Attach files (optional)") }
                pickedNames.forEach { Text("📎 $it", style = MaterialTheme.typography.labelMedium) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && cover.length >= 20 && (days.toIntOrNull() ?: 0) > 0, onClick = {
                busy = true
                scope.launch {
                    val r = graph.projects.sendProposal(project.id, ProposalRequest(cover, amount, days.toInt(), pickedIds))
                    busy = false
                    if (r is ApiResult.Ok) onSent() else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ReportDialog(entityType: String, entityId: String, onDismiss: () -> Unit, onSent: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report") },
        text = { OutlinedTextField(reason, { reason = it }, label = { Text("Reason (10+ chars)") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(enabled = reason.length >= 10, onClick = {
                scope.launch {
                    val r = graph.engagement.report(ReportRequest(entityType, entityId, reason))
                    if (r is ApiResult.Ok) onSent() else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---- Freelancer: Find work (matching / saved / invitations) ----
@Composable
fun FindWorkScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    var tab by remember { mutableStateOf(0) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Find work", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        QuotaBanner(forClient = false)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text("Recommended") })
            FilterChip(tab == 1, { tab = 1 }, label = { Text("Saved") })
            FilterChip(tab == 2, { tab = 2 }, label = { Text("Invitations") })
        }
        when (tab) {
            0 -> ApiContent(loaderKey = "match", loader = { graph.discovery.matching() }) { resp ->
                if (resp.matches.isEmpty()) EmptyState("No matches yet — complete your skills to get recommendations.")
                else LazyColumn { items(resp.matches, key = { it.id }) { ProjectCard(it) { nav.push(Screen.ProjectDetail(it)) } } }
            }
            1 -> ApiContent(loaderKey = "saved", loader = { graph.projects.saved() }) { resp ->
                if (resp.projects.isEmpty()) EmptyState("No saved projects yet.")
                else LazyColumn { items(resp.projects, key = { it.id }) { ProjectCard(it) { nav.push(Screen.ProjectDetail(it)) } } }
            }
            2 -> InvitationsList(nav)
        }
    }
}

// ---- Client: my projects + post + proposals ----
@Composable
fun ClientProjectsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var reload by remember { mutableStateOf(0) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("My projects", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { nav.push(Screen.PostProject) }) { Text("Post") }
        }
        Spacer(Modifier.height(8.dp))
        QuotaBanner(forClient = true, reloadKey = reload)
        Spacer(Modifier.height(8.dp))
        ApiContent(loaderKey = reload, loader = { graph.projects.mine() }) { resp ->
            if (resp.projects.isEmpty()) EmptyState("You haven't posted any projects yet.")
            else LazyColumn {
                items(resp.projects, key = { it.id }) { project ->
                    ListCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StatusChip(project.status)
                        }
                        Text(formatMoney(project.budgetPaise), color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { nav.push(Screen.Proposals(project)) }) { Text("Proposals (${project.proposalCount ?: 0})") }
                            if (project.status == "open" || project.status == "draft") {
                                OutlinedButton(onClick = {
                                    scope.launch { if (graph.projects.cancel(project.id) is ApiResult.Ok) { toast("Cancelled"); reload++ } }
                                }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostProjectScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("fixed") }
    var level by remember { mutableStateOf("intermediate") }
    var sealed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    TopBarScaffold(title = "Post a project", onBack = { nav.pop() }) { m ->
        Column(m.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title (4+ chars)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(description, { description = it }, label = { Text("Description (20+ chars)") }, modifier = Modifier.fillMaxWidth().height(140.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(skills, { skills = it }, label = { Text("Skills (comma separated)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(budget, { budget = it }, label = { Text("Budget (INR)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Type"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == "fixed", { type = "fixed" }, label = { Text("Fixed") })
                FilterChip(type == "hourly", { type = "hourly" }, label = { Text("Hourly") })
            }
            Text("Experience"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("entry", "intermediate", "expert").forEach {
                    FilterChip(level == it, { level = it }, label = { Text(it.replaceFirstChar(Char::uppercase)) })
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(sealed, { sealed = it }); Text("Sealed proposals")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !busy && title.length >= 4 && description.length >= 20 && skills.isNotBlank() && budget.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        val body = ProjectRequest(
                            title = title, description = description,
                            requiredSkills = skills.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            budget = budget, projectType = type, experienceLevel = level, isSealed = sealed
                        )
                        val r = graph.projects.create(body)
                        busy = false
                        if (r is ApiResult.Ok) { toast("Project posted"); nav.pop() } else if (r is ApiResult.Err) toast(r.message)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Publish project") }
        }
    }
}

@Composable
fun ProposalsScreen(project: Project, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var awardFor by remember { mutableStateOf<Proposal?>(null) }
    var reload by remember { mutableStateOf(0) }
    TopBarScaffold(title = "Proposals", onBack = { nav.pop() }) { m ->
        Column(m.fillMaxWidth().padding(16.dp)) {
            Text(project.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ApiContent(loaderKey = reload, loader = { graph.projects.proposals(project.id) }) { resp ->
                if (resp.proposals.isEmpty()) EmptyState("No proposals yet.")
                else LazyColumn {
                    items(resp.proposals, key = { it.id }) { p ->
                        ListCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(p.freelancerName ?: "Freelancer", fontWeight = FontWeight.SemiBold)
                                Text(formatMoney(p.amountPaise), color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${p.durationDays} days · ${prettyStatus(p.status)}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Text(p.coverLetter, style = MaterialTheme.typography.bodyMedium)
                            p.attachments.forEach { att ->
                                TextButton(onClick = { scope.launch { openAttachment(context, graph, att, toast) } }) {
                                    Text("📎 ${att.fileName}")
                                }
                            }
                            TagRow(p.skills.take(6))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        when (val r = graph.messages.openConversation(p.id)) {
                                            is ApiResult.Ok -> nav.push(Screen.Thread(Conversation(id = r.data, projectTitle = project.title, freelancerName = p.freelancerName)))
                                            is ApiResult.Err -> toast(r.message)
                                        }
                                    }
                                }) { Text("Message") }
                                if (p.status == "submitted") {
                                    Button(onClick = { awardFor = p }) { Text("Award") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    awardFor?.let { proposal ->
        AwardDialog(proposal, onDismiss = { awardFor = null }, onAwarded = { awardFor = null; reload++; toast("Contract awarded") })
    }
}

@Composable
fun InviteDialog(handle: String, onDismiss: () -> Unit, onSent: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var openProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var selected by remember { mutableStateOf<Project?>(null) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        (graph.projects.mine() as? ApiResult.Ok)?.let { ok ->
            openProjects = ok.data.projects.filter { it.status == "open" }
            selected = openProjects.firstOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite @$handle") },
        text = {
            Column {
                if (openProjects.isEmpty()) {
                    Text("Post an open project first to invite freelancers.")
                } else {
                    Text("Choose a project:", style = MaterialTheme.typography.labelMedium)
                    openProjects.forEach { p ->
                        FilterChip(selected = selected?.id == p.id, onClick = { selected = p }, label = { Text(p.title) },
                            modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(message, { message = it }, label = { Text("Message (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = {
                val project = selected ?: return@TextButton
                scope.launch {
                    val r = graph.projects.invite(project.id, InviteRequest(handle, message))
                    if (r is ApiResult.Ok) { toast("Invitation sent"); onSent() } else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Send invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AwardDialog(proposal: Proposal, onDismiss: () -> Unit, onAwarded: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var milestoneTitle by remember { mutableStateOf("Full delivery") }
    val amount = remember { paiseToRupees(proposal.amountPaise) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Award contract") },
        text = {
            Column {
                Text("A single milestone for ${formatMoney(proposal.amountPaise)} will be created.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(milestoneTitle, { milestoneTitle = it }, label = { Text("Milestone title") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = milestoneTitle.length >= 2, onClick = {
                scope.launch {
                    val r = graph.projects.accept(proposal.id, AcceptRequest(listOf(MilestoneInput(milestoneTitle, amount))))
                    if (r is ApiResult.Ok) onAwarded() else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Award") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Visible daily-quota banner: freelancer applies-left / client project-posts-left.
@Composable
private fun QuotaBanner(forClient: Boolean, reloadKey: Any = Unit) {
    val graph = LocalGraph.current
    var resp by remember(reloadKey) { mutableStateOf<QuotaResponse?>(null) }
    LaunchedEffect(reloadKey) { (graph.wallet.quota() as? ApiResult.Ok)?.let { resp = it.data } }
    val r = resp ?: return
    val info = if (forClient) r.postQuota?.let { Triple(it.available, it.granted, it.planLabel) }
               else r.quota?.let { Triple(it.available, it.granted, it.planLabel) }
    val (avail, granted, planLabel) = info ?: return
    val label = if (forClient) "project posts" else "applies"
    Surface(
        color = if (avail <= 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "$avail of $granted $label left today · $planLabel plan · resets at midnight IST",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
