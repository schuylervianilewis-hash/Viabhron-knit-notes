package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND colorTheme = :colorName ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByColor(colorName: String): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE isArchived = 0")
    suspend fun getActiveNoteCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateArchiveStatus(id: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET colorTheme = :colorName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNoteColor(id: Long, colorName: String, updatedAt: Long = System.currentTimeMillis())
    @Query("SELECT * FROM notes WHERE isArchived = 0 AND folderName = :folderName ORDER BY orderIndex ASC, updatedAt DESC")
    fun getNotesByFolder(folderName: String): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT folderName FROM notes WHERE folderName IS NOT NULL AND folderName != ''")
    fun getAllFolderNames(): Flow<List<String>>

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteNotesByIds(ids: List<Long>)

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateBatchPinStatus(ids: List<Long>, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateBatchArchiveStatus(ids: List<Long>, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET colorTheme = :colorName, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateBatchNoteColor(ids: List<Long>, colorName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folderName = :folderName, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateBatchFolderName(ids: List<Long>, folderName: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateNoteOrder(id: Long, orderIndex: Int)
}
