package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordReplacementDao {

    @Query("SELECT * FROM word_replacements ORDER BY LENGTH(target_phrase) DESC, target_phrase ASC")
    fun getAllReplacementsFlow(): Flow<List<WordReplacementEntity>>

    @Query("SELECT * FROM word_replacements WHERE is_enabled = 1 ORDER BY LENGTH(target_phrase) DESC")
    fun getEnabledReplacementsFlow(): Flow<List<WordReplacementEntity>>

    @Query("SELECT * FROM word_replacements WHERE is_enabled = 1 ORDER BY LENGTH(target_phrase) DESC")
    suspend fun getEnabledReplacements(): List<WordReplacementEntity>

    @Query("SELECT * FROM word_replacements WHERE id = :id LIMIT 1")
    suspend fun getReplacementById(id: Long): WordReplacementEntity?

    @Query("SELECT COUNT(*) FROM word_replacements")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplacement(replacement: WordReplacementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(replacements: List<WordReplacementEntity>)

    @Update
    suspend fun updateReplacement(replacement: WordReplacementEntity)

    @Delete
    suspend fun deleteReplacement(replacement: WordReplacementEntity)

    @Query("DELETE FROM word_replacements WHERE id IN (:ids)")
    suspend fun deleteReplacementsByIds(ids: List<Long>)

    @Query("UPDATE word_replacements SET is_enabled = :enabled WHERE id IN (:ids)")
    suspend fun updateEnabledStatus(ids: List<Long>, enabled: Boolean)

    @Query("UPDATE word_replacements SET is_enabled = :enabled WHERE category = :category")
    suspend fun updateCategoryEnabledStatus(category: String, enabled: Boolean)

    @Query("DELETE FROM word_replacements")
    suspend fun deleteAll()
}
