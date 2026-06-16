package com.twowork.core.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.twowork.core.data.AppUpdateRepository
import com.twowork.core.data.AssessmentRepository
import com.twowork.core.data.ContractRepository
import com.twowork.core.data.DiscoveryRepository
import com.twowork.core.data.EngagementRepository
import com.twowork.core.data.MessageRepository
import com.twowork.core.data.ProfileRepository
import com.twowork.core.data.ProjectRepository
import com.twowork.core.data.SessionManager
import com.twowork.core.net.AppCookieJar
import com.twowork.core.net.Network

/** Lightweight manual dependency container, created once per app process. */
class Graph(context: Context, debug: Boolean) {
    private val cookieJar = AppCookieJar(context)
    private val client = Network.client(cookieJar, debug)
    private val api = Network.api(client)

    val session = SessionManager(api, cookieJar)
    val discovery = DiscoveryRepository(api)
    val projects = ProjectRepository(api)
    val contracts = ContractRepository(api)
    val messages = MessageRepository(api)
    val engagement = EngagementRepository(api)
    val profile = ProfileRepository(api)
    val assessments = AssessmentRepository(api)
    val appUpdate = AppUpdateRepository(api)
}

val LocalGraph = staticCompositionLocalOf<Graph> { error("Graph not provided") }
