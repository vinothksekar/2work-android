package com.twowork.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.twowork.core.di.LocalGraph
import com.twowork.core.model.Conversation
import com.twowork.core.model.Project
import com.twowork.core.model.User
import com.twowork.core.ui.LoadingBox

/** App-wide screens (a lightweight typed back stack — detail screens carry their data). */
sealed interface Screen {
    data object Discover : Screen
    data object Work : Screen
    data object Contracts : Screen
    data object Inbox : Screen
    data object Account : Screen
    data class ProjectDetail(val project: Project) : Screen
    data class Proposals(val project: Project) : Screen
    data class Thread(val conversation: Conversation) : Screen
    data object Notifications : Screen
    data object Invitations : Screen
    data object PostProject : Screen
    data object EditProfile : Screen
    data object Verification : Screen
    data object Assessments : Screen
    data class Exam(val attemptId: String, val skill: String, val level: Int) : Screen
}

class Nav(start: Screen) {
    val stack = mutableStateListOf(start)
    val current get() = stack.last()
    fun push(screen: Screen) { stack.add(screen) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun selectTab(tab: Screen) {
        stack.clear(); stack.add(tab)
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    val graph = LocalGraph.current
    val initialized by graph.session.initialized.collectAsState()
    val me by graph.session.me.collectAsState()

    LaunchedEffect(Unit) { graph.session.bootstrap() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !initialized -> LoadingBox()
            me == null -> AuthFlow()
            else -> Home(me!!)
        }
    }
    AppUpdateGate()
}

@Composable
private fun Home(user: User) {
    val nav = remember { Nav(Screen.Discover) }
    val current = nav.current

    val workLabel = if (user.isClient) "Projects" else "Find work"
    val tabs = listOf(
        Tab(Screen.Discover, "Discover", Icons.Filled.Explore),
        Tab(Screen.Work, workLabel, Icons.Filled.Work),
        Tab(Screen.Contracts, "Contracts", Icons.Filled.ReceiptLong),
        Tab(Screen.Inbox, "Inbox", Icons.AutoMirrored.Filled.Chat),
        Tab(Screen.Account, "Account", Icons.Filled.AccountCircle)
    )
    val isRoot = tabs.any { it.screen == current }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    Scaffold(
        bottomBar = {
            if (isRoot) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.screen,
                            onClick = { nav.selectTab(tab.screen) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (val screen = current) {
            Screen.Discover -> DiscoverScreen(user, nav, mod)
            Screen.Work -> if (user.isClient) ClientProjectsScreen(nav, mod) else FindWorkScreen(nav, mod)
            Screen.Contracts -> ContractsScreen(user, mod)
            Screen.Inbox -> ConversationsScreen(nav, mod)
            Screen.Account -> AccountScreen(user, nav, mod)
            is Screen.ProjectDetail -> ProjectDetailScreen(user, screen.project, nav, mod)
            is Screen.Proposals -> ProposalsScreen(screen.project, nav, mod)
            is Screen.Thread -> ThreadScreen(screen.conversation, nav, mod)
            Screen.Notifications -> NotificationsScreen(nav, mod)
            Screen.Invitations -> InvitationsScreen(nav, mod)
            Screen.PostProject -> PostProjectScreen(nav, mod)
            Screen.EditProfile -> EditProfileScreen(user, nav, mod)
            Screen.Verification -> VerificationScreen(user, nav, mod)
            Screen.Assessments -> AssessmentsScreen(nav, mod)
            is Screen.Exam -> ExamScreen(screen.attemptId, screen.skill, screen.level, nav, mod)
        }
    }
}
