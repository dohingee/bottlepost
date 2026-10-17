package com.bottlepost.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MailDao {
    @Query("SELECT * FROM threads ORDER BY lastAt DESC")
    abstract fun observeThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    abstract fun observeThread(threadId: String): Flow<ThreadEntity?>

    @Query("SELECT * FROM threads WHERE threadId = :threadId")
    abstract suspend fun thread(threadId: String): ThreadEntity?

    @Query("SELECT * FROM letters WHERE threadId = :threadId ORDER BY sentAt ASC")
    abstract fun observeLetters(threadId: String): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters WHERE threadId = :threadId ORDER BY sentAt DESC LIMIT :limit")
    abstract suspend fun recentLetters(threadId: String, limit: Int): List<LetterEntity>

    @Upsert
    abstract suspend fun upsertThread(thread: ThreadEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLetter(letter: LetterEntity): Long

    @Query("UPDATE threads SET unread = 0 WHERE threadId = :threadId")
    abstract suspend fun markRead(threadId: String)

    @Query("UPDATE threads SET closed = 1 WHERE threadId = :threadId")
    abstract suspend fun close(threadId: String)

    @Query("UPDATE threads SET closed = 1, partnerLeft = 1 WHERE threadId = :threadId")
    abstract suspend fun markPartnerLeft(threadId: String)

    @Query("DELETE FROM threads WHERE threadId = :threadId")
    abstract suspend fun deleteThreadRow(threadId: String)

    @Query("DELETE FROM letters WHERE threadId = :threadId")
    abstract suspend fun deleteLetters(threadId: String)

    @Transaction
    open suspend fun deleteThread(threadId: String) {
        deleteLetters(threadId)
        deleteThreadRow(threadId)
    }

    @Query("SELECT * FROM threads")
    abstract suspend fun allThreads(): List<ThreadEntity>

    @Query("SELECT * FROM letters")
    abstract suspend fun allLetters(): List<LetterEntity>

    @Query("DELETE FROM threads")
    abstract suspend fun clearThreads()

    @Query("DELETE FROM letters")
    abstract suspend fun clearLetters()

    @Transaction
    open suspend fun replaceAll(threads: List<ThreadEntity>, letters: List<LetterEntity>) {
        clearThreads()
        clearLetters()
        threads.forEach { upsertThread(it) }
        letters.forEach { insertLetter(it) }
    }
}

@Database(entities = [ThreadEntity::class, LetterEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mail(): MailDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "bottlepost.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: "partner left" flag for the leave-conversation feature. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE threads ADD COLUMN partnerLeft INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: whose turn it is, shown in the conversation list; filled in from the newest letter. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE threads ADD COLUMN lastOutgoing INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE threads SET lastOutgoing = COALESCE((SELECT outgoing FROM letters " +
                        "WHERE letters.threadId = threads.threadId ORDER BY sentAt DESC LIMIT 1), 0)",
                )
            }
        }
    }
}
