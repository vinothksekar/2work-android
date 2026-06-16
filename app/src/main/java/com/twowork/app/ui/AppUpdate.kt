package com.twowork.app.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.UpdateInfo
import com.twowork.core.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Self-hosted in-app updater: compares the installed version with the server
 *  manifest, downloads the APK via DownloadManager, and launches the installer. */
object AppUpdater {
    private const val FILE_NAME = "2work-update.apk"
    private const val APK_MIME = "application/vnd.android.package-archive"

    fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }

    fun isUpdateAvailable(context: Context, info: UpdateInfo): Boolean =
        info.versionCode > currentVersionCode(context) && info.apkUrl.isNotBlank()

    /** Returns null on success (installer launched), otherwise an error message. */
    suspend fun downloadAndInstall(context: Context, apkUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)
            if (target.exists()) target.delete()
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("2Work update")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, FILE_NAME)
            val id = manager.enqueue(request)
            var error: String? = "The download did not complete"
            var done = false
            while (!done) {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        error = "The download was cancelled"; done = true
                    } else when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> { install(context, target); error = null; done = true }
                        DownloadManager.STATUS_FAILED -> { error = "The download failed"; done = true }
                    }
                }
                if (!done) delay(600)
            }
            error
        } catch (e: Exception) {
            e.message ?: "Update failed"
        }
    }

    private fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, downloading: Boolean, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!info.mandatory && !downloading) onDismiss() },
        title = { Text("Update available") },
        text = {
            Text(
                buildString {
                    append("Version ${info.versionName} is available.")
                    if (info.notes.isNotBlank()) append("\n\n${info.notes}")
                    if (info.mandatory) append("\n\nThis update is required to continue.")
                    if (downloading) append("\n\nDownloading…")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) {
                if (downloading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Update")
            }
        },
        dismissButton = {
            if (!info.mandatory) TextButton(onClick = onDismiss, enabled = !downloading) { Text("Later") }
        }
    )
}

/** Silent check on launch; surfaces the dialog when a newer build is published. */
@Composable
fun AppUpdateGate() {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = graph.appUpdate.latest()
        if (result is ApiResult.Ok && AppUpdater.isUpdateAvailable(context, result.data)) {
            update = result.data
        }
    }

    val info = update
    if (info != null && !dismissed) {
        UpdateDialog(
            info = info,
            downloading = downloading,
            onUpdate = {
                downloading = true
                scope.launch {
                    val error = AppUpdater.downloadAndInstall(context, info.apkUrl)
                    downloading = false
                    if (error != null) toast(error)
                }
            },
            onDismiss = { dismissed = true }
        )
    }
}
