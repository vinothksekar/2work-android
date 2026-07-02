package com.twowork.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.WorkScreenshot
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun WorkDiaryScreen(contractId: String, contractTitle: String, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast = rememberToaster()
    var reload by remember { mutableStateOf(0) }
    var uploading by remember { mutableStateOf(false) }

    // Camera/gallery picker — compress to JPEG, encode base64, upload as thumbnail_data
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@runCatching null
                    // Downscale to thumbnail size
                    val scaled = Bitmap.createScaledBitmap(bmp, 320,
                        (320 * bmp.height / bmp.width.coerceAtLeast(1)).coerceAtLeast(1), true)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
                    "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }.getOrNull()
            }
            if (result == null) { toast("Could not process image"); uploading = false; return@launch }
            when (val r = graph.workDiary.upload(contractId, result)) {
                is ApiResult.Ok -> { toast("Screenshot added"); reload++ }
                is ApiResult.Err -> toast(r.message)
            }
            uploading = false
        }
    }

    var billing by remember { mutableStateOf<List<com.twowork.core.model.BillingPeriod>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(reload) {
        (graph.workDiary.billing(contractId) as? ApiResult.Ok)?.let { billing = it.data.periods }
    }

    TopBarScaffold(title = "WorkDiary: $contractTitle", onBack = { nav.pop() }) { m ->
        ApiContent(loaderKey = reload, loader = { graph.workDiary.screenshots(contractId) }, modifier = m) { resp ->
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${resp.screenshots.size} screenshot${if (resp.screenshots.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall)
                    Button(onClick = { imagePicker.launch("image/*") }, enabled = !uploading) {
                        Text(if (uploading) "Uploading…" else "Add photo")
                    }
                }
                if (billing.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text("Weekly billing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        billing.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${"%.1f".format(p.minutes / 60.0)}h tracked" +
                                        (p.periodEnd?.let { " · to ${it.take(10)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(com.twowork.core.ui.formatMoney(p.grossPaise), fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                HorizontalDivider()
                if (resp.screenshots.isEmpty()) {
                    EmptyState("No screenshots yet — use the desktop app for auto captures, or tap \"Add photo\".")
                } else {
                    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(resp.screenshots) { shot ->
                            ScreenshotRow(shot)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotRow(shot: WorkScreenshot) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThumbnailImage(shot.thumbnailData, Modifier.size(72.dp).aspectRatio(1.6f))
        Column(Modifier.weight(1f)) {
            Text(shot.capturedAt?.take(16)?.replace("T", " ") ?: "–",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("WorkDiary capture", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThumbnailImage(dataUri: String, modifier: Modifier = Modifier) {
    val bmp = remember(dataUri) {
        runCatching {
            val b64 = if (dataUri.contains(",")) dataUri.substringAfter(",") else dataUri
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bmp != null) {
        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Screenshot", modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            Text("?", Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
