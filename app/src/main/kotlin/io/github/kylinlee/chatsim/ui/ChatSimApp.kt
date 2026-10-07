package io.github.kylinlee.chatsim.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import io.github.kylinlee.chatsim.ui.common.RequestAppPermissions
import io.github.kylinlee.chatsim.ui.contacts.ContactDetailsScreen
import io.github.kylinlee.chatsim.ui.contacts.ContactsScreen
import io.github.kylinlee.chatsim.ui.conversations.ConversationsScreen
import io.github.kylinlee.chatsim.ui.dialpad.DialpadScreen
import io.github.kylinlee.chatsim.navigation.ContactDetailsRoute
import io.github.kylinlee.chatsim.navigation.ContactsRoute
import io.github.kylinlee.chatsim.navigation.ConversationsRoute
import io.github.kylinlee.chatsim.navigation.DialpadRoute
import io.github.kylinlee.chatsim.navigation.NewConversationRoute
import io.github.kylinlee.chatsim.navigation.RecycleBinRoute
import io.github.kylinlee.chatsim.navigation.SettingsRoute
import io.github.kylinlee.chatsim.navigation.RolesRoute
import io.github.kylinlee.chatsim.navigation.SimImportRoute
import io.github.kylinlee.chatsim.navigation.ThreadRoute
import io.github.kylinlee.chatsim.navigation.UserRuleEditRoute
import io.github.kylinlee.chatsim.navigation.UserRuleRecordsRoute
import io.github.kylinlee.chatsim.navigation.UserRulesRoute
import io.github.kylinlee.chatsim.ui.components.HomeNavigationBar
import io.github.kylinlee.chatsim.ui.components.HomeNavigationRail
import io.github.kylinlee.chatsim.ui.components.HomeTab
import io.github.kylinlee.chatsim.ui.newconversation.NewConversationScreen
import io.github.kylinlee.chatsim.ui.settings.RecycleBinScreen
import io.github.kylinlee.chatsim.ui.settings.SettingsScreen
import io.github.kylinlee.chatsim.ui.settings.RolesScreen
import io.github.kylinlee.chatsim.ui.settings.SimImportScreen
import io.github.kylinlee.chatsim.ui.settings.UserRuleEditScreen
import io.github.kylinlee.chatsim.ui.settings.UserRuleRecordsScreen
import io.github.kylinlee.chatsim.ui.settings.UserRulesScreen
import io.github.kylinlee.chatsim.ui.thread.ThreadScreen
import io.github.kylinlee.chatsim.viewmodel.ContactsViewModel
import io.github.kylinlee.chatsim.viewmodel.ConversationsViewModel
import io.github.kylinlee.chatsim.R

private val HomeTabBarHeight = 80.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ChatSimApp(
    pendingRoute: NavKey? = null,
    onPendingRouteHandled: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(ConversationsRoute)

    // Keep the list view models scoped to the activity so that switching tabs does not reload their data
    val conversationsViewModel: ConversationsViewModel = hiltViewModel()
    val contactsViewModel: ContactsViewModel = hiltViewModel()
    val unreadCount by conversationsViewModel.unreadCount.collectAsStateWithLifecycle()

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val isWideLayout = windowAdaptiveInfo.windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        directive = remember(windowAdaptiveInfo) {
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(windowAdaptiveInfo)
                .copy(horizontalPartitionSpacerSize = 0.dp)
        }
    )

    RequestAppPermissions()

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            if (backStack.firstOrNull() !is ConversationsRoute) {
                backStack.clear()
                backStack.add(ConversationsRoute)
            }
            if (backStack.lastOrNull() is ThreadRoute || backStack.lastOrNull() is DialpadRoute) {
                backStack[backStack.lastIndex] = pendingRoute
            } else {
                backStack.add(pendingRoute)
            }
            onPendingRouteHandled()
        }
    }

    val selectedTab = when (backStack.firstOrNull()) {
        is ContactsRoute -> HomeTab.Contacts
        is UserRuleRecordsRoute -> HomeTab.Manage
        is UserRulesRoute -> HomeTab.Rules
        else -> HomeTab.Messages
    }
    val isHomeRoute = backStack.lastOrNull() is ConversationsRoute ||
        backStack.lastOrNull() is ContactsRoute ||
        backStack.lastOrNull() is UserRulesRoute ||
        backStack.lastOrNull() is UserRuleRecordsRoute

    val switchTab: (HomeTab) -> Unit = { tab ->
        val route: NavKey = when (tab) {
            HomeTab.Contacts -> ContactsRoute
            HomeTab.Manage -> UserRuleRecordsRoute
            HomeTab.Rules -> UserRulesRoute
            HomeTab.Messages -> ConversationsRoute
        }
        backStack.clear()
        backStack.add(route)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (isWideLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                HomeNavigationRail(
                    selectedTab = selectedTab,
                    onTabSelected = switchTab,
                    onSettingsClick = { backStack.add(SettingsRoute) },
                    unreadCount = unreadCount,
                )
                Box(modifier = Modifier.weight(1f)) {
                    HomeNavDisplay(
                        backStack = backStack,
                        listDetailStrategy = listDetailStrategy,
                        showSettingsInTopBar = false,
                        reserveTabBar = false,
                        conversationsViewModel = conversationsViewModel,
                        contactsViewModel = contactsViewModel,
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // the tab bar sits below the pages: home pages leave room for it,
                // any other full screen page simply covers it
                HomeNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = switchTab,
                    enabled = isHomeRoute,
                    unreadCount = unreadCount,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                HomeNavDisplay(
                    backStack = backStack,
                    listDetailStrategy = listDetailStrategy,
                    showSettingsInTopBar = true,
                    reserveTabBar = true,
                    conversationsViewModel = conversationsViewModel,
                    contactsViewModel = contactsViewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun HomeNavDisplay(
    backStack: MutableList<NavKey>,
    listDetailStrategy: ListDetailSceneStrategy<NavKey>,
    showSettingsInTopBar: Boolean,
    reserveTabBar: Boolean,
    conversationsViewModel: ConversationsViewModel,
    contactsViewModel: ContactsViewModel,
) {
    val onSettingsClick: (() -> Unit)? = if (showSettingsInTopBar) {
        { backStack.add(SettingsRoute) }
    } else {
        null
    }

    val homeSceneModifier = if (reserveTabBar) {
        Modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
            .padding(bottom = HomeTabBarHeight)
    } else {
        Modifier
    }

    // Track the back stack ourselves so that back navigations always animate the same way,
    // no matter which of the transition specs the NavDisplay picks internally.
    val currentRoutes = backStack.toList()
    var previousRoutes by remember { mutableStateOf(currentRoutes) }
    val isBackNavigation = remember(currentRoutes) { isBackStackPop(previousRoutes, currentRoutes) }
    SideEffect { previousRoutes = currentRoutes }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        sceneStrategies = listOf(listDetailStrategy),
        sizeTransform = null,
        transitionSpec = {
            val fromTab = homeTabIndex(initialState)
            val toTab = homeTabIndex(targetState)
            when {
                // the newly selected tab sits to the left of the current one
                fromTab != null && toTab != null && toTab < fromTab -> backTransition()
                isBackNavigation -> backTransition()
                else -> forwardTransition()
            }
        },
        popTransitionSpec = {
            backTransition()
        },
        predictivePopTransitionSpec = {
            backTransition()
        },
        entryProvider = entryProvider<NavKey> {
            entry<ConversationsRoute>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { ConversationsDetailPlaceholder() }
                )
            ) {
                Box(modifier = homeSceneModifier) {
                    ConversationsScreen(
                        viewModel = conversationsViewModel,
                        selectedThread = backStack.lastOrNull() as? ThreadRoute,
                        onConversationClick = { thread ->
                            val route = ThreadRoute(
                                roleId = thread.roleId,
                                peerNumber = thread.peerNumber,
                                title = thread.title,
                            )
                            if (backStack.lastOrNull() is ThreadRoute) {
                                backStack[backStack.lastIndex] = route
                            } else {
                                backStack.add(route)
                            }
                        },
                        onConversationDeleted = { thread ->
                            val index = backStack.indexOfLast {
                                it is ThreadRoute && it.roleId == thread.roleId && it.peerNumber == thread.peerNumber
                            }
                            if (index >= 0) {
                                backStack.removeAt(index)
                            }
                        },
                        onNewConversationClick = { backStack.add(NewConversationRoute) },
                        onDialpadClick = { backStack.add(DialpadRoute()) },
                        onSettingsClick = onSettingsClick,
                    )
                }
            }
            entry<ContactsRoute>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { ContactsDetailPlaceholder() }
                )
            ) {
                Box(modifier = homeSceneModifier) {
                    ContactsScreen(
                        viewModel = contactsViewModel,
                        onContactClick = { contactId ->
                            val route = ContactDetailsRoute(contactId)
                            if (backStack.lastOrNull() is ContactDetailsRoute) {
                                backStack[backStack.lastIndex] = route
                            } else {
                                backStack.add(route)
                            }
                        },
                        onCreateContact = { backStack.add(ContactDetailsRoute(contactId = 0)) },
                        onDialNumber = { number -> backStack.add(DialpadRoute(number)) },
                        onSettingsClick = onSettingsClick,
                    )
                }
            }
            entry<ContactDetailsRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                ContactDetailsScreen(
                    route = route,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ThreadRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                ThreadScreen(
                    route = route,
                    onBack = { backStack.removeLastOrNull() },
                    onCreateContact = { number ->
                        backStack.add(ContactDetailsRoute(contactId = 0, phoneNumber = number))
                    },
                )
            }
            entry<NewConversationRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                NewConversationScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onConversationStarted = { route ->
                        if (backStack.lastOrNull() is NewConversationRoute) {
                            backStack[backStack.lastIndex] = route
                        } else {
                            backStack.add(route)
                        }
                    },
                )
            }
            entry<DialpadRoute>(
                metadata = ListDetailSceneStrategy.detailPane() + ListDetailSceneStrategy.paneAnimation(
                    enterTransition = fadeIn(animationSpec = tween(300)),
                    exitTransition = fadeOut(animationSpec = tween(300)),
                )
            ) { route ->
                DialpadScreen(
                    onBack = { backStack.removeLastOrNull() },
                    initialNumber = route.phoneNumber,
                    onOpenConversation = { route ->
                        if (backStack.lastOrNull() is DialpadRoute) {
                            backStack[backStack.lastIndex] = route
                        } else {
                            backStack.add(route)
                        }
                    },
                    onCreateContact = { number ->
                        backStack.add(ContactDetailsRoute(contactId = 0, phoneNumber = number))
                    },
                )
            }
            entry<SettingsRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                val context = LocalContext.current
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onRolesClick = { backStack.add(RolesRoute) },
                    onSimImportClick = { backStack.add(SimImportRoute) },
                    onRecycleBinClick = { backStack.add(RecycleBinRoute) },
                    onOverlayPermissionClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                )
            }
            entry<RolesRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                RolesScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<SimImportRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                SimImportScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<UserRulesRoute>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        TabDetailPlaceholder(
                            iconRes = io.github.kylinlee.chatsim.R.drawable.ic_symbol_rule,
                            textRes = io.github.kylinlee.chatsim.R.string.tab_rules,
                        )
                    }
                )
            ) {
                Box(modifier = homeSceneModifier) {
                    UserRulesScreen(
                        onBack = { backStack.removeLastOrNull() },
                        showBack = backStack.size > 1,
                        onAddRule = { backStack.add(UserRuleEditRoute()) },
                        onEditRule = { ruleId -> backStack.add(UserRuleEditRoute(ruleId)) },
                        onSettingsClick = onSettingsClick,
                    )
                }
            }
            entry<UserRuleEditRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                UserRuleEditScreen(
                    route = route,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<UserRuleRecordsRoute>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        TabDetailPlaceholder(
                            iconRes = io.github.kylinlee.chatsim.R.drawable.ic_symbol_dashboard,
                            textRes = io.github.kylinlee.chatsim.R.string.tab_manage,
                        )
                    }
                )
            ) {
                Box(modifier = homeSceneModifier) {
                    UserRuleRecordsScreen(
                        onBack = { backStack.removeLastOrNull() },
                        showBack = backStack.size > 1,
                        onSettingsClick = onSettingsClick,
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onRecycleBinClick = { backStack.add(RecycleBinRoute) },
                    )
                }
            }
            entry<RecycleBinRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                RecycleBinScreen(
                    onBack = { backStack.removeLastOrNull() },
                    showBack = backStack.size > 1,
                )
            }
        },
    )
}

@Composable
private fun TabDetailPlaceholder(
    @androidx.annotation.DrawableRes iconRes: Int,
    @androidx.annotation.StringRes textRes: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationsDetailPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_symbol_chat),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.start_conversation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactsDetailPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_symbol_person),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.select_contact),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Index of the bottom navigation tab the scene's root route belongs to, or null for non-tab routes. */
private fun homeTabIndex(scene: Scene<NavKey>): Int? {
    val contentKey = scene.entries.firstOrNull()?.contentKey
    // the default content key is Pair(key.toString(), key::class.toString())
    val keyString = when (contentKey) {
        is Pair<*, *> -> contentKey.first?.toString()
        else -> contentKey?.toString()
    }
    return when (keyString) {
        ConversationsRoute.toString() -> 0
        ContactsRoute.toString() -> 1
        UserRuleRecordsRoute.toString() -> 2
        UserRulesRoute.toString() -> 3
        else -> null
    }
}

/** The current page stays on top and slides out to the right while the returning page follows. */
private fun backTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        initialOffsetX = { -it },
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        targetOffsetX = { it },
    ),
    targetContentZIndex = -1f,
    sizeTransform = null,
)

/** The newly opened page slides in from the right above the current one. */
private fun forwardTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        initialOffsetX = { it },
    ),
    initialContentExit = slideOutHorizontally(
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        targetOffsetX = { -it },
    ),
    targetContentZIndex = 1f,
    sizeTransform = null,
)

/** Mirrors the pop detection of the NavDisplay on the routes owned by the app. */
private fun isBackStackPop(oldRoutes: List<NavKey>, newRoutes: List<NavKey>): Boolean {
    if (oldRoutes.isEmpty() || newRoutes.isEmpty()) return false
    if (oldRoutes.first() != newRoutes.first()) return false
    if (newRoutes.size >= oldRoutes.size) return false
    return newRoutes.indices.all { newRoutes[it] == oldRoutes[it] }
}
