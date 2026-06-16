package com.twowork.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.AssessmentItem
import com.twowork.core.model.SubmitResultResponse
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.Pill
import com.twowork.core.ui.SectionHeader
import com.twowork.core.ui.StatusChip
import com.twowork.core.ui.TagRow
import com.twowork.core.ui.formatMoney
import kotlinx.coroutines.launch

@Composable
fun AssessmentsScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var starting by remember { mutableStateOf<String?>(null) }

    TopBarScaffold(title = "Skills & Certification", onBack = { nav.pop() }) { m ->
        ApiContent(loader = { graph.assessments.available() }, modifier = m) { data ->
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    Text(
                        "Certify the skills on your profile. Pass a 3-level exam (60% to pass) to earn a public badge shown to clients.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (data.badges.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        SectionHeader("Your badges")
                        TagRow(data.badges.map { "${it.skill} · L${it.level}" })
                    }
                    Spacer(Modifier.height(14.dp))
                    SectionHeader("Available assessments")
                    if (data.assessments.isEmpty()) {
                        EmptyState("Add skills to your profile to unlock assessments. Only skills with an exam bank appear here.")
                    }
                }
                items(data.assessments, key = { it.skill }) { item ->
                    AssessmentCard(item, busy = starting == "${item.skill}-${item.nextLevel}") {
                        val level = item.nextLevel ?: return@AssessmentCard
                        starting = "${item.skill}-$level"
                        scope.launch {
                            when (val r = graph.assessments.start(item.skill, level)) {
                                is ApiResult.Ok -> {
                                    val attempt = r.data.attempt
                                    if (r.data.razorpay == null && attempt.paymentStatus == "paid") {
                                        nav.push(Screen.Exam(attempt.id, item.skill, level))
                                    } else {
                                        toast("Pay the exam fee on 2work.in to start this level.")
                                    }
                                }
                                is ApiResult.Err -> toast(r.message)
                            }
                            starting = null
                        }
                    }
                }
            }
        }
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
            Text(
                "Next: Level ${item.nextLevel}" + (item.feePaise?.let { " · ${formatMoney(it)}" } ?: " · free while in beta"),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onStart, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Start Level ${item.nextLevel} exam")
            }
        }
    }
}

@Composable
fun ExamScreen(attemptId: String, skill: String, level: Int, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var result by remember { mutableStateOf<SubmitResultResponse?>(null) }
    var submitting by remember { mutableStateOf(false) }

    TopBarScaffold(title = "$skill · Level $level", onBack = { nav.pop() }) { m ->
        val finished = result
        if (finished != null) {
            ResultView(finished, skill, level) { nav.pop() }
        } else {
            ApiContent(loader = { graph.assessments.questions(attemptId) }, modifier = m) { data ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        "Answer all ${data.questions.size} questions. 60% to pass.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    data.questions.forEachIndexed { index, question ->
                        Spacer(Modifier.height(16.dp))
                        Text("${index + 1}. ${question.question}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        question.options.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().clickable { answers = answers + (question.id to option.key) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = answers[question.id] == option.key, onClick = { answers = answers + (question.id to option.key) })
                                Text(option.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        enabled = !submitting && answers.size == data.questions.size,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            submitting = true
                            scope.launch {
                                when (val r = graph.assessments.submit(attemptId, answers)) {
                                    is ApiResult.Ok -> { result = r.data; graph.session.refresh() }
                                    is ApiResult.Err -> { toast(r.message); submitting = false }
                                }
                            }
                        }
                    ) { if (submitting) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Submit answers") }
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
        Text(
            if (result.passed) "🏅 Passed!" else "Not passed yet",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text("Score: ${result.score} / ${result.total}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (result.passed && result.badge != null) {
            Pill("${result.badge!!.skill} · Level ${result.badge!!.level}")
            Spacer(Modifier.height(8.dp))
            Text("Badge added to your public profile.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else {
            Text("You need 60% to pass. You can retake Level $level for $skill.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
