package com.twowork.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.StateContent
import com.twowork.core.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { actions() }
            )
        }
    ) { padding -> content(Modifier.padding(padding)) }
}

/** Auto-loads [loader] into a three-state UI with built-in retry. */
@Composable
fun <T> ApiContent(
    loaderKey: Any = Unit,
    loader: suspend () -> ApiResult<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    var reload by remember { mutableIntStateOf(0) }
    var state by remember(loaderKey) { mutableStateOf<UiState<T>>(UiState.Loading) }
    LaunchedEffect(loaderKey, reload) {
        state = UiState.Loading
        state = when (val r = loader()) {
            is ApiResult.Ok -> UiState.Data(r.data)
            is ApiResult.Err -> UiState.Error(r.message)
        }
    }
    StateContent(state, onRetry = { reload++ }, modifier.fillMaxSize()) { content(it) }
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun rememberToaster(): (String) -> Unit {
    val context = LocalContext.current
    return remember { { msg: String -> context.toast(msg) } }
}
