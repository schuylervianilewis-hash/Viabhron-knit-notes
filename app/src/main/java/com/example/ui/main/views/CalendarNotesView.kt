package com.example.ui.main.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.NoteEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import com.example.data.model.NoteColor
import com.example.ui.main.SelectableColorNoteCardItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarNotesView(
    notes: List<NoteEntity>,
    onOpenNoteEditor: (noteId: Long?, initialColor: NoteColor) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onChangeColor: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var calendarMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDate by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayHeaderFormat = remember { SimpleDateFormat("EE", Locale.getDefault()) }
    val selectedDateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }

    // Map days in current month to their note counts and colors
    val notesByDay = remember(notes, calendarMonth) {
        val map = mutableMapOf<Int, MutableList<NoteEntity>>()
        val cal = Calendar.getInstance()
        val currentYear = calendarMonth.get(Calendar.YEAR)
        val currentMonth = calendarMonth.get(Calendar.MONTH)

        notes.forEach { note ->
            cal.timeInMillis = note.updatedAt
            if (cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth) {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                map.getOrPut(day) { mutableListOf() }.add(note)
            }
        }
        map
    }

    // Filter notes belonging to the selected date
    val notesOnSelectedDate = remember(notes, selectedDate) {
        val targetCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        val noteCal = Calendar.getInstance()
        notes.filter { note ->
            noteCal.timeInMillis = note.updatedAt
            noteCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                    noteCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month Navigation Header
        item(key = "calendar_header") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val nextCal = (calendarMonth.clone() as Calendar).apply {
                                    add(Calendar.MONTH, -1)
                                }
                                calendarMonth = nextCal
                                LogKeeperManager.log(
                                    LogTag.Navigation,
                                    "Calendar: Previous month (${monthFormat.format(nextCal.time)})"
                                )
                            },
                            modifier = Modifier.testTag("cal_prev_month")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                calendarMonth = Calendar.getInstance()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = monthFormat.format(calendarMonth.time),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextCal = (calendarMonth.clone() as Calendar).apply {
                                    add(Calendar.MONTH, 1)
                                }
                                calendarMonth = nextCal
                                LogKeeperManager.log(
                                    LogTag.Navigation,
                                    "Calendar: Next month (${monthFormat.format(nextCal.time)})"
                                )
                            },
                            modifier = Modifier.testTag("cal_next_month")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Day of week headers (Sun, Mon, Tue, Wed, Thu, Fri, Sat)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        daysOfWeek.forEach { dayName ->
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Monthly Days Grid
                    val firstDayOfWeek = (calendarMonth.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                    }.get(Calendar.DAY_OF_WEEK) - 1 // 0-based for Sun

                    val daysInMonth = calendarMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

                    val todayCal = Calendar.getInstance()
                    val isCurrentMonthYear = todayCal.get(Calendar.YEAR) == calendarMonth.get(Calendar.YEAR) &&
                            todayCal.get(Calendar.MONTH) == calendarMonth.get(Calendar.MONTH)
                    val todayDayNumber = todayCal.get(Calendar.DAY_OF_MONTH)

                    val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    val isSelectedMonthYear = selectedCal.get(Calendar.YEAR) == calendarMonth.get(Calendar.YEAR) &&
                            selectedCal.get(Calendar.MONTH) == calendarMonth.get(Calendar.MONTH)
                    val selectedDayNumber = selectedCal.get(Calendar.DAY_OF_MONTH)

                    for (row in 0 until totalCells / 7) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0..6) {
                                val cellIndex = row * 7 + col
                                val dayNumber = cellIndex - firstDayOfWeek + 1

                                if (dayNumber in 1..daysInMonth) {
                                    val isToday = isCurrentMonthYear && (dayNumber == todayDayNumber)
                                    val isSelected = isSelectedMonthYear && (dayNumber == selectedDayNumber)
                                    val dayNotes = notesByDay[dayNumber] ?: emptyList()

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .background(
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    isToday -> Color(0xFFE0F2FE)
                                                    else -> Color.Transparent
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        1.5.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                } else Modifier
                                            )
                                            .clickable {
                                                val newDate = (calendarMonth.clone() as Calendar).apply {
                                                    set(Calendar.DAY_OF_MONTH, dayNumber)
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }.timeInMillis
                                                selectedDate = newDate
                                                LogKeeperManager.log(
                                                    LogTag.UI_Editor,
                                                    "Selected date: $dayNumber ${monthFormat.format(calendarMonth.time)} (${dayNotes.size} notes)"
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayNumber",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        isToday -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    },
                                                    fontSize = 13.sp
                                                )
                                            )

                                            // Note indicator dots
                                            if (dayNotes.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    dayNotes.take(3).forEach { note ->
                                                        val color = NoteColor.fromName(note.colorTheme)
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .background(color.stripeColor, CircleShape)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Selected Date
        item(key = "selected_date_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedDateFormat.format(Date(selectedDate)),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${notesOnSelectedDate.size} notes",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Notes List for Selected Date
        if (notesOnSelectedDate.isEmpty()) {
            item(key = "empty_date_notes") {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No notes on this date",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        } else {
            items(
                items = notesOnSelectedDate,
                key = { "cal_${it.id}" }
            ) { note ->
                val noteColor = remember(note.colorTheme) { NoteColor.fromName(note.colorTheme) }
                val timeStr = remember(note.updatedAt) {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(note.updatedAt))
                }

                SelectableColorNoteCardItem(
                    note = note,
                    noteColor = noteColor,
                    formattedTime = timeStr,
                    isSelected = false,
                    isSelectionMode = false,
                    onClick = { onOpenNoteEditor(note.id, noteColor) },
                    onLongClick = { onOpenNoteEditor(note.id, noteColor) },
                    onTogglePin = { onTogglePin(note) },
                    onDelete = { onDeleteNote(note) },
                    onChangeColor = { onChangeColor(note) }
                )
            }
        }
    }
}
