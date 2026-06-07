package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallEventDao {

    @Query("SELECT * FROM call_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<CallEvent>>

    @Query("SELECT * FROM call_events WHERE isSynced = 0")
    suspend fun getUnsyncedEvents(): List<CallEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CallEvent)

    @Query("UPDATE call_events SET isSynced = 1 WHERE eventId = :id")
    suspend fun markEventAsSynced(id: String)

    @Query("UPDATE call_events SET isSynced = 1 WHERE eventId IN (:ids)")
    suspend fun markEventsAsSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM call_events WHERE timestamp >= :startOfToday")
    fun countEventsToday(startOfToday: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_events WHERE source = :source AND timestamp >= :minTime AND timestamp <= :maxTime")
    suspend fun hasEventProximity(source: String, minTime: Long, maxTime: Long): Int

    @Query("SELECT COUNT(*) FROM call_events WHERE isSynced = 0")
    fun countPendingSync(): Flow<Int>

    @Query("SELECT * FROM call_events ORDER BY timestamp DESC LIMIT 1")
    fun getLastEvent(): Flow<CallEvent?>
    
    @Query("DELETE FROM call_events")
    suspend fun clear()
}
