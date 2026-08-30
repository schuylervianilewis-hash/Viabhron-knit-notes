package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY importedAt DESC")
    fun getAllModels(): Flow<List<ModelInfoEntity>>

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    fun getActiveModel(): Flow<ModelInfoEntity?>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: Long): ModelInfoEntity?

    @Query("SELECT * FROM models WHERE fileName = :fileName LIMIT 1")
    suspend fun getModelByFileName(fileName: String): ModelInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelInfoEntity): Long

    @Update
    suspend fun updateModel(model: ModelInfoEntity)

    @Delete
    suspend fun deleteModel(model: ModelInfoEntity)

    @Query("UPDATE models SET isActive = 0")
    suspend fun deactivateAllModels()

    @Query("UPDATE models SET isActive = 1 WHERE id = :modelId")
    suspend fun setActiveModel(modelId: Long)
}
