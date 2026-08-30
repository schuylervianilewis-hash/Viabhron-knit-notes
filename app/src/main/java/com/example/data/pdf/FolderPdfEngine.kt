package com.example.data.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.db.NoteEntity
import com.example.data.logkeeper.LogKeeperManager
import com.example.data.logkeeper.LogTag
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FolderPdfEngine {

    private const val PAGE_WIDTH = 595 // Standard A4 width in PostScript points (72 dpi)
    private const val PAGE_HEIGHT = 842 // Standard A4 height
    private const val MARGIN = 50f
    private const val LINE_SPACING = 20f

    /**
     * Exports a collection of notes in a folder into a beautifully styled multi-page PDF book/manuscript.
     * Also embeds structured metadata so the PDF can be imported back into the app.
     */
    fun exportFolderToPdf(
        context: Context,
        folderTitle: String,
        notesInOrder: List<NoteEntity>
    ): File? {
        val document = PdfDocument()
        try {
            var pageNumber = 1

            val titlePaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val chapterHeaderPaint = Paint().apply {
                color = Color.rgb(0, 137, 123)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }

            val rulePaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1.5f
            }

            // 1. PAGE 1: TITLE & COVER / TOC PAGE
            val coverPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val coverPage = document.startPage(coverPageInfo)
            val coverCanvas = coverPage.canvas

            var yPos = MARGIN + 60f
            coverCanvas.drawText(folderTitle, MARGIN, yPos, titlePaint)
            yPos += 24f
            val exportDate = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date())
            coverCanvas.drawText("Compiled Manuscript • $exportDate", MARGIN, yPos, subtitlePaint)
            yPos += 16f
            coverCanvas.drawText("Total Chapters / Notes: ${notesInOrder.size}", MARGIN, yPos, subtitlePaint)
            yPos += 30f
            coverCanvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, rulePaint)
            yPos += 40f

            // Table of Contents Section
            val tocHeaderPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            coverCanvas.drawText("TABLE OF CONTENTS", MARGIN, yPos, tocHeaderPaint)
            yPos += 25f

            for ((index, note) in notesInOrder.withIndex()) {
                val tocLine = "${index + 1}.  ${note.title.ifBlank { "Untitled Note" }}"
                coverCanvas.drawText(tocLine, MARGIN + 10f, yPos, bodyPaint)
                yPos += 20f
                if (yPos > PAGE_HEIGHT - MARGIN - 40f) break
            }

            // Footer
            coverCanvas.drawText(
                "Exported by ColorNote • Page $pageNumber",
                MARGIN,
                PAGE_HEIGHT - MARGIN,
                footerPaint
            )
            document.finishPage(coverPage)
            pageNumber++

            // 2. CHAPTER PAGES (Each note starts on its own section/page)
            for ((index, note) in notesInOrder.withIndex()) {
                var currentNotePageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                var currentNotePage = document.startPage(currentNotePageInfo)
                var canvas = currentNotePage.canvas

                yPos = MARGIN + 30f

                // Chapter Heading
                val chapterNumber = "CHAPTER ${index + 1}"
                canvas.drawText(chapterNumber, MARGIN, yPos, subtitlePaint)
                yPos += 22f

                val noteTitle = note.title.ifBlank { "Untitled Chapter" }
                canvas.drawText(noteTitle, MARGIN, yPos, chapterHeaderPaint)
                yPos += 16f

                val noteDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(note.updatedAt))
                canvas.drawText("Last modified: $noteDate", MARGIN, yPos, subtitlePaint)
                yPos += 20f
                canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, rulePaint)
                yPos += 28f

                // Body text lines with wrapping
                val contentLines = note.content.split("\n")
                for (paragraph in contentLines) {
                    val words = paragraph.split(" ")
                    var currentLine = ""

                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        val textWidth = bodyPaint.measureText(testLine)
                        if (textWidth > (PAGE_WIDTH - (MARGIN * 2))) {
                            // Check page break
                            if (yPos > PAGE_HEIGHT - MARGIN - 30f) {
                                canvas.drawText(
                                    "$folderTitle • Page $pageNumber",
                                    MARGIN,
                                    PAGE_HEIGHT - MARGIN,
                                    footerPaint
                                )
                                document.finishPage(currentNotePage)
                                pageNumber++
                                currentNotePageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                                currentNotePage = document.startPage(currentNotePageInfo)
                                canvas = currentNotePage.canvas
                                yPos = MARGIN + 30f
                            }
                            canvas.drawText(currentLine, MARGIN, yPos, bodyPaint)
                            yPos += LINE_SPACING
                            currentLine = word
                        } else {
                            currentLine = testLine
                        }
                    }

                    if (currentLine.isNotEmpty()) {
                        if (yPos > PAGE_HEIGHT - MARGIN - 30f) {
                            canvas.drawText(
                                "$folderTitle • Page $pageNumber",
                                MARGIN,
                                PAGE_HEIGHT - MARGIN,
                                footerPaint
                            )
                            document.finishPage(currentNotePage)
                            pageNumber++
                            currentNotePageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                            currentNotePage = document.startPage(currentNotePageInfo)
                            canvas = currentNotePage.canvas
                            yPos = MARGIN + 30f
                        }
                        canvas.drawText(currentLine, MARGIN, yPos, bodyPaint)
                        yPos += LINE_SPACING
                    }
                    yPos += 8f // Extra spacing between paragraphs
                }

                // Finish chapter page
                canvas.drawText(
                    "$folderTitle • Page $pageNumber",
                    MARGIN,
                    PAGE_HEIGHT - MARGIN,
                    footerPaint
                )
                document.finishPage(currentNotePage)
                pageNumber++
            }

            // Output PDF file to app cache/files
            val safeFileName = folderTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val exportDir = File(context.cacheDir, "pdf_exports").apply { mkdirs() }
            val outputFile = File(exportDir, "${safeFileName}_manuscript.pdf")

            // Write PDF bytes
            val fileOutputStream = FileOutputStream(outputFile)
            document.writeTo(fileOutputStream)

            // Embed JSON metadata payload into trailing stream comment for seamless round-trip import
            val metadataJson = buildMetadataPayload(folderTitle, notesInOrder)
            val metadataComment = "\n%---COLORNOTE_METADATA_START---\n%$metadataJson\n%---COLORNOTE_METADATA_END---\n"
            fileOutputStream.write(metadataComment.toByteArray(Charsets.UTF_8))
            fileOutputStream.flush()
            fileOutputStream.close()

            LogKeeperManager.log(LogTag.Storage, "PDF exported successfully: ${outputFile.name} (${outputFile.length()} bytes)")
            return outputFile
        } catch (e: Exception) {
            LogKeeperManager.log(LogTag.Storage, "PDF export failed: ${e.message}")
            return null
        } finally {
            document.close()
        }
    }

    /**
     * Shares the generated PDF file using standard Android Intent.
     */
    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Manuscript PDF"))
    }

    /**
     * Builds structured JSON payload representing the folder and chapter notes.
     */
    private fun buildMetadataPayload(folderTitle: String, notes: List<NoteEntity>): String {
        val root = JSONObject()
        root.put("app", "ColorNote")
        root.put("version", 1)
        root.put("folderTitle", folderTitle)
        val array = JSONArray()
        for ((index, note) in notes.withIndex()) {
            val item = JSONObject()
            item.put("title", note.title)
            item.put("content", note.content)
            item.put("colorTheme", note.colorTheme)
            item.put("orderIndex", index)
            item.put("isPinned", note.isPinned)
            item.put("isChecklist", note.isChecklist)
            array.put(item)
        }
        root.put("chapters", array)
        return root.toString()
    }

    /**
     * Parses an imported PDF file. If it was generated by ColorNote, extracts all original chapters.
     * Returns the folder title and list of imported NoteEntity items ready to insert into Room.
     */
    fun importNotesFromPdfFile(fileBytes: ByteArray): Pair<String, List<NoteEntity>>? {
        val content = String(fileBytes, Charsets.UTF_8)
        val startTag = "%---COLORNOTE_METADATA_START---\n%"
        val endTag = "\n%---COLORNOTE_METADATA_END---"

        val startIndex = content.indexOf(startTag)
        val endIndex = content.indexOf(endTag)

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            val jsonString = content.substring(startIndex + startTag.length, endIndex).trim()
            try {
                val root = JSONObject(jsonString)
                val folderTitle = root.optString("folderTitle", "Imported PDF Book")
                val chaptersArray = root.optJSONArray("chapters") ?: JSONArray()

                val importedNotes = mutableListOf<NoteEntity>()
                for (i in 0 until chaptersArray.length()) {
                    val obj = chaptersArray.getJSONObject(i)
                    importedNotes.add(
                        NoteEntity(
                            title = obj.optString("title", "Chapter ${i + 1}"),
                            content = obj.optString("content", ""),
                            colorTheme = obj.optString("colorTheme", "YELLOW"),
                            isPinned = obj.optBoolean("isPinned", false),
                            isChecklist = obj.optBoolean("isChecklist", false),
                            folderName = folderTitle,
                            orderIndex = obj.optInt("orderIndex", i),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                LogKeeperManager.log(LogTag.Storage, "Parsed ColorNote PDF metadata: '$folderTitle' with ${importedNotes.size} chapters")
                return Pair(folderTitle, importedNotes)
            } catch (e: Exception) {
                LogKeeperManager.log(LogTag.Storage, "Failed to parse PDF metadata: ${e.message}")
            }
        }

        // Fallback for generic text PDF
        LogKeeperManager.log(LogTag.Storage, "Standard PDF loaded without custom app metadata.")
        return null
    }
}
