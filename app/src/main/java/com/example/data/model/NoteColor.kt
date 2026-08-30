package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NoteBlue
import com.example.ui.theme.NoteBlueStripe
import com.example.ui.theme.NoteGreen
import com.example.ui.theme.NoteGreenStripe
import com.example.ui.theme.NotePeach
import com.example.ui.theme.NotePeachStripe
import com.example.ui.theme.NotePink
import com.example.ui.theme.NotePinkStripe
import com.example.ui.theme.NotePurple
import com.example.ui.theme.NotePurpleStripe
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.NoteYellowStripe

@Immutable
enum class NoteColor(
    val idName: String,
    val displayName: String,
    val bgColor: Color,
    val stripeColor: Color
) {
    YELLOW("YELLOW", "Yellow", NoteYellow, NoteYellowStripe),
    PEACH("PEACH", "Peach", NotePeach, NotePeachStripe),
    PINK("PINK", "Pink", NotePink, NotePinkStripe),
    GREEN("GREEN", "Green", NoteGreen, NoteGreenStripe),
    BLUE("BLUE", "Blue", NoteBlue, NoteBlueStripe),
    PURPLE("PURPLE", "Purple", NotePurple, NotePurpleStripe);

    companion object {
        fun fromName(name: String?): NoteColor {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: YELLOW
        }
    }
}
