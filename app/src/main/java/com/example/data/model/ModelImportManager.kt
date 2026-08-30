package com.example.data.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.ModelInfoEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class ModelValidationResult(
    val isValid: Boolean,
    val format: String,
    val modelTier: String,
    val fileSizeBytes: Long,
    val errorMessage: String? = null
)

object ModelImportManager {

    private const val MODELS_DIR_NAME = "models"

    fun getModelsDirectory(context: Context): File {
        val dir = File(context.filesDir, MODELS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun inspectAndImportModel(
        context: Context,
        uri: Uri
    ): Result<ModelInfoEntity> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var displayName = "whisper_model.bin"
            var reportedSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    if (sizeIndex != -1) {
                        reportedSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Inspecting model file: '$displayName' (${reportedSize / (1024 * 1024)} MB)"
            )

            // Validate header bytes from input stream
            val validation = validateModelStream(contentResolver.openInputStream(uri), displayName, reportedSize)
            if (!validation.isValid) {
                val err = validation.errorMessage ?: "Invalid model file structure"
                LogKeeperManager.log(LogTag.VoiceEngine, "Model validation failed: $err")
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            // Copy to sandboxed internal storage
            val modelsDir = getModelsDirectory(context)
            val destinationFile = File(modelsDir, displayName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream for URI"))

            val actualSize = destinationFile.length()

            val entity = ModelInfoEntity(
                fileName = displayName,
                filePath = destinationFile.absolutePath,
                fileSizeBytes = actualSize,
                format = validation.format,
                modelTier = validation.modelTier,
                isActive = true // Make recently imported model active by default
            )

            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Successfully imported model: $displayName [Format: ${entity.format}, Tier: ${entity.modelTier}, Size: ${actualSize / (1024 * 1024)} MB]"
            )

            Result.success(entity)
        } catch (e: Exception) {
            LogKeeperManager.log(LogTag.VoiceEngine, "Error during model import: ${e.message}")
            Result.failure(e)
        }
    }

    private fun validateModelStream(
        inputStream: InputStream?,
        fileName: String,
        reportedSize: Long
    ): ModelValidationResult {
        if (inputStream == null) {
            return ModelValidationResult(
                isValid = false,
                format = "UNKNOWN",
                modelTier = "Unknown",
                fileSizeBytes = 0L,
                errorMessage = "Unable to read model stream"
            )
        }

        try {
            val headerBytes = ByteArray(16)
            val bytesRead = inputStream.use { it.read(headerBytes) }

            if (bytesRead < 4) {
                return ModelValidationResult(
                    isValid = false,
                    format = "UNKNOWN",
                    modelTier = "Unknown",
                    fileSizeBytes = reportedSize,
                    errorMessage = "File is empty or truncated"
                )
            }

            // Check GGUF Magic "GGUF" (0x47, 0x47, 0x55, 0x46)
            val isGguf = headerBytes[0] == 0x47.toByte() &&
                    headerBytes[1] == 0x47.toByte() &&
                    headerBytes[2] == 0x55.toByte() &&
                    headerBytes[3] == 0x46.toByte()

            // Check GGML magic "ggml" or "ggmf" or "ggjt"
            val isGgml = (headerBytes[0] == 0x67.toByte() && headerBytes[1] == 0x67.toByte() &&
                    (headerBytes[2] == 0x6d.toByte() || headerBytes[2] == 0x6a.toByte()))

            // Check TFLite magic "TFL3"
            val isTfLite = headerBytes[4] == 0x54.toByte() &&
                    headerBytes[5] == 0x46.toByte() &&
                    headerBytes[6] == 0x4C.toByte() &&
                    headerBytes[7] == 0x33.toByte()

            val format = when {
                isGguf -> "GGUF"
                isGgml -> "GGML_BIN"
                isTfLite -> "TFLITE"
                fileName.endsWith(".gguf", ignoreCase = true) -> "GGUF"
                fileName.endsWith(".bin", ignoreCase = true) -> "GGML_BIN"
                fileName.endsWith(".tflite", ignoreCase = true) -> "TFLITE"
                fileName.endsWith(".onnx", ignoreCase = true) -> "ONNX"
                else -> "BINARY"
            }

            val sizeMb = reportedSize / (1024 * 1024)
            val tier = when {
                sizeMb in 20..55 || fileName.contains("tiny", ignoreCase = true) -> "Tiny (~39MB)"
                sizeMb in 56..160 || fileName.contains("base", ignoreCase = true) -> "Base (~75MB)"
                sizeMb in 161..500 || fileName.contains("small", ignoreCase = true) -> "Small (~240MB)"
                sizeMb > 500 || fileName.contains("medium", ignoreCase = true) -> "Medium (~760MB)"
                else -> "Custom ($sizeMb MB)"
            }

            return ModelValidationResult(
                isValid = true,
                format = format,
                modelTier = tier,
                fileSizeBytes = reportedSize
            )
        } catch (e: Exception) {
            return ModelValidationResult(
                isValid = false,
                format = "UNKNOWN",
                modelTier = "Unknown",
                fileSizeBytes = 0L,
                errorMessage = e.localizedMessage ?: "Failed reading model header"
            )
        }
    }

    suspend fun deleteModelFile(model: ModelInfoEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(model.filePath)
            val deleted = if (file.exists()) file.delete() else true
            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Deleted model file: ${model.fileName} (Result: $deleted)"
            )
            deleted
        } catch (e: Exception) {
            LogKeeperManager.log(
                LogTag.VoiceEngine,
                "Failed to delete model file: ${model.fileName} (${e.message})"
            )
            false
        }
    }
}
