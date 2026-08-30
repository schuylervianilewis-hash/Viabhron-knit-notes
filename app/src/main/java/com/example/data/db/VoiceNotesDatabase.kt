package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.NoteColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [NoteEntity::class, ModelInfoEntity::class, WordReplacementEntity::class, VoiceCommandEntity::class],
    version = 6,
    exportSchema = false
)
abstract class VoiceNotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun modelDao(): ModelDao
    abstract fun wordReplacementDao(): WordReplacementDao
    abstract fun voiceCommandDao(): VoiceCommandDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceNotesDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): VoiceNotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceNotesDatabase::class.java,
                    "voice_notes_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            scope.launch(Dispatchers.IO) {
                                INSTANCE?.let { database ->
                                    populateDefaultKnittingReplacements(database.wordReplacementDao())
                                    populateDefaultVoiceCommands(database.voiceCommandDao())
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateDefaultKnittingReplacements(dao: WordReplacementDao) {
            val defaults = listOf(
                WordReplacementEntity(targetPhrase = "yarn over", replacementPhrase = "yo", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "slip slip knit", replacementPhrase = "ssk", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "slip slip purl", replacementPhrase = "ssp", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit two together", replacementPhrase = "k2tog", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit 2 together", replacementPhrase = "k2tog", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl two together", replacementPhrase = "p2tog", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl 2 together", replacementPhrase = "p2tog", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit 1", replacementPhrase = "k1", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit one", replacementPhrase = "k1", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit 2", replacementPhrase = "k2", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit two", replacementPhrase = "k2", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit 3", replacementPhrase = "k3", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit three", replacementPhrase = "k3", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit 4", replacementPhrase = "k4", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit four", replacementPhrase = "k4", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit 5", replacementPhrase = "k5", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "knit five", replacementPhrase = "k5", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl 1", replacementPhrase = "p1", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl one", replacementPhrase = "p1", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl 2", replacementPhrase = "p2", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl two", replacementPhrase = "p2", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl 3", replacementPhrase = "p3", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl three", replacementPhrase = "p3", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "make 1 left", replacementPhrase = "m1l", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "make one left", replacementPhrase = "m1l", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "make 1 right", replacementPhrase = "m1r", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "make one right", replacementPhrase = "m1r", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "make 1", replacementPhrase = "m1", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "make one", replacementPhrase = "m1", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "place marker", replacementPhrase = "pm", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "slip marker", replacementPhrase = "sm", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "wrong side", replacementPhrase = "WS", category = "Knitting", isEnabled = false),
                WordReplacementEntity(targetPhrase = "right side", replacementPhrase = "RS", category = "Knitting", isEnabled = false),
                // Sequence Counting & Aggregation Rules (Default: OFF)
                WordReplacementEntity(targetPhrase = "knit", replacementPhrase = "k", category = "Sequence", isEnabled = false),
                WordReplacementEntity(targetPhrase = "make", replacementPhrase = "m", category = "Sequence", isEnabled = false),
                WordReplacementEntity(targetPhrase = "purl", replacementPhrase = "p", category = "Sequence", isEnabled = false),
                WordReplacementEntity(targetPhrase = "slip", replacementPhrase = "sl", category = "Sequence", isEnabled = false),
                WordReplacementEntity(targetPhrase = "cast on", replacementPhrase = "co", category = "Sequence", isEnabled = false)
            )
            dao.insertAll(defaults)
            LogKeeperManager.log(LogTag.Storage, "Inserted ${defaults.size} starter word replacement rules (all OFF by default)")
        }

        suspend fun populateDefaultVoiceCommands(dao: VoiceCommandDao) {
            val defaults = listOf(
                VoiceCommandEntity(
                    commandType = "NEXT_ROW",
                    triggerPhrase = "next row",
                    displayName = "Next Row",
                    description = "Starts a new line with auto-incremented row count (e.g. 'Row 2: ')",
                    category = "Navigation",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "NEXT_ROW",
                    triggerPhrase = "next round",
                    displayName = "Next Round",
                    description = "Starts a new line with auto-incremented round count (e.g. 'Round 2: ')",
                    category = "Navigation",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "NEXT_LINE",
                    triggerPhrase = "next line",
                    displayName = "Next Line",
                    description = "Inserts a newline break in the note",
                    category = "Navigation",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "REPEAT_LAST_STITCH",
                    triggerPhrase = "repeat last stitch",
                    displayName = "Repeat Last Stitch",
                    description = "Repeats the last stitch token N times (e.g. 'repeat last stitch 3 times')",
                    category = "Repetition",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "REPEAT_LAST_STITCH",
                    triggerPhrase = "repeat last",
                    displayName = "Repeat Last",
                    description = "Repeats the preceding stitch or word N times",
                    category = "Repetition",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "REPEAT_LAST_GROUP",
                    triggerPhrase = "repeat last group",
                    displayName = "Repeat Last Group",
                    description = "Repeats the bracketed stitch group or last 2 stitches N times",
                    category = "Repetition",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "REPEAT_LAST_GROUP",
                    triggerPhrase = "repeat group",
                    displayName = "Repeat Group",
                    description = "Repeats the previous stitch sequence",
                    category = "Repetition",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "UNDO_LAST",
                    triggerPhrase = "undo last",
                    displayName = "Undo Last Stitch",
                    description = "Erases the last stitch token from the current row",
                    category = "Editing",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "INSERT_STAR",
                    triggerPhrase = "asterisk",
                    displayName = "Insert Repeat Marker",
                    description = "Inserts a repeat asterisk marker '* '",
                    category = "Punctuation",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "INSERT_COMMA",
                    triggerPhrase = "comma",
                    displayName = "Insert Comma",
                    description = "Inserts ', '",
                    category = "Punctuation",
                    isEnabled = false
                ),
                VoiceCommandEntity(
                    commandType = "INSERT_PERIOD",
                    triggerPhrase = "period",
                    displayName = "Insert Period",
                    description = "Inserts '. '",
                    category = "Punctuation",
                    isEnabled = false
                )
            )
            dao.insertAll(defaults)
            LogKeeperManager.log(LogTag.Storage, "Inserted ${defaults.size} default voice commands (all OFF by default)")
        }
    }
}
