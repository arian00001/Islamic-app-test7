package com.islamicapp.noor.Storage

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "tasbih_sessions")
data class TasbihRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dhikr: String,
    val count: Int,
    val target: Int,
    val completed: Boolean,
    val createdAt: Long
)

@Entity(tableName = "prayer_log")
data class PrayerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val prayer: String,
    val done: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "listening_days")
data class ListeningDay(
    @PrimaryKey val day: String,
    val seconds: Long,
    val updatedAt: Long
)

@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val blockedAt: Long,
    val released: Boolean
)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val minutes: Int,
    val completed: Boolean
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val refKey: String,
    val label: String,
    val createdAt: Long
)

@Database(
    entities = [
        TasbihRecord::class,
        PrayerLog::class,
        ListeningDay::class,
        BlockEvent::class,
        FocusSession::class,
        Bookmark::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tasbihDao(): TasbihDao
    abstract fun prayerDao(): PrayerDao
    abstract fun listeningDao(): ListeningDao
    abstract fun blockDao(): BlockDao
    abstract fun focusDao(): FocusDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "noor.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
