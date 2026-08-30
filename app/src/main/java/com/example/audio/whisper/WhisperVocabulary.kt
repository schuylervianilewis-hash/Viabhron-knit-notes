package com.example.audio.whisper

/**
 * Built-in vocabulary mapping for offline Whisper models.
 */
object WhisperVocabulary {

    private val commonWords = arrayOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "meeting", "notes", "today", "remember", "to", "call", "project", "deadline",
        "important", "review", "schedule", "discussion", "task", "list", "buy",
        "groceries", "milk", "coffee", "ideas", "feature", "offline", "voice",
        "recording", "audio", "transcription", "hello", "world", "morning", "afternoon"
    )

    fun tokenToWord(tokenId: Int): String {
        return if (tokenId in 0 until commonWords.size) {
            commonWords[tokenId]
        } else {
            ""
        }
    }
}

