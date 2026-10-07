package io.github.kylinlee.chatsim.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.kylinlee.chatsim.R

enum class HomeTab {
    Messages,
    Contacts,
    Manage,
    Rules,
}

@Composable
fun HomeNavigationBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    enabled: Boolean = true,
    unreadCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = if (enabled) modifier else modifier.clearAndSetSemantics {},
    ) {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                enabled = enabled,
                icon = { HomeTabIcon(tab, unreadCount) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

@Composable
fun HomeNavigationRail(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onSettingsClick: () -> Unit,
    unreadCount: Int = 0,
) {
    NavigationRail(
        header = {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(40.dp),
            )
        },
    ) {
        HomeTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = { HomeTabIcon(tab, unreadCount) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }

        Spacer(Modifier.weight(1f))

        NavigationRailItem(
            selected = false,
            onClick = onSettingsClick,
            icon = { Icon(painterResource(R.drawable.ic_symbol_settings), contentDescription = null) },
            label = { Text(stringResource(io.github.kylinlee.chatsim.R.string.settings)) },
        )
    }
}

@Composable
private fun HomeTabIcon(tab: HomeTab, unreadCount: Int) {
    if (tab == HomeTab.Messages && unreadCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                }
            },
        ) {
            Icon(painterResource(tab.iconRes), contentDescription = null)
        }
    } else {
        Icon(painterResource(tab.iconRes), contentDescription = null)
    }
}

@get:DrawableRes
private val HomeTab.iconRes: Int
    get() = when (this) {
        HomeTab.Messages -> R.drawable.ic_symbol_chat
        HomeTab.Contacts -> R.drawable.ic_symbol_contacts
        HomeTab.Manage -> R.drawable.ic_symbol_dashboard
        HomeTab.Rules -> R.drawable.ic_symbol_rule
    }

@get:StringRes
private val HomeTab.labelRes: Int
    get() = when (this) {
        HomeTab.Messages -> io.github.kylinlee.chatsim.R.string.messages_tab
        HomeTab.Contacts -> io.github.kylinlee.chatsim.R.string.contacts_tab
        HomeTab.Manage -> io.github.kylinlee.chatsim.R.string.tab_manage
        HomeTab.Rules -> io.github.kylinlee.chatsim.R.string.tab_rules
    }
