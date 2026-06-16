package com.twowork.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.twowork.core.PasswordPolicy
import com.twowork.core.di.LocalGraph
import com.twowork.core.net.ApiResult
import kotlinx.coroutines.launch

private enum class AuthMode { Login, Register, Forgot, Verify }

/** Password input with a trailing eye icon that toggles masking. */
@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        modifier = modifier
    )
}

/** Live checklist of the server password rules; met items are ticked and muted. */
@Composable
private fun PasswordRequirements(password: String) {
    Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
        PasswordPolicy.rules.forEach { rule ->
            val ok = rule.satisfiedBy(password)
            Text(
                text = (if (ok) "✓ " else "• ") + rule.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AuthFlow() {
    var mode by remember { mutableStateOf(AuthMode.Login) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("2Work", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Verified freelance marketplace", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        when (mode) {
            AuthMode.Login -> LoginForm(
                onRegister = { mode = AuthMode.Register },
                onForgot = { mode = AuthMode.Forgot },
                onVerify = { mode = AuthMode.Verify }
            )
            AuthMode.Register -> RegisterForm(onLogin = { mode = AuthMode.Login })
            AuthMode.Forgot -> ForgotForm(onBack = { mode = AuthMode.Login })
            AuthMode.Verify -> VerifyForm(onBack = { mode = AuthMode.Login })
        }
    }
}

@Composable
private fun LoginForm(onRegister: () -> Unit, onForgot: () -> Unit, onVerify: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var showTotp by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Text("Sign in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    PasswordField(password, { password = it }, "Password", Modifier.fillMaxWidth())
    if (showTotp) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(totp, { totp = it.filter(Char::isDigit).take(6) }, label = { Text("Authenticator code (admins)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    }
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            error = null; loading = true
            scope.launch {
                when (val r = graph.session.login(email, password, totp)) {
                    is ApiResult.Ok -> {}
                    is ApiResult.Err -> { error = r.message; loading = false }
                }
            }
        },
        enabled = !loading && email.isNotBlank() && password.length >= 6,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (loading) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Sign in")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onForgot) { Text("Forgot password?") }
        TextButton(onClick = { showTotp = !showTotp }) { Text(if (showTotp) "Hide 2FA" else "Have a 2FA code?") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("New to 2Work?")
        TextButton(onClick = onRegister) { Text("Create account") }
    }
    TextButton(onClick = onVerify, modifier = Modifier.fillMaxWidth()) { Text("Verify email with a code") }
}

@Composable
private fun RegisterForm(onLogin: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf("freelancer") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Text("Create account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = role == "freelancer", onClick = { role = "freelancer" }, label = { Text("Freelancer") })
        FilterChip(selected = role == "client", onClick = { role = "client" }, label = { Text("Project host") })
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    PasswordField(password, { password = it }, "Password", Modifier.fillMaxWidth())
    if (password.isNotEmpty()) PasswordRequirements(password)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(terms, { terms = it })
        Text("I accept the Terms and Privacy Policy", style = MaterialTheme.typography.bodySmall)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            error = null; info = null; loading = true
            scope.launch {
                when (val r = graph.session.register(email, password, fullName, role)) {
                    is ApiResult.Ok -> { loading = false; info = "Account created. Check your email to verify, then sign in." }
                    is ApiResult.Err -> { loading = false; error = r.message }
                }
            }
        },
        enabled = !loading && terms && fullName.isNotBlank() && email.isNotBlank() && PasswordPolicy.isValid(password),
        modifier = Modifier.fillMaxWidth()
    ) { if (loading) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Create account") }
    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Already have an account? Sign in") }
}

@Composable
private fun ForgotForm(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Text("Reset password", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
    Spacer(Modifier.height(12.dp))
    Button(onClick = {
        scope.launch {
            message = when (val res = graph.session.forgotPassword(email)) {
                is ApiResult.Ok -> res.data.message.ifBlank { "If the account is eligible, a reset email has been sent." }
                is ApiResult.Err -> res.message
            }
        }
    }, enabled = email.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send reset link") }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
}

@Composable
private fun VerifyForm(onBack: () -> Unit) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Text("Verify email", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text("Paste the token from your verification email.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(token, { token = it.trim() }, label = { Text("Verification token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
    Spacer(Modifier.height(12.dp))
    Button(onClick = {
        scope.launch {
            message = when (val res = graph.session.verifyEmail(token)) {
                is ApiResult.Ok -> res.data.message.ifBlank { "Email verified. You can sign in." }
                is ApiResult.Err -> res.message
            }
        }
    }, enabled = token.length >= 32, modifier = Modifier.fillMaxWidth()) { Text("Verify") }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
}
