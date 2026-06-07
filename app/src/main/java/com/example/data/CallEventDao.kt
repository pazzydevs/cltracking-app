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

    @Query("SELECT * FROM call_events ORDER BY timestamp DESC")
    suspend fun getAllEventsDirect(): List<CallEvent>

    @Query("SELECT * FROM call_events WHERE eventId = :id")
    suspend fun getEventById(id: String): CallEvent?

    @Query("DELETE FROM call_events WHERE eventId = :id")
    suspend fun deleteEventById(id: String)

    @Query("SELECT * FROM call_events WHERE source = :source AND (status = 'ringing' OR status = 'active') AND timestamp >= :fromTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentOpenWhatsAppEvent(source: String, fromTime: Long): CallEvent?

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

    @Query("SELECT * FROM call_events WHERE source = :source AND contactName = :contactName AND timestamp >= :minTime AND timestamp <= :maxTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentEvent(source: String, contactName: String, minTime: Long, maxTime: Long): CallEvent?

    @Query("SELECT * FROM call_events WHERE source = :source AND (phoneNumber = :phoneOrContact OR contactName = :phoneOrContact) AND timestamp >= :fromTime AND timestamp <= :toTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentOpenEvent(source: String, phoneOrContact: String, fromTime: Long, toTime: Long): CallEvent?

    @Query("SELECT * FROM call_events WHERE (phoneNumber = :phoneOrContact OR contactName = :phoneOrContact) AND (status = 'missed' OR status = 'declined') AND timestamp >= :fromTime AND timestamp <= :toTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentMissedEventForCallback(phoneOrContact: String, fromTime: Long, toTime: Long): CallEvent?

    @Query("SELECT * FROM call_events WHERE source = :source AND timestamp >= :fromTime AND timestamp <= :toTime ORDER BY timestamp DESC")
    suspend fun findEventsInWindow(source: String, fromTime: Long, toTime: Long): List<CallEvent>

    @Query("SELECT COUNT(*) FROM call_events WHERE isSynced = 0")
    fun countPendingSync(): Flow<Int>

    @Query("SELECT * FROM call_events ORDER BY timestamp DESC LIMIT 1")
    fun getLastEvent(): Flow<CallEvent?>
    
    @Query("DELETE FROM call_events")
    suspend fun clear()
}
