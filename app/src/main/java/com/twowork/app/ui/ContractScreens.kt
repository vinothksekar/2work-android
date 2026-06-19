package com.twowork.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.app.RazorpayBridge
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.*
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.StatusChip
import com.twowork.core.ui.formatMoney
import com.twowork.core.ui.prettyStatus
import kotlinx.coroutines.launch

private sealed interface MsAction {
    data class Deliver(val milestoneId: String) : MsAction
    data class Revision(val deliverableId: String) : MsAction
    data class Dispute(val milestoneId: String) : MsAction
    data class RateFreelancer(val milestoneId: String) : MsAction
    data class RateClient(val milestoneId: String) : MsAction
    data class CancelContract(val contractId: String) : MsAction
}

@Composable
fun ContractsScreen(user: User, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var action by remember { mutableStateOf<MsAction?>(null) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Contracts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ApiContent(loaderKey = reload, loader = { graph.contracts.contracts() }) { resp ->
            if (resp.contracts.isEmpty()) EmptyState("No contracts yet.")
            else LazyColumn {
                items(resp.contracts, key = { it.id }) { contract ->
                    ContractCard(user, contract,
                        onFund = { milestoneId ->
                            scope.launch {
                                when (val r = graph.contracts.fund(milestoneId)) {
                                    is ApiResult.Ok -> {
                                        val rzp = r.data.razorpay
                                        val ref = r.data.payment?.checkoutReference.orEmpty()
                                        if (rzp != null && ref.isNotEmpty()) {
                                            RazorpayBridge.launch(context as ComponentActivity, rzp, "Milestone funding") { success, paymentId ->
                                                scope.launch {
                                                    if (success && paymentId != null) {
                                                        when (val cap = graph.wallet.capturePayment(ref, paymentId, rzp.orderId)) {
                                                            is ApiResult.Ok -> toast("Milestone funded!")
                                                            is ApiResult.Err -> toast("Payment received — ${cap.message}")
                                                        }
                                                        reload++
                                                    } else if (!success) toast(paymentId ?: "Payment cancelled")
                                                }
                                            }
                                        } else {
                                            toast("Funding intent created — provider capture required")
                                            reload++
                                        }
                                    }
                                    is ApiResult.Err -> toast(r.message)
                                }
                            }
                        },
                        onAccept = { deliverableId ->
                            scope.launch { if (graph.contracts.acceptDelivery(deliverableId) is ApiResult.Ok) { toast("Delivery accepted"); reload++ } }
                        },
                        onAction = { action = it }
                    )
                }
            }
        }
    }

    when (val a = action) {
        is MsAction.Deliver -> DeliverDialog(a.milestoneId, { action = null }) { action = null; reload++; toast("Deliverable submitted") }
        is MsAction.Revision -> RevisionDialog(a.deliverableId, { action = null }) { action = null; reload++; toast("Revision requested") }
        is MsAction.Dispute -> DisputeDialog(a.milestoneId, { action = null }) { action = null; reload++; toast("Dispute opened") }
        is MsAction.RateFreelancer -> RateDialog("Rate freelancer", { action = null }) { score, review, done ->
            scope.launch { done(graph.contracts.rateFreelancer(a.milestoneId, RatingRequest(score, review))) }
        }
        is MsAction.RateClient -> RateDialog("Rate client", { action = null }) { score, review, done ->
            scope.launch { done(graph.contracts.rateClient(a.milestoneId, RatingRequest(score, review))) }
        }
        is MsAction.CancelContract -> CancelContractDialog(a.contractId, { action = null }) {
            action = null; reload++; toast("Contract closed — feedback recorded on the freelancer")
        }
        null -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContractCard(
    user: User,
    contract: Contract,
    onFund: (String) -> Unit,
    onAccept: (String) -> Unit,
    onAction: (MsAction) -> Unit
) {
    ListCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(contract.projectTitle ?: "Contract", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusChip(contract.status)
        }
        Text(
            (if (user.isClient) "with ${contract.freelancerName ?: "freelancer"}" else "for ${contract.clientName ?: "client"}") +
                " · ${formatMoney(contract.totalPaise, contract.currency)}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        contract.milestones.forEach { ms ->
            val latestDelivery = contract.deliverables.filter { it.milestoneId == ms.id }.maxByOrNull { it.revisionNo }
            val rated = contract.ratings.any { it.milestoneId == ms.id }
            val clientRated = contract.clientRatings.any { it.milestoneId == ms.id }
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${ms.sequenceNo}. ${ms.title}", style = MaterialTheme.typography.bodyLarge)
                    Text(formatMoney(ms.amountPaise, ms.currency))
                }
                StatusChip(ms.status)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (user.isClient && ms.status == "awaiting_funding")
                        OutlinedButton(onClick = { onFund(ms.id) }) { Text("Fund") }
                    if (user.isFreelancer && (ms.status == "funded" || ms.status == "revision_requested"))
                        OutlinedButton(onClick = { onAction(MsAction.Deliver(ms.id)) }) { Text("Submit work") }
                    if (user.isClient && ms.status == "submitted" && latestDelivery?.status == "submitted") {
                        OutlinedButton(onClick = { onAccept(latestDelivery.id) }) { Text("Accept") }
                        OutlinedButton(onClick = { onAction(MsAction.Revision(latestDelivery.id)) }) { Text("Revise") }
                    }
                    if (ms.status in listOf("funded", "submitted", "revision_requested", "accepted_pending_release"))
                        OutlinedButton(onClick = { onAction(MsAction.Dispute(ms.id)) }) { Text("Dispute") }
                    if (user.isClient && ms.status == "paid" && !rated)
                        OutlinedButton(onClick = { onAction(MsAction.RateFreelancer(ms.id)) }) { Text("Rate") }
                    if (user.isFreelancer && ms.status == "paid" && !clientRated)
                        OutlinedButton(onClick = { onAction(MsAction.RateClient(ms.id)) }) { Text("Rate client") }
                }
                latestDelivery?.let {
                    Text("Delivery r${it.revisionNo}: ${prettyStatus(it.status)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (user.isClient && contract.status == "active" && contract.milestones.all { it.status == "awaiting_funding" }) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { onAction(MsAction.CancelContract(contract.id)) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Cancel & close with feedback") }
        }
    }
}

@Composable
private fun DeliverDialog(milestoneId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var summary by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Submit deliverable") },
        text = {
            Column {
                OutlinedTextField(summary, { summary = it }, label = { Text("Summary (20+ chars)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Artifact URL (https)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = summary.length >= 20 && url.startsWith("https://"), onClick = {
                scope.launch {
                    val r = graph.contracts.deliver(milestoneId, DeliverableRequest(summary, url))
                    if (r is ApiResult.Ok) onDone() else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RevisionDialog(deliverableId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var feedback by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Request revision") },
        text = { OutlinedTextField(feedback, { feedback = it }, label = { Text("Feedback (10+ chars)") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(enabled = feedback.length >= 10, onClick = {
                scope.launch {
                    val r = graph.contracts.requestRevision(deliverableId, FeedbackRequest(feedback))
                    if (r is ApiResult.Ok) onDone() else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun DisputeDialog(milestoneId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var reason by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Open dispute") },
        text = {
            Column {
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason (20+ chars)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(evidence, { evidence = it }, label = { Text("Evidence (10+ chars)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = reason.length >= 20 && evidence.length >= 10, onClick = {
                scope.launch {
                    val r = graph.contracts.dispute(milestoneId, DisputeRequest(reason, evidence))
                    if (r is ApiResult.Ok) onDone() else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun RateDialog(title: String, onDismiss: () -> Unit, submit: (Int, String, (ApiResult<Unit>) -> Unit) -> Unit) {
    val toast = rememberToaster()
    var score by remember { mutableIntStateOf(5) }
    var review by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n ->
                        OutlinedButton(onClick = { score = n }) { Text(if (n <= score) "★" else "☆") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(review, { review = it }, label = { Text("Review (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submit(score, review) { r ->
                    if (r is ApiResult.Ok) onDismiss() else if (r is ApiResult.Err) toast(r.message)
                }
            }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun CancelContractDialog(contractId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var score by remember { mutableIntStateOf(2) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cancel & close contract") },
        text = {
            Column {
                Text(
                    "Use this if the freelancer is unresponsive. The contract and project close, and your feedback is recorded on the freelancer's public rating. Only available before any milestone is funded.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("Rating for the freelancer", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n ->
                        OutlinedButton(onClick = { score = n }) { Text(if (n <= score) "★" else "☆") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason (10+ chars)") },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = reason.trim().length >= 10, onClick = {
                scope.launch {
                    when (val r = graph.contracts.cancel(contractId, reason.trim(), score)) {
                        is ApiResult.Ok -> onDone()
                        is ApiResult.Err -> toast(r.message)
                    }
                }
            }) { Text("Close contract") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep open") } })
}
