package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceCommandDao {

    @Query("SELECT * FROM voice_commands ORDER BY category ASC, LENGTH(trigger_phrase) DESC")
    fun getAllCommandsFlow(): Flow<List<VoiceCommandEntity>>

    @Query("SELECT * FROM voice_commands WHERE is_enabled = 1 ORDER BY LENGTH(trigger_phrase) DESC")
    fun getEnabledCommandsFlow(): Flow<List<VoiceCommandEntity>>

    @Query("SELECT * FROM voice_commands WHERE is_enabled = 1 ORDER BY LENGTH(trigger_phrase) DESC")
    suspend fun getEnabledCommands(): List<VoiceCommandEntity>

    @Query("SELECT COUNT(*) FROM voice_commands")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: VoiceCommandEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(commands: List<VoiceCommandEntity>)

    @Update
    suspend fun updateCommand(command: VoiceCommandEntity)

    @Delete
    suspend fun deleteCommand(command: VoiceCommandEntity)

    @Query("DELETE FROM voice_commands WHERE id IN (:ids)")
    suspend fun deleteCommandsByIds(ids: List<Long>)

    @Query("UPDATE voice_commands SET is_enabled = :enabled WHERE id IN (:ids)")
    suspend fun updateEnabledStatus(ids: List<Long>, enabled: Boolean)

    @Query("DELETE FROM voice_commands")
    suspend fun deleteAll()
}
