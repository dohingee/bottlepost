package com.bottlepost.app.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.data.local.ThreadEntity
import com.bottlepost.app.data.local.connected
import com.bottlepost.app.ui.common.genderText
import com.bottlepost.app.ui.settings.SettingsScreen
import java.text.DateFormat
import java.util.Date

private enum class Tab(val title: Int, val icon: ImageVector, val empty: Int) {
    Inbox(R.string.tab_inbox, Icons.Filled.Email, R.string.empty_inbox),
    Sent(R.string.tab_sent, Icons.AutoMirrored.Filled.Send, R.string.empty_sent),
    Connections(R.string.tab_connections, Icons.Filled.Favorite, R.string.empty_connections),
    Settings(R.string.tab_settings, Icons.Filled.Settings, 0),
}

private fun Tab.matches(t: ThreadEntity) = when (this) {
    Tab.Inbox -> !t.startedByMe && !t.connected
    Tab.Sent -> t.startedByMe && !t.connected
    Tab.Connections -> t.connected
    Tab.Settings -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    container: AppContainer,
    onOpenThread: (String) -> Unit,
    onCompose: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    val threads by container.mail.observeThreads().collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by container.profiles.profile.collectAsStateWithLifecycle(initialValue = null)

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(if (tab == Tab.Settings) R.string.tab_settings else R.string.app_name)) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    val unread = threads.filter { t.matches(it) }.sumOf { it.unread }
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = {
                            BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) { Icon(t.icon, contentDescription = null) }
                        },
                        label = { Text(stringResource(t.title)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.Settings && profile?.status == "active") {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = { Icon(Icons.Filled.Create, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_write_letter)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (profile != null && profile?.status != "active") StatusBanner(profile!!.status, profile!!.suspendedUntil)
            if (tab == Tab.Settings) {
                SettingsScreen(container, onAccountDeleted)
            } else {
                val list = remember(threads, tab) { threads.filter { tab.matches(it) } }
                if (list.isEmpty()) EmptyState(tab.empty)
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.threadId }) { ThreadCard(it) { onOpenThread(it.threadId) } }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(status: String, until: Long?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        val text = if (status == "banned") stringResource(R.string.status_banned)
        else if (until != null) stringResource(R.string.status_suspended_until, DateFormat.getDateInstance().format(Date(until)))
        else stringResource(R.string.status_suspended_review)
        Text(text, modifier = Modifier.padding(16.dp))
        Text(stringResource(R.string.status_appeal_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
    }
}

@Composable
private fun EmptyState(text: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(text), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ThreadCard(t: ThreadEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val who = t.partnerNickname.ifBlank { stringResource(R.string.stranger) }
                val gender = if (t.partnerGender.isNotBlank()) " · ${genderText(t.partnerGender)}" else ""
                Text(
                    who + gender,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (t.unread > 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(t.lastAt)), style = MaterialTheme.typography.labelSmall)
            }
            Text(t.lastPreview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (t.closed) Text(stringResource(R.string.thread_closed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
