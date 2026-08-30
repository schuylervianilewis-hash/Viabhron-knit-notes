package com.example.ui.models

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ModelInfoEntity
import com.example.data.db.VoiceNotesDatabase
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.ModelImportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ModelImportUiState {
    data object Idle : ModelImportUiState
    data class Importing(val progressMessage: String) : ModelImportUiState
    data class Success(val modelName: String) : ModelImportUiState
    data class Error(val message: String) : ModelImportUiState
}

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = VoiceNotesDatabase.getDatabase(application, viewModelScope)
    private val modelDao = database.modelDao()

    val models: StateFlow<List<ModelInfoEntity>> = modelDao.getAllModels().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeModel: StateFlow<ModelInfoEntity?> = modelDao.getActiveModel().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _importState = MutableStateFlow<ModelImportUiState>(ModelImportUiState.Idle)
    val importState: StateFlow<ModelImportUiState> = _importState.asStateFlow()

    fun importModelFromUri(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ModelImportUiState.Importing("Reading and validating model file...")
            LogKeeperManager.log(LogTag.VoiceEngine, "Starting model import from URI...")

            val result = ModelImportManager.inspectAndImportModel(getApplication(), uri)
            result.onSuccess { modelEntity ->
                // Deactivate current active models and set new model as active
                modelDao.deactivateAllModels()
                val newId = modelDao.insertModel(modelEntity.copy(isActive = true))
                LogKeeperManager.log(
                    LogTag.VoiceEngine,
                    "Model registered in database with ID #$newId (${modelEntity.fileName})"
                )
                _importState.value = ModelImportUiState.Success(modelEntity.fileName)
            }.onFailure { exception ->
                val errorMsg = exception.message ?: "Failed to import model"
                LogKeeperManager.log(LogTag.VoiceEngine, "Import failed: $errorMsg")
                _importState.value = ModelImportUiState.Error(errorMsg)
            }
        }
    }

    fun setActiveModel(model: ModelInfoEntity) {
        viewModelScope.launch {
            modelDao.deactivateAllModels()
            modelDao.setActiveModel(model.id)
            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Switched active speech model to: ${model.fileName} (${model.modelTier})"
            )
        }
    }

    fun deleteModel(model: ModelInfoEntity) {
        viewModelScope.launch {
            ModelImportManager.deleteModelFile(model)
            modelDao.deleteModel(model)
            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Model deleted from app database and internal storage: ${model.fileName}"
            )
        }
    }

    fun clearImportStatus() {
        _importState.value = ModelImportUiState.Idle
    }
}
