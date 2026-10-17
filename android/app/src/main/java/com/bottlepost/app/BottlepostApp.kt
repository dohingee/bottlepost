package com.bottlepost.app

import android.app.Application
import android.content.Context
import com.bottlepost.app.ads.AdGate
import com.bottlepost.app.ads.NoOpAdGate
import com.bottlepost.app.auth.AuthManager
import com.bottlepost.app.backup.BackupWorker
import com.bottlepost.app.backup.DriveBackupManager
import com.bottlepost.app.data.MailRepository
import com.bottlepost.app.data.ProfileRepository
import com.bottlepost.app.data.local.AppDatabase
import com.bottlepost.app.data.remote.BottlepostApi
import com.bottlepost.app.notify.LetterMessagingService
import com.bottlepost.app.translate.LetterTranslator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BottlepostApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        container = AppContainer(this)
        LetterMessagingService.createChannel(this)
    }
}

/** Manual dependency container; small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val api = BottlepostApi()
    val auth = AuthManager()
    private val db = AppDatabase.create(appContext)
    val profiles = ProfileRepository(appContext, api)
    val mail = MailRepository(db.mail(), api, FirebaseFirestore.getInstance(), appScope)
    val backup = DriveBackupManager(appContext, db.mail(), api)
    val translator = LetterTranslator()
    val adGate: AdGate = NoOpAdGate

    /** One-off messages (string resource ids) shown as a snackbar on the main screen; buffered until it is back on screen. */
    val notices = Channel<Int>(Channel.BUFFERED)

    suspend fun registerPushToken(token: String? = null) {
        if (auth.uid == null || profiles.current() == null) return
        api.registerPushToken(token ?: FirebaseMessaging.getInstance().token.await())
    }

    /** Everything the signed-in, onboarded app needs running. */
    suspend fun onReady() {
        auth.uid?.let { mail.startSync(it) }
        runCatching { registerPushToken() }
        if (auth.isGoogleLinked) BackupWorker.schedule(appContext)
    }

    suspend fun wipeLocal() {
        mail.stopSync()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        profiles.clear()
    }
}
