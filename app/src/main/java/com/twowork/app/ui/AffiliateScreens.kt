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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.AffiliateReferral
import com.twowork.core.model.AffiliateResponse
import com.twowork.core.ui.EmptyState
import com.twowork.core.ui.ListCard
import com.twowork.core.ui.StatusChip
import com.twowork.core.ui.formatMoney
import com.twowork.core.ui.prettyStatus

@Composable
fun AffiliateScreen(nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val toast = rememberToaster()
    val clipboard = LocalClipboardManager.current
    TopBarScaffold(title = "Affiliate & referrals", onBack = { nav.pop() }) { m ->
        ApiContent(loader = { graph.wallet.affiliate() }, modifier = m) { a: AffiliateResponse ->
            LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ListCard {
                        Text("Share & earn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Share your link. When someone signs up with it and buys a paid plan, you earn ${a.commissionPercent}% of their plan purchase into your wallet.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(a.shareUrl, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { clipboard.setText(AnnotatedString(a.shareUrl)); toast("Affiliate link copied") }) { Text("Copy link") }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Referrals", a.stats.total.toString())
                            Stat("Converted", a.stats.converted.toString())
                            Stat("Earned", formatMoney(a.stats.earnedPaise))
                        }
                    }
                }
                item { Text("Your referrals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                if (a.referrals.isEmpty()) item { EmptyState("No referrals yet. Share your link to start earning.") }
                items(a.referrals) { ReferralRow(it) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReferralRow(r: AffiliateReferral) {
    ListCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier) {
                Text(r.fullName, fontWeight = FontWeight.SemiBold)
                Text(r.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                StatusChip(if (r.status == "converted") "verified" else "pending")
                if (r.commissionPaise > 0) Text(formatMoney(r.commissionPaise), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
