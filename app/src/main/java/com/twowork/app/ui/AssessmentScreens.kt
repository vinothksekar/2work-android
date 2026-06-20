package com.twowork.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.AssessmentItem
import com.twowork.core.model.AssessmentQuestion
import com.twowork.core.model.SubmitResultResponse
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.Pill
import com.twowork.core.ui.SectionHeader
import com.twowork.core.ui.StatusChip
import com.twowork.core.ui.TagRow
import com.twowork.core.ui.formatMoney
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AssessmentsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var starting by remember { mutableStateOf<String?>(null) }
    var rulesFor by remember { mutableStateOf<AssessmentItem?>(null) }

    TopBarScaffold(title = "Skills & Certification", onBack = { nav.pop() }) { m ->
        ApiContent(loader = { graph.assessments.available() }, modifier = m) { data ->
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    Text(
                        "Paid, timed, proctored certification exams. Pass a 3-level exam (60% to pass) to earn a public badge clients can see.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (data.badges.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        SectionHeader("Your badges")
                        TagRow(data.badges.map { "${it.skill} · L${it.level}" })
                    }
                    Spacer(Modifier.height(14.dp))
                    SectionHeader("Available assessments")
                    if (data.assessments.isEmpty()) EmptyState("No assessment banks yet.")
                }
                items(data.assessments, key = { it.skill }) { item ->
                    AssessmentCard(item, busy = starting == "${item.skill}-${item.nextLevel}") {
                        if (item.nextLevel != null) rulesFor = item
                    }
                }
            }
        }
    }

    rulesFor?.let { item ->
        val level = item.nextLevel ?: 1
        ExamRulesDialog(
            skill = item.skill, level = level,
            feePaise = item.feePaise ?: 0, timeLimitSeconds = item.timeLimitSeconds ?: 300,
            onDismiss = { rulesFor = null },
            onStart = {
                rulesFor = null
                starting = "${item.skill}-$level"
                scope.launch {
                    when (val r = graph.assessments.start(item.skill, level)) {
                        is ApiResult.Ok -> {
                            val attempt = r.data.attempt
                            if (attempt.paymentStatus == "paid") nav.push(Screen.Exam(attempt.id, item.skill, level))
                            else toast("Could not start the exam.")
                        }
                        is ApiResult.Err -> toast(r.message)
                    }
                    starting = null
                }
            }
        )
    }
}

@Composable
private fun AssessmentCard(item: AssessmentItem, busy: Boolean, onStart: () -> Unit) {
    ListCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.skill, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (item.badgeLevel > 0) StatusChip("Level ${item.badgeLevel}")
        }
        Spacer(Modifier.height(6.dp))
        if (item.nextLevel == null) {
            Text("Top level achieved ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        } else {
            val mins = (item.timeLimitSeconds ?: 300) / 60
            Text(
                "Next: Level ${item.nextLevel} · ${item.feePaise?.let { formatMoney(it) } ?: "₹9"} · $mins min timed",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStart, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Start Level ${item.nextLevel}")
            }
        }
    }
}

@Composable
private fun ExamRulesDialog(
    skill: String, level: Int, feePaise: Long, timeLimitSeconds: Int,
    onDismiss: () -> Unit, onStart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$skill · Level $level exam") },
        text = {
            Column {
                Text("Fee: ${formatMoney(feePaise)} (from your wallet, non-refundable)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Proctored exam rules:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "• ${timeLimitSeconds / 60} minute timer — auto-submits at 0\n" +
                        "• One question at a time — you cannot go back\n" +
                        "• Screenshots and screen recording are blocked\n" +
                        "• Leaving the app warns you; leaving more than 3 times auto-closes the exam\n" +
                        "• 60% correct to pass and earn the badge",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onStart) { Text("Pay ${formatMoney(feePaise)} & start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ExamScreen(attemptId: String, skill: String, level: Int, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var questions by remember { mutableStateOf<List<AssessmentQuestion>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var remaining by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<SubmitResultResponse?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var warnings by remember { mutableIntStateOf(0) }
    val finished = result != null

    // Single submit path (manual / timeout / forfeit), guarded against double-fire.
    suspend fun finish(forfeited: Boolean) {
        if (submitting || finished) return
        submitting = true
        when (val r = graph.assessments.submit(attemptId, answers, forfeited, proctoringViolations = warnings)) {
            is ApiResult.Ok -> { result = r.data; graph.session.refresh() }
            is ApiResult.Err -> { toast(r.message); submitting = false }
        }
    }

    // FLAG_SECURE: block screenshots, screen recording and screen sharing while
    // the exam is on screen; cleared when leaving.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // App-switch / background warns each time and is counted. The count is sent
    // on submit: up to REVIEW_THRESHOLD events auto-validate; more than that holds
    // the result for admin review (the exam is NOT force-closed). The timer keeps
    // running server-side, so you can't gain time either way.
    val reviewThreshold = 5
    DisposableEffect(lifecycleOwner) {
        var leftDuringExam = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!finished && questions.isNotEmpty()) leftDuringExam = true
                Lifecycle.Event.ON_RESUME -> if (leftDuringExam && !finished) {
                    leftDuringExam = false
                    warnings += 1
                    if (warnings > reviewThreshold) {
                        toast("Proctoring flagged ($warnings events) — your result will be held for admin review.")
                    } else {
                        toast("Proctoring warning $warnings — please don't leave the exam.")
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // No going back out of the exam mid-attempt.
    BackHandler(enabled = !finished && questions.isNotEmpty()) { /* swallow */ }

    LaunchedEffect(attemptId) {
        when (val r = graph.assessments.questions(attemptId)) {
            is ApiResult.Ok -> {
                questions = r.data.questions
                remaining = r.data.attempt.remainingSeconds.takeIf { it > 0 } ?: r.data.attempt.timeLimitSeconds
            }
            is ApiResult.Err -> loadError = r.message
        }
    }

    // Countdown — auto-submit at 0.
    LaunchedEffect(questions, finished) {
        if (questions.isEmpty() || finished) return@LaunchedEffect
        while (remaining > 0 && !finished) { delay(1000); remaining -= 1 }
        if (!finished) finish(forfeited = false)
    }

    TopBarScaffold(title = "$skill · Level $level", onBack = { /* disabled during exam */ }) { m ->
        when {
            finished -> ResultView(result!!, skill, level) { nav.pop() }
            loadError != null -> Column(m.fillMaxSize().padding(24.dp)) { Text(loadError!!, color = MaterialTheme.colorScheme.error) }
            questions.isEmpty() -> Column(m.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            else -> {
                val q = questions[index]
                Column(m.fillMaxSize().padding(16.dp)) {
                    // Timer + progress
                    val danger = remaining <= 30
                    Surface(color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Q ${index + 1}/${questions.size}" + if (warnings > 0) "  ⚠ $warnings" else "", fontWeight = FontWeight.SemiBold)
                            Text("⏱ ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", fontWeight = FontWeight.Bold)
                        }
                    }
                    LinearProgressIndicator(progress = { (index + 1).toFloat() / questions.size }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                        Text("${index + 1}. ${q.question}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        q.options.forEach { option ->
                            val selected = answers[q.id] == option.key
                            Surface(
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .selectable(selected = selected, onClick = { answers = answers + (q.id to option.key) })
                            ) {
                                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selected, onClick = { answers = answers + (q.id to option.key) })
                                    Text(option.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    val isLast = index == questions.size - 1
                    Button(
                        enabled = answers.containsKey(q.id) && !submitting,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (isLast) scope.launch { finish(forfeited = false) }
                            else index += 1
                        }
                    ) {
                        if (submitting) CircularProgressIndicator(Modifier.height(18.dp))
                        else Text(if (isLast) "Submit exam" else "Next question")
                    }
                    Text("You can't return to previous questions.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultView(result: SubmitResultResponse, skill: String, level: Int, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val title = when {
            result.inReview -> "⏳ Under review"
            result.passed -> "🏅 Passed!"
            result.reason == "forfeited" -> "Exam ended"
            result.reason == "timed_out" -> "Time's up"
            else -> "Not passed yet"
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = if (result.passed && !result.inReview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text("Score: ${result.score} / ${result.total}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        when {
            result.inReview -> Text(
                "Your exam was flagged by proctoring (${result.violations} events), so the result is held for admin review. " +
                    "Provisional result: ${if (result.provisionalPassed) "pass" else "fail"}. " +
                    "You'll be notified once an admin approves it — your certificate is issued only after approval.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            result.passed && result.badge != null -> {
                Pill("${result.badge!!.skill} · Level ${result.badge!!.level}")
                Spacer(Modifier.height(8.dp))
                Text("Badge & certificate added to your public profile.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            result.reason == "forfeited" -> Text("You left the app during the exam, so it was ended. The fee isn't refundable.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            result.reason == "timed_out" -> Text("You ran out of time. You can buy another attempt to retry.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            else -> Text("You need 60% to pass. You can buy another attempt for Level $level $skill.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
