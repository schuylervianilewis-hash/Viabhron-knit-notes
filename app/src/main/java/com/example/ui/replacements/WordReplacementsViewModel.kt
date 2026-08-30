package com.example.ui.replacements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.VoiceCommandEntity
import com.example.data.db.VoiceNotesDatabase
import com.example.data.db.WordReplacementEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReplacementTab {
    WORD_REPLACEMENTS,
    VOICE_COMMANDS
}

data class WordReplacementsUiState(
    val selectedTab: ReplacementTab = ReplacementTab.WORD_REPLACEMENTS,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val selectedItemIds: Set<Long> = emptySet(),
    val editingReplacement: WordReplacementEntity? = null,
    val editingCommand: VoiceCommandEntity? = null,
    val isAddDialogOpen: Boolean = false,
    val isDeleteConfirmDialogOpen: Boolean = false,
    val userMessage: String? = null
)

class WordReplacementsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = VoiceNotesDatabase.getDatabase(application, viewModelScope)
    private val wordReplacementDao = database.wordReplacementDao()
    private val voiceCommandDao = database.voiceCommandDao()

    val replacements: StateFlow<List<WordReplacementEntity>> = wordReplacementDao.getAllReplacementsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceCommands: StateFlow<List<VoiceCommandEntity>> = voiceCommandDao.getAllCommandsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(WordReplacementsUiState())
    val uiState: StateFlow<WordReplacementsUiState> = _uiState.asStateFlow()

    init {
        // Ensure default rules and commands exist if database is newly initialized
        viewModelScope.launch {
            if (wordReplacementDao.getCount() == 0) {
                VoiceNotesDatabase.populateDefaultKnittingReplacements(wordReplacementDao)
            }
            if (voiceCommandDao.getCount() == 0) {
                VoiceNotesDatabase.populateDefaultVoiceCommands(voiceCommandDao)
            }
        }
    }

    fun selectTab(tab: ReplacementTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                selectedCategory = null,
                selectedItemIds = emptySet()
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onCategoryFilterChanged(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val currentSelected = state.selectedItemIds
            val newSelected = if (currentSelected.contains(id)) {
                currentSelected - id
            } else {
                currentSelected + id
            }
            state.copy(selectedItemIds = newSelected)
        }
    }

    fun selectAll(ids: List<Long>) {
        _uiState.update { it.copy(selectedItemIds = ids.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItemIds = emptySet()) }
    }

    // --- Word Replacements Actions ---

    fun toggleCategory(category: String, enable: Boolean) {
        viewModelScope.launch {
            wordReplacementDao.updateCategoryEnabledStatus(category, enable)
            LogKeeperManager.log(
                LogTag.UI_Editor,
                "Toggled all rules in category '$category': enabled=$enable"
            )
            _uiState.update {
                it.copy(userMessage = "${if (enable) "Enabled" else "Disabled"} all '$category' rules.")
            }
        }
    }

    fun toggleRuleEnabled(replacement: WordReplacementEntity) {
        viewModelScope.launch {
            val updated = replacement.copy(isEnabled = !replacement.isEnabled)
            wordReplacementDao.updateReplacement(updated)
            LogKeeperManager.log(
                LogTag.UI_Editor,
                "Toggled rule '${replacement.targetPhrase}': enabled=${updated.isEnabled}"
            )
        }
    }

    fun openAddReplacementDialog() {
        _uiState.update { it.copy(isAddDialogOpen = true, editingReplacement = null, editingCommand = null) }
    }

    fun openEditReplacementDialog(replacement: WordReplacementEntity) {
        _uiState.update { it.copy(isAddDialogOpen = true, editingReplacement = replacement, editingCommand = null) }
    }

    fun saveReplacement(
        targetPhrase: String,
        replacementPhrase: String,
        category: String,
        isMatchCase: Boolean,
        isEnabled: Boolean
    ) {
        if (targetPhrase.isBlank() || replacementPhrase.isBlank()) {
            _uiState.update { it.copy(userMessage = "Target and replacement phrases cannot be empty.") }
            return
        }

        viewModelScope.launch {
            val currentEditing = _uiState.value.editingReplacement
            if (currentEditing != null) {
                val updated = currentEditing.copy(
                    targetPhrase = targetPhrase.trim(),
                    replacementPhrase = replacementPhrase.trim(),
                    category = category.trim().ifBlank { "General" },
                    isMatchCase = isMatchCase,
                    isEnabled = isEnabled
                )
                wordReplacementDao.updateReplacement(updated)
                LogKeeperManager.log(LogTag.UI_Editor, "Updated word replacement rule: '${updated.targetPhrase}' -> '${updated.replacementPhrase}'")
            } else {
                val newRule = WordReplacementEntity(
                    targetPhrase = targetPhrase.trim(),
                    replacementPhrase = replacementPhrase.trim(),
                    category = category.trim().ifBlank { "General" },
                    isMatchCase = isMatchCase,
                    isEnabled = isEnabled
                )
                wordReplacementDao.insertReplacement(newRule)
                LogKeeperManager.log(LogTag.UI_Editor, "Created word replacement rule: '${newRule.targetPhrase}' -> '${newRule.replacementPhrase}'")
            }
            dismissDialog()
            _uiState.update { it.copy(userMessage = "Rule saved successfully.") }
        }
    }

    fun deleteReplacement(replacement: WordReplacementEntity) {
        viewModelScope.launch {
            wordReplacementDao.deleteReplacement(replacement)
            _uiState.update { state ->
                state.copy(
                    selectedItemIds = state.selectedItemIds - replacement.id,
                    userMessage = "Rule '${replacement.targetPhrase}' deleted."
                )
            }
            LogKeeperManager.log(LogTag.UI_Editor, "Deleted word replacement rule: '${replacement.targetPhrase}'")
        }
    }

    // --- Voice Commands Actions ---

    fun toggleCommandEnabled(command: VoiceCommandEntity) {
        viewModelScope.launch {
            val updated = command.copy(isEnabled = !command.isEnabled)
            voiceCommandDao.updateCommand(updated)
            LogKeeperManager.log(
                LogTag.UI_Editor,
                "Toggled voice command '${command.displayName}': enabled=${updated.isEnabled}"
            )
        }
    }

    fun openAddCommandDialog() {
        _uiState.update { it.copy(isAddDialogOpen = true, editingCommand = null, editingReplacement = null) }
    }

    fun openEditCommandDialog(command: VoiceCommandEntity) {
        _uiState.update { it.copy(isAddDialogOpen = true, editingCommand = command, editingReplacement = null) }
    }

    fun saveVoiceCommand(
        triggerPhrase: String,
        displayName: String,
        description: String,
        commandType: String,
        category: String,
        isEnabled: Boolean
    ) {
        if (triggerPhrase.isBlank() || displayName.isBlank()) {
            _uiState.update { it.copy(userMessage = "Trigger phrase and display name cannot be empty.") }
            return
        }

        viewModelScope.launch {
            val currentEditing = _uiState.value.editingCommand
            if (currentEditing != null) {
                val updated = currentEditing.copy(
                    triggerPhrase = triggerPhrase.trim().lowercase(),
                    displayName = displayName.trim(),
                    description = description.trim(),
                    commandType = commandType,
                    category = category.trim().ifBlank { "Navigation" },
                    isEnabled = isEnabled
                )
                voiceCommandDao.updateCommand(updated)
                LogKeeperManager.log(LogTag.UI_Editor, "Updated voice command: '${updated.displayName}'")
            } else {
                val newCommand = VoiceCommandEntity(
                    triggerPhrase = triggerPhrase.trim().lowercase(),
                    displayName = displayName.trim(),
                    description = description.trim(),
                    commandType = commandType,
                    category = category.trim().ifBlank { "Navigation" },
                    isEnabled = isEnabled
                )
                voiceCommandDao.insertCommand(newCommand)
                LogKeeperManager.log(LogTag.UI_Editor, "Created voice command: '${newCommand.displayName}'")
            }
            dismissDialog()
            _uiState.update { it.copy(userMessage = "Voice command saved successfully.") }
        }
    }

    fun deleteCommand(command: VoiceCommandEntity) {
        viewModelScope.launch {
            voiceCommandDao.deleteCommand(command)
            _uiState.update { state ->
                state.copy(
                    selectedItemIds = state.selectedItemIds - command.id,
                    userMessage = "Command '${command.displayName}' deleted."
                )
            }
            LogKeeperManager.log(LogTag.UI_Editor, "Deleted voice command: '${command.displayName}'")
        }
    }

    // --- Bulk Actions & Presets ---

    fun dismissDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false, editingReplacement = null, editingCommand = null) }
    }

    fun deleteSelected() {
        val selectedIds = _uiState.value.selectedItemIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            if (_uiState.value.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
                wordReplacementDao.deleteReplacementsByIds(selectedIds)
            } else {
                voiceCommandDao.deleteCommandsByIds(selectedIds)
            }
            LogKeeperManager.log(LogTag.UI_Editor, "Deleted ${selectedIds.size} items in bulk")
            _uiState.update {
                it.copy(
                    selectedItemIds = emptySet(),
                    isDeleteConfirmDialogOpen = false,
                    userMessage = "Deleted ${selectedIds.size} items."
                )
            }
        }
    }

    fun enableSelected(enable: Boolean) {
        val selectedIds = _uiState.value.selectedItemIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            if (_uiState.value.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
                wordReplacementDao.updateEnabledStatus(selectedIds, enable)
            } else {
                voiceCommandDao.updateEnabledStatus(selectedIds, enable)
            }
            LogKeeperManager.log(LogTag.UI_Editor, "Set ${selectedIds.size} items enabled=$enable")
            clearSelection()
            _uiState.update {
                it.copy(userMessage = "${if (enable) "Enabled" else "Disabled"} ${selectedIds.size} items.")
            }
        }
    }

    fun resetToDefaultKnittingPreset() {
        viewModelScope.launch {
            if (_uiState.value.selectedTab == ReplacementTab.WORD_REPLACEMENTS) {
                VoiceNotesDatabase.populateDefaultKnittingReplacements(wordReplacementDao)
                _uiState.update { it.copy(userMessage = "Loaded default Knitting shorthand dictionary.") }
            } else {
                VoiceNotesDatabase.populateDefaultVoiceCommands(voiceCommandDao)
                _uiState.update { it.copy(userMessage = "Loaded default Voice Commands & Macros.") }
            }
        }
    }

    fun setDeleteConfirmDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDeleteConfirmDialogOpen = isOpen) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
