package com.example.data.repository

import com.example.data.db.NoteDao
import com.example.data.db.NoteEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.NoteColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class NotesRepository(
    private val noteDao: NoteDao
) {
    val activeNotes: Flow<List<NoteEntity>> = noteDao.getActiveNotes()
        .onEach { notes ->
            LogKeeperManager.log(LogTag.Storage, "Loaded ${notes.size} active notes from database")
        }

    val archivedNotes: Flow<List<NoteEntity>> = noteDao.getArchivedNotes()

    fun getNoteById(id: Long): Flow<NoteEntity?> = noteDao.getNoteById(id)

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    fun getNotesByColor(color: NoteColor): Flow<List<NoteEntity>> = noteDao.getNotesByColor(color.name)

    suspend fun insertNote(note: NoteEntity): Long {
        val id = noteDao.insertNote(note)
        LogKeeperManager.log(LogTag.Storage, "Note created #$id: '${note.title}' (${note.colorTheme})")
        return id
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(note)
        LogKeeperManager.log(LogTag.Storage, "Note updated #${note.id}: '${note.title}'")
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
        LogKeeperManager.log(LogTag.Storage, "Note deleted #${note.id}: '${note.title}'")
    }

    suspend fun deleteNoteById(id: Long) {
        noteDao.deleteNoteById(id)
        LogKeeperManager.log(LogTag.Storage, "Note deleted #$id")
    }

    suspend fun togglePin(id: Long, currentPinState: Boolean) {
        val nextState = !currentPinState
        noteDao.updatePinStatus(id, nextState)
        LogKeeperManager.log(LogTag.Storage, "Note #$id pin status changed to: $nextState")
    }

    suspend fun toggleArchive(id: Long, currentArchiveState: Boolean) {
        val nextState = !currentArchiveState
        noteDao.updateArchiveStatus(id, nextState)
        LogKeeperManager.log(LogTag.Storage, "Note #$id archive status changed to: $nextState")
    }

    suspend fun updateNoteColor(id: Long, color: NoteColor) {
        noteDao.updateNoteColor(id, color.name)
        LogKeeperManager.log(LogTag.Storage, "Note #$id color updated to ${color.displayName}")
    }

    fun getNotesByFolder(folderName: String): Flow<List<NoteEntity>> = noteDao.getNotesByFolder(folderName)

    fun getAllFolderNames(): Flow<List<String>> = noteDao.getAllFolderNames()

    suspend fun batchDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        noteDao.deleteNotesByIds(ids)
        LogKeeperManager.log(LogTag.Storage, "Batch deleted ${ids.size} notes (IDs: $ids)")
    }

    suspend fun batchSetPin(ids: List<Long>, isPinned: Boolean) {
        if (ids.isEmpty()) return
        noteDao.updateBatchPinStatus(ids, isPinned)
        LogKeeperManager.log(LogTag.Storage, "Batch pin status changed to $isPinned for ${ids.size} notes")
    }

    suspend fun batchSetArchive(ids: List<Long>, isArchived: Boolean) {
        if (ids.isEmpty()) return
        noteDao.updateBatchArchiveStatus(ids, isArchived)
        LogKeeperManager.log(LogTag.Storage, "Batch archive status changed to $isArchived for ${ids.size} notes")
    }

    suspend fun batchSetColor(ids: List<Long>, color: NoteColor) {
        if (ids.isEmpty()) return
        noteDao.updateBatchNoteColor(ids, color.name)
        LogKeeperManager.log(LogTag.Storage, "Batch color set to ${color.displayName} for ${ids.size} notes")
    }

    suspend fun batchSetFolder(ids: List<Long>, folderName: String?) {
        if (ids.isEmpty()) return
        noteDao.updateBatchFolderName(ids, folderName)
        LogKeeperManager.log(LogTag.Storage, "Batch moved ${ids.size} notes to folder '${folderName ?: "None"}'")
    }

    suspend fun updateNoteOrder(id: Long, orderIndex: Int) {
        noteDao.updateNoteOrder(id, orderIndex)
    }

    suspend fun addQuickNote(
        title: String,
        content: String = "",
        color: NoteColor = NoteColor.YELLOW,
        isChecklist: Boolean = false,
        folderName: String? = null
    ): Long {
        val note = NoteEntity(
            title = title,
            content = content,
            colorTheme = color.name,
            isChecklist = isChecklist,
            folderName = folderName
        )
        return insertNote(note)
    }
}
