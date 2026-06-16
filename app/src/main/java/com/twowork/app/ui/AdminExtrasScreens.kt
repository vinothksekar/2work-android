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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.AdminQuestion
import com.twowork.core.model.AdminQuestionRequest
import com.twowork.core.model.QuestionOption
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.StatusChip
import kotlinx.coroutines.launch

@Composable
fun AdminExtrasScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()

    TopBarScaffold(title = "Admin tools", onBack = { nav.pop() }) { m ->
        LazyColumn(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { WalletAdjustCard(onAdjust = { userId, amount, reason ->
                scope.launch {
                    when (val r = graph.adminExtras.adjustWallet(userId, amount, reason)) {
                        is ApiResult.Ok -> toast("Wallet adjusted. Balance: ${r.data.balance / 100.0} INR")
                        is ApiResult.Err -> toast(r.message)
                    }
                }
            }) }

            item { CreateUserCard(onCreate = { email, name, role, pw ->
                scope.launch {
                    when (val r = graph.adminExtras.createUser(email, name, role, pw)) {
                        is ApiResult.Ok -> toast("User created: $email")
                        is ApiResult.Err -> toast(r.message)
                    }
                }
            }) }

            item { AdminQuestionBankCard(nav = nav) }
        }
    }
}

@Composable
private fun WalletAdjustCard(onAdjust: (String, Double, String) -> Unit) {
    var userId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    ListCard {
        Text("Wallet adjustment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Credit (+) or debit (−) any user wallet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(userId, { userId = it }, label = { Text("User ID (UUID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, label = { Text("Amount (INR, − to debit)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val amt = amount.toDoubleOrNull() ?: return@Button
            if (userId.isBlank() || reason.isBlank()) return@Button
            onAdjust(userId.trim(), amt, reason.trim())
            userId = ""; amount = ""; reason = ""
        }, modifier = Modifier.fillMaxWidth()) { Text("Adjust wallet") }
    }
}

@Composable
private fun CreateUserCard(onCreate: (String, String, String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("freelancer") }
    var password by remember { mutableStateOf("") }
    ListCard {
        Text("Create user", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("freelancer", "client").forEach { r ->
                OutlinedButton(
                    onClick = { role = r },
                    modifier = Modifier.weight(1f)
                ) {
                    if (role == r) Text("✓ $r", fontWeight = FontWeight.Bold) else Text(r)
                }
            }
        }
        OutlinedTextField(password, { password = it }, label = { Text("Password (min 8)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (email.isBlank() || name.isBlank() || password.length < 8) return@Button
            onCreate(email.trim(), name.trim(), role, password)
            email = ""; name = ""; password = ""
        }, modifier = Modifier.fillMaxWidth()) { Text("Create user") }
    }
}

@Composable
private fun AdminQuestionBankCard(nav: Nav) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var skillFilter by remember { mutableStateOf("") }
    val questions = remember { mutableStateListOf<AdminQuestion>() }
    var showEditor by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<AdminQuestion?>(null) }

    fun reload() {
        scope.launch {
            val r = graph.adminExtras.questions(skillFilter.trim().lowercase().ifBlank { null })
            if (r is ApiResult.Ok) { questions.clear(); questions.addAll(r.data.questions) }
            else if (r is ApiResult.Err) toast(r.message)
        }
    }

    LaunchedEffect(Unit) { reload() }

    ListCard {
        Text("Question bank", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(skillFilter, { skillFilter = it }, label = { Text("Filter by skill") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { reload() }) { Text("Load") }
            Button(onClick = { editingQuestion = null; showEditor = true }) { Text("+ Add") }
        }
        Spacer(Modifier.height(8.dp))
        if (questions.isEmpty()) {
            EmptyState("No questions loaded. Type a skill and press Load.")
        } else {
            questions.forEach { q ->
                ListCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatusChip(q.skill); StatusChip("L${q.level}"); if (!q.active) StatusChip("inactive")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(q.question, style = MaterialTheme.typography.bodyMedium)
                            q.options.forEach { o ->
                                val correct = o.key == q.correctKey
                                Text("${o.key.uppercase()}: ${o.text}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (correct) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editingQuestion = q; showEditor = true }) { Text("Edit") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val r = graph.adminExtras.deleteQuestion(q.id)
                                if (r is ApiResult.Ok) { questions.remove(q); toast("Deleted") }
                                else if (r is ApiResult.Err) toast(r.message)
                            }
                        }) { Text("Delete") }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showEditor) {
        QuestionEditorDialog(
            existing = editingQuestion,
            onDismiss = { showEditor = false },
            onSave = { req ->
                scope.launch {
                    val r = if (editingQuestion != null) graph.adminExtras.updateQuestion(editingQuestion!!.id, req)
                            else graph.adminExtras.createQuestion(req)
                    if (r is ApiResult.Ok) { toast("Saved"); showEditor = false; reload() }
                    else if (r is ApiResult.Err) toast(r.message)
                }
            }
        )
    }
}

@Composable
private fun QuestionEditorDialog(
    existing: AdminQuestion?,
    onDismiss: () -> Unit,
    onSave: (AdminQuestionRequest) -> Unit
) {
    var skill by remember(existing) { mutableStateOf(existing?.skill ?: "") }
    var level by remember(existing) { mutableStateOf(existing?.level?.toString() ?: "1") }
    var question by remember(existing) { mutableStateOf(existing?.question ?: "") }
    var optA by remember(existing) { mutableStateOf(existing?.options?.find { it.key == "a" }?.text ?: "") }
    var optB by remember(existing) { mutableStateOf(existing?.options?.find { it.key == "b" }?.text ?: "") }
    var optC by remember(existing) { mutableStateOf(existing?.options?.find { it.key == "c" }?.text ?: "") }
    var optD by remember(existing) { mutableStateOf(existing?.options?.find { it.key == "d" }?.text ?: "") }
    var correctKey by remember(existing) { mutableStateOf(existing?.correctKey ?: "a") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit question" else "Add question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(skill, { skill = it }, label = { Text("Skill") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(level, { level = it.filter(Char::isDigit).take(1) }, label = { Text("Level (1-3)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(question, { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(optA, { optA = it }, label = { Text("Option A") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(optB, { optB = it }, label = { Text("Option B") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(optC, { optC = it }, label = { Text("Option C (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(optD, { optD = it }, label = { Text("Option D (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Correct answer:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("a", "b", "c", "d").forEach { k ->
                        OutlinedButton(onClick = { correctKey = k }) {
                            if (correctKey == k) Text("✓ ${k.uppercase()}", fontWeight = FontWeight.Bold) else Text(k.uppercase())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = skill.isNotBlank() && question.length >= 10 && optA.isNotBlank() && optB.isNotBlank(),
                onClick = {
                    val opts = buildList {
                        add(QuestionOption("a", optA))
                        add(QuestionOption("b", optB))
                        if (optC.isNotBlank()) add(QuestionOption("c", optC))
                        if (optD.isNotBlank()) add(QuestionOption("d", optD))
                    }
                    onSave(AdminQuestionRequest(skill.trim().lowercase(), level.toIntOrNull() ?: 1, question.trim(), opts, correctKey))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
