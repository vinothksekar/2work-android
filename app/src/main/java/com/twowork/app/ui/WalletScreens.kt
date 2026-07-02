package com.twowork.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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

private sealed interface WalletDialog {
    data object Topup : WalletDialog
    data object Settlement : WalletDialog
    data object Bank : WalletDialog
}

@Composable
fun WalletScreen(user: User, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<WalletDialog?>(null) }

    TopBarScaffold(title = "Wallet & plan", onBack = { nav.pop() }) { m ->
        ApiContent(loaderKey = reload, loader = { graph.wallet.wallet() }, modifier = m) { wallet ->
            var sub by remember(reload) { mutableStateOf<SubscriptionResponse?>(null) }
            var settle by remember(reload) { mutableStateOf<SettlementsResponse?>(null) }
            var quota by remember(reload) { mutableStateOf<ApplyQuota?>(null) }
            var invoices by remember(reload) { mutableStateOf<List<Invoice>>(emptyList()) }
            androidx.compose.runtime.LaunchedEffect(reload) {
                (graph.wallet.subscription() as? ApiResult.Ok)?.let { sub = it.data }
                (graph.wallet.settlements() as? ApiResult.Ok)?.let { settle = it.data }
                if (user.isFreelancer) (graph.wallet.quota() as? ApiResult.Ok)?.let { quota = it.data.quota }
                (graph.wallet.invoices() as? ApiResult.Ok)?.let { invoices = it.data.invoices }
            }
            LazyColumn(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                item { BalanceCard(wallet, onTopup = { dialog = WalletDialog.Topup }, onWithdraw = { dialog = WalletDialog.Settlement }) }
                item { PlanCard(sub, quota, wallet.wallet.balancePaise, onSubscribe = { plan, method ->
                    scope.launch {
                        when (val r = graph.wallet.subscribe(plan, method)) {
                            is ApiResult.Ok -> {
                                val rzp = r.data.razorpay
                                if (rzp != null) {
                                    RazorpayBridge.launch(context as ComponentActivity, rzp, "$plan plan") { success, paymentId ->
                                        scope.launch {
                                            if (success && paymentId != null) {
                                                when (val cap = graph.wallet.capturePayment(rzp.reference, paymentId, rzp.orderId)) {
                                                    is ApiResult.Ok -> toast("Plan activated!")
                                                    is ApiResult.Err -> toast("Payment received — ${cap.message}")
                                                }
                                                reload++
                                            } else if (!success) toast(paymentId ?: "Payment cancelled")
                                        }
                                    }
                                } else {
                                    when {
                                        plan == "free" -> toast("Switched to Free plan")
                                        r.data.method == "wallet" -> toast(if (r.data.isUpgrade) "Plan upgraded from wallet" else "Plan activated from wallet")
                                        else -> toast("Plan updated")
                                    }
                                    reload++
                                }
                            }
                            is ApiResult.Err -> toast(r.message)
                        }
                    }
                }) }
                item { BankSettlementCard(settle, onAddBank = { dialog = WalletDialog.Bank }) }
                item { Text("Wallet activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                if (wallet.transactions.isEmpty()) item { EmptyState("No wallet activity yet.") }
                items(wallet.transactions, key = { it.id }) { TxnRow(it) }
                item { Text("Invoices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                if (invoices.isEmpty()) item { EmptyState("No invoices yet. Invoices are generated for plan purchases and platform fees.") }
                items(invoices, key = { "inv-" + it.id }) { InvoiceRow(it) }
            }
        }
    }

    when (dialog) {
        WalletDialog.Topup -> AmountDialog("Add money to wallet",
            "Demo mode credits instantly; live payments use Razorpay checkout.", "Add money",
            onDismiss = { dialog = null }) { amount, done ->
            scope.launch {
                when (val r = graph.wallet.topup(amount)) {
                    is ApiResult.Ok -> {
                        done()
                        val rzp = r.data.razorpay
                        if (rzp != null) {
                            dialog = null
                            RazorpayBridge.launch(context as ComponentActivity, rzp, "Wallet top-up") { success, paymentId ->
                                scope.launch {
                                    if (success && paymentId != null) {
                                        when (val cap = graph.wallet.capturePayment(rzp.reference, paymentId, rzp.orderId)) {
                                            is ApiResult.Ok -> toast("Wallet credited!")
                                            is ApiResult.Err -> toast("Payment received — ${cap.message}")
                                        }
                                        reload++
                                    } else if (!success) toast(paymentId ?: "Payment cancelled")
                                }
                            }
                        } else {
                            dialog = null; reload++; toast("Wallet updated")
                        }
                    }
                    is ApiResult.Err -> { done(); toast(r.message) }
                }
            }
        }
        WalletDialog.Settlement -> AmountDialog("Withdraw to bank",
            "Free. Minimum ₹1,000. Requires KYC + an approved bank account.", "Request settlement",
            onDismiss = { dialog = null }) { amount, done ->
            scope.launch { val r = graph.wallet.requestSettlement(amount); if (r is ApiResult.Ok) { done(); dialog = null; reload++; toast("Settlement requested") } else if (r is ApiResult.Err) { done(); toast(r.message) } }
        }
        WalletDialog.Bank -> BankDialog(onDismiss = { dialog = null }) { body, done ->
            scope.launch { val r = graph.wallet.saveBank(body); if (r is ApiResult.Ok) { done(); dialog = null; reload++; toast("Bank saved — pending approval") } else if (r is ApiResult.Err) { done(); toast(r.message) } }
        }
        null -> {}
    }
}

@Composable
private fun BalanceCard(wallet: WalletResponse, onTopup: () -> Unit, onWithdraw: () -> Unit) {
    ListCard {
        Text("Wallet balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatMoney(wallet.wallet.balancePaise, wallet.wallet.currency),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = if (wallet.wallet.balancePaise < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (wallet.wallet.balancePaise < 0) {
            Text("Your wallet is negative (commission charged). Add money to clear it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTopup) { Text("Add money") }
            OutlinedButton(onClick = onWithdraw) { Text("Withdraw") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniStat("Earned", formatMoney(wallet.stats.totalEarnedPaise))
            MiniStat("Commission", formatMoney(wallet.stats.totalCommissionPaise))
            MiniStat("Settled", formatMoney(wallet.stats.totalSettledPaise))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private val PLAN_RANK = mapOf("free" to 0, "elite" to 1, "premium" to 2)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    sub: SubscriptionResponse?, quota: ApplyQuota?, walletPaise: Long,
    onSubscribe: (plan: String, method: String?) -> Unit
) {
    ListCard {
        Text("Subscription plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val active = sub?.activePlan
        val activeId = active?.id ?: "free"
        val endIso = sub?.subscription?.currentPeriodEnd
        val endMs = endIso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        val now = System.currentTimeMillis()
        val stillActive = activeId != "free" && endMs != null && endMs > now
        val activePrice = sub?.plans?.find { it.id == activeId }?.pricePaise ?: 0L
        Text("Current: ${active?.label ?: "Free"} · ${active?.commissionPercent ?: 10.0}% commission" +
            (if (stillActive && endIso != null) " · until ${endIso.take(10)}" else ""),
            style = MaterialTheme.typography.bodyMedium)
        quota?.let {
            Text("Applies left today: ${it.available} of ${it.granted} (${it.planLabel})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        (sub?.plans ?: emptyList()).forEach { plan ->
            val rank = PLAN_RANK[plan.id] ?: 0
            val activeRank = PLAN_RANK[activeId] ?: 0
            val isUpgrade = stillActive && rank > activeRank
            val charge: Long = if (isUpgrade && endMs != null) {
                val frac = ((endMs - now).toDouble() / (30.0 * 24 * 3600 * 1000)).coerceIn(0.0, 1.0)
                maxOf(100L, Math.ceil((plan.pricePaise - activePrice) * frac).toLong())
            } else plan.pricePaise
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${plan.label} · ${if (plan.pricePaise > 0) formatMoney(plan.pricePaise) + "/mo" else "Free"}",
                            fontWeight = FontWeight.SemiBold)
                        Text("${plan.commissionPercent}% fee · ${plan.applyStart} applies/day +${plan.refillAmount}/${plan.refillIntervalHours}h",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isUpgrade) Text("Upgrade now · prorated ${formatMoney(charge)}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (plan.id == activeId) StatusChip("current")
                }
                when {
                    plan.id == activeId -> {}
                    plan.id == "free" ->
                        if (stillActive)
                            Text("Reverts to Free on ${endIso?.take(10) ?: "expiry"}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else OutlinedButton(onClick = { onSubscribe("free", null) }) { Text("Switch to Free") }
                    stillActive && rank < activeRank ->
                        Text("Available after ${endIso?.take(10) ?: "expiry"}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (walletPaise >= charge)
                            Button(onClick = { onSubscribe(plan.id, "wallet") }) { Text("Pay from wallet (${formatMoney(charge)})") }
                        else
                            OutlinedButton(onClick = {}, enabled = false) { Text("Wallet low (${formatMoney(charge)})") }
                        OutlinedButton(onClick = { onSubscribe(plan.id, "razorpay") }) { Text("Pay online") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankSettlementCard(settle: SettlementsResponse?, onAddBank: () -> Unit) {
    ListCard {
        Text("Bank account & settlements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val bank = settle?.bankAccount
        if (bank != null) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${bank.bankName.ifBlank { "Account" }} ••••${bank.accountLast4} · ${bank.ifsc}",
                    style = MaterialTheme.typography.bodyMedium)
                StatusChip(bank.status)
            }
        } else {
            Text("No settlement bank account yet. Add one; an admin approves it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onAddBank) { Text("Add / update bank account") }
        (settle?.settlements ?: emptyList()).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(formatMoney(s.amountPaise), fontWeight = FontWeight.SemiBold)
                StatusChip(s.status)
            }
        }
    }
}

@Composable
private fun InvoiceRow(inv: Invoice) {
    ListCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(inv.invoiceNumber, fontWeight = FontWeight.SemiBold)
                if (inv.description.isNotBlank()) Text(inv.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("GST @ ${inv.gstBps / 100}% included: ${formatMoney(inv.gstPaise)}" +
                        (inv.createdAt?.let { " · ${it.take(10)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatMoney(inv.totalPaise), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TxnRow(t: WalletTransaction) {
    ListCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(prettyStatus(t.kind), fontWeight = FontWeight.SemiBold)
                if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text((if (t.amountPaise < 0) "−" else "+") + formatMoney(kotlin.math.abs(t.amountPaise)),
                fontWeight = FontWeight.Bold,
                color = if (t.amountPaise < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AmountDialog(title: String, help: String, confirm: String, onDismiss: () -> Unit, submit: (String, () -> Unit) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount (INR)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (amount.toDoubleOrNull() ?: 0.0) >= 1.0, onClick = { busy = true; submit(amount) { busy = false } }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun BankDialog(onDismiss: () -> Unit, submit: (BankAccountRequest, () -> Unit) -> Unit) {
    var holder by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val ok = holder.length >= 2 && number.length in 6..20 && Regex("^[A-Za-z]{4}0[A-Za-z0-9]{6}$").matches(ifsc)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Settlement bank account") },
        text = {
            Column {
                Text("Your account number is encrypted; an admin approves the account before settlements.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(holder, { holder = it }, label = { Text("Account holder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it.filter(Char::isDigit).take(20) }, label = { Text("Account number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ifsc, { ifsc = it.uppercase().take(11) }, label = { Text("IFSC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bankName, { bankName = it }, label = { Text("Bank name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = ok && !busy, onClick = { busy = true; submit(BankAccountRequest(holder, number, ifsc, bankName)) { busy = false } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
