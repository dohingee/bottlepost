package com.bottlepost.app.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.data.local.ThreadEntity
import com.bottlepost.app.data.local.connected
import com.bottlepost.app.ui.common.EmptyBottle
import com.bottlepost.app.ui.common.PaperSheet
import com.bottlepost.app.ui.common.Sea
import com.bottlepost.app.ui.common.SeaBackdrop
import com.bottlepost.app.ui.common.SeaTopBar
import com.bottlepost.app.ui.common.WaxSeal
import com.bottlepost.app.ui.common.errorMessage
import com.bottlepost.app.ui.common.genderText
import com.bottlepost.app.ui.settings.SettingsScreen
import com.bottlepost.app.ui.theme.LetterTextStyle
import com.bottlepost.app.ui.thread.DeleteThreadDialogs
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class Tab(val title: Int, val empty: Int) {
    Inbox(R.string.tab_inbox, R.string.empty_inbox),
    Connections(R.string.tab_connections, R.string.empty_connections),
    Settings(R.string.tab_settings, 0),
}

/** Letters I sent that nobody answered yet stay out at sea; they show up once a reply arrives. */
private fun Tab.matches(t: ThreadEntity) = when (this) {
    Tab.Inbox -> !t.startedByMe && !t.connected
    Tab.Connections -> t.connected
    Tab.Settings -> false
}

@Composable
private fun TabIcon(tab: Tab) = when (tab) {
    Tab.Inbox -> Icon(painterResource(R.drawable.ic_notification), contentDescription = null)
    Tab.Connections -> Icon(Icons.Filled.Favorite, contentDescription = null)
    Tab.Settings -> Icon(Icons.Filled.Settings, contentDescription = null)
}

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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<ThreadEntity?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        container.notices.receiveAsFlow().collect { snackbar.showSnackbar(context.getString(it)) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SeaTopBar(title = {
                Text(
                    stringResource(if (tab == Tab.Settings) R.string.tab_settings else R.string.app_name),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                )
            })
        },
        bottomBar = {
            NavigationBar(containerColor = Sea.Night, contentColor = Sea.Paper) {
                Tab.entries.forEachIndexed { i, t ->
                    val unread = threads.filter { t.matches(it) }.sumOf { it.unread }
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = { BadgedBox(badge = { if (unread > 0) Badge(containerColor = Sea.Wax) { Text("$unread") } }) { TabIcon(t) } },
                        label = { Text(stringResource(t.title)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Sea.Paper,
                            selectedTextColor = Sea.Paper,
                            indicatorColor = Sea.Teal,
                            unselectedIconColor = Sea.Paper.copy(alpha = 0.6f),
                            unselectedTextColor = Sea.Paper.copy(alpha = 0.6f),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.Settings && profile?.status == "active") {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    containerColor = Sea.Deep,
                    contentColor = Sea.Paper,
                    shape = CircleShape,
                    icon = { Icon(Icons.Filled.Create, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_write_letter), fontWeight = FontWeight.SemiBold) },
                )
            }
        },
    ) { padding ->
        SeaBackdrop(Modifier.fillMaxSize()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (profile != null && profile?.status != "active") StatusBanner(profile!!.status, profile!!.suspendedUntil)
                if (tab == Tab.Settings) {
                    SettingsScreen(container, onAccountDeleted)
                } else {
                    val list = remember(threads, tab) { threads.filter { tab.matches(it) } }
                    if (list.isEmpty()) EmptyBottle(stringResource(tab.empty))
                    else LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list, key = { it.threadId }) {
                            ThreadCard(it, onClick = { onOpenThread(it.threadId) }, onLongClick = { deleting = it })
                        }
                    }
                }
            }
        }
    }

    deleting?.let { t ->
        DeleteThreadDialogs(t, onDismiss = { deleting = null }) {
            deleting = null
            scope.launch {
                runCatching { container.mail.delete(t) }
                    .onSuccess { snackbar.showSnackbar(context.getString(R.string.delete_done)) }
                    .onFailure { snackbar.showSnackbar(context.getString(errorMessage(it))) }
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

/** An envelope-like paper card; press and hold to delete. */
@Composable
internal fun ThreadCard(t: ThreadEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    PaperSheet(
        tinted = t.closed,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val who = t.partnerNickname.ifBlank { stringResource(R.string.stranger) }
            val gender = if (t.partnerGender.isNotBlank()) " · ${genderText(t.partnerGender)}" else ""
            Text(
                who + gender,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = if (t.unread > 0) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(t.lastAt)),
                style = MaterialTheme.typography.labelSmall,
                color = Sea.InkText.copy(alpha = 0.6f),
            )
            if (t.unread > 0) {
                Spacer(Modifier.width(8.dp))
                WaxSeal(t.unread)
            }
        }
        Text(t.lastPreview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = LetterTextStyle.copy(fontSize = 15.sp, lineHeight = 22.sp))
        if (t.closed) {
            Text(
                stringResource(if (t.partnerLeft) R.string.partner_left else R.string.thread_closed),
                style = MaterialTheme.typography.labelSmall,
                color = Sea.Wax,
            )
        }
    }
}
