package com.twowork.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twowork.core.KycValidators
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.*
import com.twowork.core.net.ApiResult
import com.twowork.core.ui.InitialBadge
import com.twowork.core.ui.StatusChip
import com.twowork.core.ui.formatMoney
import com.twowork.core.ui.prettyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(user: User, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toast = rememberToaster()
    var checkingUpdate by remember { mutableStateOf(false) }
    var manualUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var manualDownloading by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialBadge(user.fullName)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(user.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(user.role)
                    user.kycStatus?.let { StatusChip("kyc_$it") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ApiContent(loader = { graph.profile.dashboard() }) { resp ->
            val s = resp.stats
            Text("At a glance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            resp.money?.let { mny ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Wallet balance", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(mny.balancePaise), fontWeight = FontWeight.SemiBold,
                        color = if (mny.balancePaise < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                if (user.isFreelancer) MoneyRow("Total earned", formatMoney(mny.totalEarnedPaise))
                MoneyRow("Commission paid", formatMoney(mny.totalCommissionPaise))
                MoneyRow("Plan", "${mny.plan.label} · ${mny.plan.commissionPercent}% fee")
            }
            resp.rating?.let { r -> MoneyRow("Your rating", "★ %.1f (%d)".format(r.average, r.count)) }
            val pairs = buildList {
                s.projects?.let { add("Projects" to it) }
                s.proposals?.let { add("Proposals" to it) }
                s.contracts?.let { add("Contracts" to it) }
                s.activeMilestones?.let { add("Active milestones" to it) }
                s.ratings?.let { add("Ratings" to it) }
                s.conversations?.let { add("Conversations" to it) }
            }
            pairs.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$value", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
        AccountRow("Wallet & plan") { nav.push(Screen.Wallet) }
        AccountRow("Affiliate & referrals") { nav.push(Screen.Affiliate) }
        AccountRow("Edit profile") { nav.push(Screen.EditProfile) }
        AccountRow("Identity verification") { nav.push(Screen.Verification) }
        if (user.isFreelancer) AccountRow("Skills & Certification") { nav.push(Screen.Assessments) }
        if (user.isAdmin) AccountRow("Admin tools (wallet / users / exams)") { nav.push(Screen.AdminExtras) }
        AccountRow("Notifications") { nav.push(Screen.Notifications) }
        if (user.isFreelancer) AccountRow("Invitations") { nav.push(Screen.Invitations) }
        AccountRow(if (checkingUpdate) "Checking for updates…" else "Check for updates") {
            if (!checkingUpdate) {
                checkingUpdate = true
                scope.launch {
                    when (val r = graph.appUpdate.latest()) {
                        is ApiResult.Ok ->
                            if (AppUpdater.isUpdateAvailable(context, r.data)) manualUpdate = r.data
                            else toast("You're on the latest version")
                        is ApiResult.Err -> toast("Couldn't check for updates")
                    }
                    checkingUpdate = false
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { scope.launch { graph.session.logout() } }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        manualUpdate?.let { info ->
            UpdateDialog(info, manualDownloading, onUpdate = {
                manualDownloading = true
                scope.launch {
                    val error = AppUpdater.downloadAndInstall(context, info.apkUrl)
                    manualDownloading = false
                    if (error != null) toast(error)
                }
            }, onDismiss = { manualUpdate = null })
        }
    }
}

@Composable
private fun AccountRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text("›") }
    }
}

@Composable
private fun MoneyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EditProfileScreen(user: User, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var reload by remember { mutableStateOf(0) }
    TopBarScaffold(title = "Edit profile", onBack = { nav.pop() }) { m ->
        ApiContent(loaderKey = reload, loader = { graph.profile.profile() }, modifier = m) { resp ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                AvatarUploader(resp.media.avatar, resp.rating) { reload++ }
                Spacer(Modifier.height(8.dp))
                if (user.isClient) ClientProfileForm(resp.profile) { body ->
                    scope.launch { val r = graph.profile.saveClient(body); if (r is ApiResult.Ok) { toast("Saved"); graph.session.refresh(); nav.pop() } else if (r is ApiResult.Err) toast(r.message) }
                } else FreelancerProfileForm(resp.profile) { body ->
                    scope.launch { val r = graph.profile.saveFreelancer(body); if (r is ApiResult.Ok) { toast("Saved"); graph.session.refresh(); nav.pop() } else if (r is ApiResult.Err) toast(r.message) }
                }
                Spacer(Modifier.height(16.dp))
                ScreenshotGallery(resp.media.projects) { reload++ }
            }
        }
    }
}

/** Avatar preview + upload, plus the star rating summary. */
@Composable
private fun AvatarUploader(avatar: ProfileMedia?, rating: RatingSummary, onChanged: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime !in setOf("image/jpeg", "image/png", "image/webp")) { toast("Use a JPEG, PNG, or WebP image"); return@launch }
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            if (bytes == null || bytes.isEmpty()) { toast("Could not read the image"); return@launch }
            if (bytes.size > 5 * 1024 * 1024) { toast("Image too large (max 5 MB)"); return@launch }
            when (val r = graph.profile.uploadMedia("avatar", "", bytes, mime)) {
                is ApiResult.Ok -> { toast("Photo updated"); onChanged() }
                is ApiResult.Err -> toast(r.message)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (avatar != null) "Change photo" else "Upload photo") }
            Text("★ %.1f (%d ratings)".format(rating.average, rating.count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Project/business screenshots: upload + list with delete. */
@Composable
private fun ScreenshotGallery(projects: List<ProfileMedia>, onChanged: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime !in setOf("image/jpeg", "image/png", "image/webp")) { toast("Use a JPEG, PNG, or WebP image"); return@launch }
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            if (bytes == null || bytes.isEmpty()) { toast("Could not read the image"); return@launch }
            if (bytes.size > 5 * 1024 * 1024) { toast("Image too large (max 5 MB)"); return@launch }
            when (val r = graph.profile.uploadMedia("project", "", bytes, mime)) {
                is ApiResult.Ok -> { toast("Screenshot added"); onChanged() }
                is ApiResult.Err -> toast(r.message)
            }
        }
    }
    Text("Project screenshots (${projects.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    projects.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(p.caption.ifBlank { "Screenshot" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { scope.launch { if (graph.profile.deleteMedia(p.id) is ApiResult.Ok) { toast("Removed"); onChanged() } } }) { Text("Remove") }
        }
    }
    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Add screenshot") }
}

@Composable
private fun ClientProfileForm(profile: Profile?, onSave: (ClientProfileRequest) -> Unit) {
    var company by remember { mutableStateOf(profile?.companyName ?: "") }
    var description by remember { mutableStateOf(profile?.description ?: "") }
    var website by remember { mutableStateOf(profile?.website ?: "") }
    var title by remember { mutableStateOf(profile?.contactTitle ?: "") }
    var location by remember { mutableStateOf(profile?.location ?: "") }
    var orgType by remember { mutableStateOf(profile?.organisationType ?: "") }
    var mobile by remember { mutableStateOf(profile?.mobileNumber ?: "") }
    var hourly by remember { mutableStateOf(profile?.hourlyRatePaise?.let { (it / 100).toString() } ?: "") }
    val showcase = remember { mutableStateListOf<ShowcaseEntry>().also { it.addAll(profile?.showcase ?: emptyList()) } }
    var linkedin by remember { mutableStateOf(profile?.social?.get("linkedin") ?: "") }
    var twitter by remember { mutableStateOf(profile?.social?.get("twitter") ?: "") }
    var instagram by remember { mutableStateOf(profile?.social?.get("instagram") ?: "") }
    var siteUrl by remember { mutableStateOf(profile?.social?.get("website") ?: "") }
    Column(Modifier.fillMaxWidth()) {
        Field("Company name", company) { company = it }
        Field("Description (20+ chars)", description, lines = 4) { description = it }
        Field("Website", website) { website = it }
        Field("Your title", title) { title = it }
        Field("Organisation type", orgType) { orgType = it }
        Field("Location", location) { location = it }
        Field("Mobile number", mobile) { mobile = it }
        Field("Hourly hiring budget (INR)", hourly) { hourly = it }
        Text("Social links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Field("LinkedIn URL", linkedin) { linkedin = it }
        Field("Twitter/X URL", twitter) { twitter = it }
        Field("Instagram URL", instagram) { instagram = it }
        Field("Website URL", siteUrl) { siteUrl = it }
        RepeatEditor(
            heading = "Business highlights",
            items = showcase,
            label = { "${it.title}${if (it.description.isNotBlank()) " — ${it.description}" else ""}" },
            fields = listOf("Title", "Description"),
            onAdd = { vals -> if (vals[0].isNotBlank()) showcase.add(ShowcaseEntry(vals[0], vals[1])) },
            onRemove = { showcase.removeAt(it) }
        )
        Spacer(Modifier.height(12.dp))
        Button(enabled = company.length >= 2 && description.length >= 20, modifier = Modifier.fillMaxWidth(),
            onClick = { onSave(ClientProfileRequest(company, description, website, title, location, orgType, mobile,
                hourlyRate = hourly.ifBlank { null }, showcase = showcase.toList(),
                social = mapOf("linkedin" to linkedin, "twitter" to twitter, "instagram" to instagram, "website" to siteUrl).filterValues { it.isNotBlank() })) }) { Text("Save host profile") }
    }
}

@Composable
private fun FreelancerProfileForm(profile: Profile?, onSave: (FreelancerProfileRequest) -> Unit) {
    var headline by remember { mutableStateOf(profile?.headline ?: "") }
    var bio by remember { mutableStateOf(profile?.bio ?: "") }
    var skills by remember { mutableStateOf(profile?.skills?.joinToString(", ") ?: "") }
    var rate by remember { mutableStateOf(profile?.hourlyRatePaise?.let { (it / 100).toString() } ?: "") }
    var location by remember { mutableStateOf(profile?.location ?: "") }
    var availability by remember { mutableStateOf(profile?.availability ?: "") }
    var handle by remember { mutableStateOf(profile?.handle ?: "") }
    var isPublic by remember { mutableStateOf(profile?.isPublic ?: false) }
    val experience = remember { mutableStateListOf<ExperienceEntry>().also { it.addAll(profile?.experience ?: emptyList()) } }
    var linkedin by remember { mutableStateOf(profile?.social?.get("linkedin") ?: "") }
    var github by remember { mutableStateOf(profile?.social?.get("github") ?: "") }
    var twitter by remember { mutableStateOf(profile?.social?.get("twitter") ?: "") }
    var siteUrl by remember { mutableStateOf(profile?.social?.get("website") ?: "") }
    Column(Modifier.fillMaxWidth()) {
        Field("Headline", headline) { headline = it }
        Field("Public handle", handle) { handle = it }
        Field("Skills (comma separated)", skills) { skills = it }
        Field("Hourly rate (INR)", rate) { rate = it }
        Field("Bio (20+ chars)", bio, lines = 4) { bio = it }
        Field("Location", location) { location = it }
        Field("Availability", availability) { availability = it }
        Text("Social links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Field("LinkedIn URL", linkedin) { linkedin = it }
        Field("GitHub URL", github) { github = it }
        Field("Twitter/X URL", twitter) { twitter = it }
        Field("Website URL", siteUrl) { siteUrl = it }
        RepeatEditor(
            heading = "Work experience",
            items = experience,
            label = { "${it.title}${if (it.company.isNotBlank()) " @ ${it.company}" else ""} ${it.period}" },
            fields = listOf("Role / title", "Company", "Period", "What you did"),
            onAdd = { vals -> if (vals[0].isNotBlank()) experience.add(ExperienceEntry(vals[0], vals[1], vals[2], vals[3])) },
            onRemove = { experience.removeAt(it) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(isPublic, { isPublic = it }); Text("Show my verified profile publicly")
        }
        Spacer(Modifier.height(12.dp))
        Button(enabled = headline.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(FreelancerProfileRequest(
                headline = headline, bio = bio,
                skills = skills.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                hourlyRate = rate.ifBlank { null }, location = location, availability = availability,
                handle = handle.ifBlank { null }, isPublic = isPublic, experience = experience.toList(),
                social = mapOf("linkedin" to linkedin, "github" to github, "twitter" to twitter, "website" to siteUrl).filterValues { it.isNotBlank() }
            ))
        }) { Text("Save worker profile") }
    }
}

/** A small repeatable-list editor: shows current items with remove, plus N input
 * fields and an Add button. */
@Composable
private fun <T> RepeatEditor(
    heading: String,
    items: List<T>,
    label: (T) -> String,
    fields: List<String>,
    onAdd: (List<String>) -> Unit,
    onRemove: (Int) -> Unit
) {
    val values = remember { mutableStateListOf<String>().also { repeat(fields.size) { _ -> it.add("") } } }
    Spacer(Modifier.height(8.dp))
    Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    items.forEachIndexed { i, item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label(item), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onRemove(i) }) { Text("Remove") }
        }
    }
    fields.forEachIndexed { i, f -> Field(f, values[i]) { values[i] = it } }
    OutlinedButton(onClick = {
        onAdd(values.toList()); for (i in values.indices) values[i] = ""
    }, modifier = Modifier.fillMaxWidth()) { Text("Add to $heading") }
}

@Composable
fun VerificationScreen(user: User, nav: Nav, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var legalName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("India") }
    var pan by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var orgName by remember { mutableStateOf("") }
    var regRef by remember { mutableStateOf("") }
    var c1 by remember { mutableStateOf(false) }
    var c2 by remember { mutableStateOf(false) }
    var c3 by remember { mutableStateOf(false) }
    var payoutReady by remember { mutableStateOf(false) }
    var panDocUp by remember { mutableStateOf(false) }
    var aadhaarDocUp by remember { mutableStateOf(false) }
    var gstDocUp by remember { mutableStateOf(false) }

    val panOk = KycValidators.isValidPan(pan)
    val aadhaarOk = KycValidators.isValidAadhaar(aadhaar)
    val gstinOk = gstin.isBlank() || KycValidators.isValidGstin(gstin)

    TopBarScaffold(title = "KYC verification", onBack = { nav.pop() }) { m ->
        Column(m.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Submit your Indian KYC for review. IDs are validated, encrypted, and shown only to a reviewer — never published.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Field("Legal name", legalName) { legalName = it }
            Field("Country", country) { country = it }
            Field("PAN (required)", pan) { pan = it.uppercase().take(10) }
            if (pan.isNotEmpty() && !panOk) FieldError("Enter a valid PAN, e.g. ABCPK1234L")
            DocumentPicker("PAN card", "pan_card", required = true, uploaded = panDocUp) { panDocUp = it }

            if (user.isFreelancer) {
                Field("Aadhaar (required)", aadhaar) { aadhaar = it.take(14) }
                if (aadhaar.isNotEmpty() && !aadhaarOk) FieldError("Enter a valid 12-digit Aadhaar")
                DocumentPicker("Aadhaar front", "aadhaar_front", required = true, uploaded = aadhaarDocUp) { aadhaarDocUp = it }
            }
            if (user.isClient) {
                Field("GSTIN (optional)", gstin) { gstin = it.uppercase().take(15) }
                if (gstin.isNotEmpty() && !gstinOk) FieldError("GSTIN is invalid")
                if (gstin.isNotBlank()) DocumentPicker("GST certificate", "gst_certificate", required = true, uploaded = gstDocUp) { gstDocUp = it }
                Field("Organisation name", orgName) { orgName = it }
                Field("Registration / tax reference (optional)", regRef) { regRef = it }
            }

            val checks = if (user.isClient)
                listOf("authorityConfirmed" to "I'm authorised to post projects", "companyDocumentsReady" to "I can provide evidence on request", "billingConfirmed" to "My billing details are accurate")
            else
                listOf("identityConfirmed" to "My identity details are accurate", "portfolioConfirmed" to "My skills/portfolio are authentic", "termsAccepted" to "I accept delivery & dispute rules")
            val states = listOf(c1 to { v: Boolean -> c1 = v }, c2 to { v: Boolean -> c2 = v }, c3 to { v: Boolean -> c3 = v })
            checks.forEachIndexed { i, (_, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(states[i].first, states[i].second); Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (user.isFreelancer) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(payoutReady, { payoutReady = it }); Text("I can complete payout onboarding") }

            val docsOk = panDocUp &&
                (!user.isFreelancer || aadhaarDocUp) &&
                (!user.isClient || gstin.isBlank() || gstDocUp)
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = legalName.length >= 2 && country.length >= 2 && panOk &&
                    (!user.isFreelancer || aadhaarOk) && gstinOk && docsOk &&
                    c1 && c2 && c3 && (user.isClient || payoutReady),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val checkMap = checks.mapIndexed { i, (key, _) -> key to states[i].first }.toMap()
                    scope.launch {
                        val r = graph.profile.submitVerification(
                            VerificationSubmitRequest(
                                legalName = legalName,
                                country = country,
                                pan = pan,
                                aadhaar = if (user.isFreelancer) aadhaar else "",
                                gstin = if (user.isClient) gstin else "",
                                organisationName = orgName,
                                registrationReference = regRef,
                                payoutReady = payoutReady,
                                checks = checkMap
                            )
                        )
                        if (r is ApiResult.Ok) { toast("Submitted for review"); graph.session.refresh(); nav.pop() } else if (r is ApiResult.Err) toast(r.message)
                    }
                }
            ) { Text("Submit for review") }
        }
    }
}

@Composable
private fun FieldError(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 4.dp))
}

/** Picks an image, validates type/size, and uploads it as an encrypted KYC document. */
@Composable
private fun DocumentPicker(label: String, docType: String, required: Boolean, uploaded: Boolean, onResult: (Boolean) -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToaster()
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime !in setOf("image/jpeg", "image/png", "application/pdf")) {
                toast("Use a JPEG or PNG image"); busy = false; return@launch
            }
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            when {
                bytes == null || bytes.isEmpty() -> toast("Could not read the file")
                bytes.size > 6 * 1024 * 1024 -> toast("Image too large (max 6 MB)")
                else -> when (val r = graph.profile.uploadDocument(docType, bytes, mime)) {
                    is ApiResult.Ok -> { toast("$label uploaded"); onResult(true) }
                    is ApiResult.Err -> { toast(r.message); onResult(false) }
                }
            }
            busy = false
        }
    }
    OutlinedButton(
        onClick = { picker.launch("image/*") },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            when {
                uploaded -> "✓ $label uploaded — tap to replace"
                required -> "Upload $label *"
                else -> "Upload $label"
            }
        )
    }
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) },
        singleLine = lines == 1,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).let { if (lines > 1) it.height((lines * 28 + 40).dp) else it }
    )
}
