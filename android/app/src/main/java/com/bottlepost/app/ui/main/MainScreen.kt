package com.bottlepost.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bottlepost.app.AppContainer
import com.bottlepost.app.R
import com.bottlepost.app.ads.AdBanner
import com.bottlepost.app.data.local.ThreadEntity
import com.bottlepost.app.data.local.awaitingReply
import com.bottlepost.app.data.local.connected
import com.bottlepost.app.ui.common.AirmailStripe
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
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Once per app session, not every time the user comes back from a conversation.
        if (askedNotifications) return@LaunchedEffect
        askedNotifications = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
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
            val banner by container.adGate.bannersAllowed.collectAsStateWithLifecycle()
            val unread = Tab.entries.map { t -> threads.filter { t.matches(it) }.sumOf { it.unread } }
            if (!banner) {
                MainNavBar(tabIndex, unread) { tabIndex = it }
            } else {
                // Banner under the tabs, separated by a rule so it never reads as a tab button.
                Column(Modifier.background(Sea.Night)) {
                    MainNavBar(tabIndex, unread, windowInsets = WindowInsets(0)) { tabIndex = it }
                    HorizontalDivider(color = Sea.Paper.copy(alpha = 0.15f))
                    AdBanner(Modifier.padding(top = 4.dp).navigationBarsPadding())
                }
            }
        },
        floatingActionButton = { if (tab != Tab.Settings && profile?.status == "active") WriteLetterFab(onCompose) },
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
internal fun MainNavBar(
    selected: Int,
    unread: List<Int>,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    onSelect: (Int) -> Unit,
) {
    NavigationBar(containerColor = Sea.Night, contentColor = Sea.Paper, windowInsets = windowInsets) {
        Tab.entries.forEachIndexed { i, t ->
            val count = unread.getOrElse(i) { 0 }
            NavigationBarItem(
                selected = i == selected,
                onClick = { onSelect(i) },
                icon = { BadgedBox(badge = { if (count > 0) Badge(containerColor = Sea.Wax) { Text("$count") } }) { TabIcon(t) } },
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
}

@Composable
internal fun WriteLetterFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = Sea.Deep,
        contentColor = Sea.Paper,
        shape = CircleShape,
        icon = { Icon(Icons.Filled.Create, contentDescription = null) },
        text = { Text(stringResource(R.string.action_write_letter), fontWeight = FontWeight.SemiBold) },
    )
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

/**
 * A conversation as an airmail envelope: the partner's seal, their latest line and whose turn it
 * is. Press and hold to delete.
 */
@Composable
internal fun ThreadCard(t: ThreadEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    PaperSheet(
        tinted = t.closed,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AirmailStripe(Modifier.fillMaxWidth().height(5.dp), faded = t.closed)
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            val who = t.partnerNickname.ifBlank { stringResource(R.string.stranger) }
            PartnerSeal(who, t.threadId, faded = t.closed)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        who,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                        fontWeight = if (t.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (t.partnerGender.isNotBlank()) {
                        Text(
                            genderText(t.partnerGender),
                            style = MaterialTheme.typography.labelSmall,
                            color = Sea.Teal,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .border(1.dp, Sea.Teal.copy(alpha = 0.5f), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(relativeTime(t.lastAt), style = MaterialTheme.typography.labelSmall, color = Sea.InkText.copy(alpha = 0.55f))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        if (t.lastOutgoing) stringResource(R.string.preview_me, t.lastPreview) else t.lastPreview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = LetterTextStyle.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = Sea.InkText.copy(alpha = if (t.unread > 0) 1f else 0.8f),
                        modifier = Modifier.weight(1f),
                    )
                    if (t.unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        WaxSeal(t.unread)
                    }
                }
                ThreadStatus(t)
            }
        }
    }
}

/** One line under the preview: left, ended, waiting, new letter or my turn. */
@Composable
private fun ThreadStatus(t: ThreadEntity) {
    val (text, color, icon) = when {
        t.partnerLeft -> Triple(R.string.partner_left, Sea.Wax, null)
        t.closed -> Triple(R.string.thread_closed, Sea.Wax, null)
        t.awaitingReply() -> Triple(R.string.status_waiting, Sea.Teal, R.drawable.ic_notification)
        t.unread > 0 -> Triple(R.string.status_new_letter, Sea.Wax, null)
        else -> Triple(R.string.status_your_turn, Sea.Deep, null)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        } else {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** A round seal with the partner's initial, its colour fixed per conversation. */
@Composable
private fun PartnerSeal(name: String, key: String, faded: Boolean) {
    val tones = listOf(Sea.Deep, Sea.Teal, Sea.Wax, Color(0xFF8A6A3B), Color(0xFF5B4E8C), Color(0xFF3F7D5C))
    val tone = if (faded) Sea.PaperEdge else tones[Math.floorMod(key.hashCode(), tones.size)]
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(tone.copy(alpha = 0.75f), tone)))
            .border(2.dp, Sea.Paper.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = if (faded) Sea.InkText.copy(alpha = 0.6f) else Sea.Paper,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}

/** "5 min ago", "Yesterday", or a date, in the app language. */
@Composable
private fun relativeTime(at: Long): String =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
