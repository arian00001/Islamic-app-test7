package com.islamicapp.noor.Storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TasbihDao {

    @Insert
    suspend fun insert(record: TasbihRecord): Long

    @Query("SELECT * FROM tasbih_sessions ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<TasbihRecord>

    @Query("SELECT COALESCE(SUM(count), 0) FROM tasbih_sessions WHERE createdAt >= :fromMillis")
    suspend fun totalSince(fromMillis: Long): Int
}

@Dao
interface PrayerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: PrayerLog)

    @Query("SELECT * FROM prayer_log WHERE day = :day")
    suspend fun forDay(day: String): List<PrayerLog>

    @Query("SELECT COUNT(*) FROM prayer_log WHERE day = :day AND done = 1")
    suspend fun doneCount(day: String): Int
}

@Dao
interface ListeningDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: ListeningDay)

    @Query("SELECT * FROM listening_days ORDER BY day DESC LIMIT :limit")
    suspend fun recent(limit: Int = 30): List<ListeningDay>

    @Query("SELECT seconds FROM listening_days WHERE day = :day")
    suspend fun secondsFor(day: String): Long?
}

@Dao
interface BlockDao {

    @Insert
    suspend fun insert(event: BlockEvent)

    @Query("SELECT * FROM block_events ORDER BY blockedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<BlockEvent>
}

@Dao
interface FocusDao {

    @Insert
    suspend fun insert(session: FocusSession): Long

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 30): List<FocusSession>
}

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark): Long

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun all(): List<Bookmark>

    @Query("DELETE FROM bookmarks WHERE kind = :kind AND refKey = :refKey")
    suspend fun remove(kind: String, refKey: String)
}
