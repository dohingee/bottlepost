package com.bottlepost.app.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.bottlepost.app.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone that installed version 1 of the app must open its database after updating. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {
    @Test
    fun v1DatabaseUpgradesToCurrent() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val file = ctx.getDatabasePath("bottlepost.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `threads` (`threadId` TEXT NOT NULL, `startedByMe` INTEGER NOT NULL, `partnerNickname` TEXT NOT NULL, `partnerGender` TEXT NOT NULL, `partnerLang` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastAt` INTEGER NOT NULL, `lastPreview` TEXT NOT NULL, `hasIncoming` INTEGER NOT NULL, `hasOutgoing` INTEGER NOT NULL, `unread` INTEGER NOT NULL, `closed` INTEGER NOT NULL, PRIMARY KEY(`threadId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `letters` (`deliveryId` TEXT NOT NULL, `threadId` TEXT NOT NULL, `outgoing` INTEGER NOT NULL, `body` TEXT NOT NULL, `lang` TEXT NOT NULL, `sentAt` INTEGER NOT NULL, `sig` TEXT NOT NULL, PRIMARY KEY(`deliveryId`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_letters_threadId` ON `letters` (`threadId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO threads VALUES ('a',0,'n','F','ko',1,3,'p',1,1,0,0)")
            db.execSQL("INSERT INTO threads VALUES ('b',1,'m','M','ko',1,3,'q',1,1,0,0)")
            db.execSQL("INSERT INTO letters VALUES ('d1','a',0,'hi','ko',1,'s')")
            db.execSQL("INSERT INTO letters VALUES ('d2','a',1,'yo','ko',2,'s')")
            db.execSQL("INSERT INTO letters VALUES ('d3','b',1,'hey','ko',1,'s')")
            db.execSQL("INSERT INTO letters VALUES ('d4','b',0,'back','ko',2,'s')")
            db.version = 1
        }
        val room = AppDatabase.create(ctx)
        runBlocking {
            val a = room.mail().thread("a")!!
            val b = room.mail().thread("b")!!
            assertTrue("newest letter in a is mine", a.lastOutgoing)
            assertFalse(b.lastOutgoing)
            assertFalse(a.partnerLeft)
            assertEquals(2, room.mail().recentLetters("a", 10).size)
        }
        room.close()
    }
}
