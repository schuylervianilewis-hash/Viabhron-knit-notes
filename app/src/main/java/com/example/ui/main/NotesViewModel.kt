package com.example.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.NoteEntity
import com.example.data.db.VoiceNotesDatabase
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.NoteColor
import com.example.data.repository.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NoteSortOrder(val displayName: String) {
    MODIFIED_DESC("Modified time (Z - A)"),
    MODIFIED_ASC("Modified time (A - Z)"),
    CREATED_DESC("Created time (Newest)"),
    ALPHABETICAL("Alphabetical (A - Z)"),
    COLOR("Color Grouping")
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = VoiceNotesDatabase.getDatabase(application, viewModelScope)
    val repository = NotesRepository(database.noteDao())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedColorFilter = MutableStateFlow<NoteColor?>(null)
    val selectedColorFilter: StateFlow<NoteColor?> = _selectedColorFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(NoteSortOrder.MODIFIED_DESC)
    val sortOrder: StateFlow<NoteSortOrder> = _sortOrder.asStateFlow()

    val notes: StateFlow<List<NoteEntity>> = combine(
        _searchQuery,
        _selectedColorFilter,
        _sortOrder
    ) { query, colorFilter, sort ->
        Triple(query, colorFilter, sort)
    }.flatMapLatest { (query, colorFilter, sort) ->
        val baseFlow = if (query.isNotBlank()) {
            repository.searchNotes(query.trim())
        } else if (colorFilter != null) {
            repository.getNotesByColor(colorFilter)
        } else {
            repository.activeNotes
        }

        combine(baseFlow) { notesListArray ->
            val list = notesListArray[0]
            when (sort) {
                NoteSortOrder.MODIFIED_DESC -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt }
                )
                NoteSortOrder.MODIFIED_ASC -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.updatedAt }
                )
                NoteSortOrder.CREATED_DESC -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.createdAt }
                )
                NoteSortOrder.ALPHABETICAL -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.title.lowercase() }
                )
                NoteSortOrder.COLOR -> list.sortedWith(
                    compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.colorTheme }
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val archivedNotes: StateFlow<List<NoteEntity>> = repository.archivedNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allActiveNotes: StateFlow<List<NoteEntity>> = repository.activeNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allFolders: StateFlow<List<String>> = repository.getAllFolderNames().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Multi-Select State
    private val _selectedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedNoteIds: StateFlow<Set<Long>> = _selectedNoteIds.asStateFlow()

    fun toggleNoteSelection(noteId: Long) {
        val current = _selectedNoteIds.value
        _selectedNoteIds.value = if (current.contains(noteId)) {
            current - noteId
        } else {
            current + noteId
        }
    }

    fun selectAll(noteIds: List<Long>) {
        _selectedNoteIds.value = noteIds.toSet()
        LogKeeperManager.log(LogTag.UI_Editor, "Multi-select: Selected all ${noteIds.size} notes")
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
        LogKeeperManager.log(LogTag.UI_Editor, "Multi-select: Selection cleared")
    }

    fun batchSetColor(color: NoteColor) {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.batchSetColor(ids, color)
                clearSelection()
            }
        }
    }

    fun batchSetPin(isPinned: Boolean) {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.batchSetPin(ids, isPinned)
                clearSelection()
            }
        }
    }

    fun batchArchive() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.batchSetArchive(ids, true)
                clearSelection()
            }
        }
    }

    fun batchDelete() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.batchDelete(ids)
                clearSelection()
            }
        }
    }

    fun batchSetFolder(folderName: String?) {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                repository.batchSetFolder(ids, folderName)
                clearSelection()
            }
        }
    }

    fun reorderNoteInFolder(id: Long, newIndex: Int) {
        viewModelScope.launch {
            repository.updateNoteOrder(id, newIndex)
        }
    }

    fun importNotesFromPdf(folderName: String, importedNotes: List<NoteEntity>) {
        viewModelScope.launch {
            for (note in importedNotes) {
                repository.insertNote(note)
            }
            LogKeeperManager.log(LogTag.Storage, "Imported ${importedNotes.size} notes into folder '$folderName'")
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            LogKeeperManager.log(LogTag.UI_Editor, "Searching notes: '$query'")
        }
    }

    fun onColorFilterSelected(color: NoteColor?) {
        _selectedColorFilter.value = color
        LogKeeperManager.log(LogTag.UI_Editor, "Color filter set to: ${color?.displayName ?: "All"}")
    }

    fun cycleSortOrder() {
        val nextOrder = when (_sortOrder.value) {
            NoteSortOrder.MODIFIED_DESC -> NoteSortOrder.MODIFIED_ASC
            NoteSortOrder.MODIFIED_ASC -> NoteSortOrder.CREATED_DESC
            NoteSortOrder.CREATED_DESC -> NoteSortOrder.ALPHABETICAL
            NoteSortOrder.ALPHABETICAL -> NoteSortOrder.COLOR
            NoteSortOrder.COLOR -> NoteSortOrder.MODIFIED_DESC
        }
        _sortOrder.value = nextOrder
        LogKeeperManager.log(LogTag.UI_Editor, "Sort order changed: ${nextOrder.displayName}")
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.togglePin(note.id, note.isPinned)
        }
    }

    fun toggleArchive(note: NoteEntity) {
        viewModelScope.launch {
            repository.toggleArchive(note.id, note.isArchived)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun updateNoteColor(note: NoteEntity, color: NoteColor) {
        viewModelScope.launch {
            repository.updateNoteColor(note.id, color)
        }
    }
}
