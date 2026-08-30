# Application Architecture Blueprint: Offline Voice Notes (ColorNote Style)

---

## 1. System Overview & Core Directives

- **Primary Purpose:** Purely offline, lightweight, speech-to-text note-taking app with a classic ColorNote-style visual identity, where voice-to-text is the primary input method and text editing is for adjustments.
- **Target Platform:** Android 15 Go Edition (64-bit ARM / `arm64-v8a`, 3 GB RAM).
- **Target SDK:** Android API 24–35.
- **Network Footprint:** **Zero network dependencies.** Manifest strictly omits `android.permission.INTERNET` ensuring 100% air-gapped security and privacy.
- **System Insets & Edge-to-Edge:** Proper `WindowInsets.systemBars` / `WindowInsets.safeDrawing` padding applied across all screens so the Android status bar (top) and system gesture/button navigation bar (bottom) are never covered or overlapped by UI elements or FABs.

---

## 2. Audio Processing & Speech-to-Text (STT) Architecture

### A. Raw Audio Recording (No Android Voice Recognition Service / No Cloud)
- **Zero Android SpeechRecognizer:** Strictly **NO** Google Speech Recognition / Play Services / Cloud STT dependencies.
- **Audio Capture API:** Android Native `AudioRecord`.
- **Audio Format:** 16,000 Hz sample rate, 16-bit linear PCM, Mono channel.
- **Volatile In-Memory Ring Buffer:** Raw audio buffers are processed entirely in memory as normalized Float32 arrays (`[-1.0f, 1.0f]`) while recording.
- **Zero Audio Storage:** **No audio files (`.wav`/`.mp3`/`.pcm`/`.m4a`) are saved to disk or database.** Only recognized text is persisted. Once transcribed, temporary audio buffers are wiped immediately.
- **Lightweight Audio Chunking:** Real-time chunk slicing (3–5s conversational chunks or silence/VAD-triggered) to minimize memory pressure and optimize on-device inference latency.

### B. Fast On-Demand STT Engine (Whisper Offline Inference)
- **Inference Runtime:** Native `whisper.cpp` / ONNX engine utilizing ARM NEON SIMD optimizations.
- **On-Demand Fast Loading:** The model is not loaded at cold app start (keeping startup fast and base memory under 50 MB). When voice recording is triggered, model weights load into memory (~200–400ms via `mmap`) and stay warm for active dictation.
- **Primary Voice-First Flow:** Voice input feeds directly into model inference, outputting text streams directly onto the active note's ruled notebook lines.

### C. Local Model Import System (Zero APK Bloat)
- **Zero Pre-Bundled Model in APK:** The app installs as a lightweight APK (< 15 MB) without heavy model weights pre-packaged.
- **Post-Install Import Method:** Android Storage Access Framework (`ActivityResultContracts.OpenDocument` / SAF file picker).
- **Supported File Formats:**
  - `.bin` (GGML format: `ggml-tiny.bin` ~39 MB, `ggml-base.en.bin` ~75 MB, `ggml-small.bin` ~240 MB)
  - `.gguf` (Quantized format: `tiny-q5_1.gguf`, `base-q8_0.gguf`, `distil-small-q5_1.gguf`)
- **Model Verification & Sandboxing:**
  - Validates file headers, format integrity, and quantization metadata.
  - Copies and sandboxes imported models into private app storage (`context.filesDir/models/`).
  - Allows selecting active model or importing multiple models (e.g. Tiny for speed, Base for accuracy).

---

## 3. UI & Interaction Design (ColorNote Aesthetic)

### A. System Inset Handling (Edge-to-Edge)
- All root composables and modal sheets utilize `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` or `Scaffold(contentWindowInsets = ...)` ensuring top status bar and bottom 3-button/gesture system navigation bars never overlap content, headers, or floating action buttons.

### B. Main Screen & Top Bar
- **Top App Bar:**
  - App title ("ColorNote").
  - **Search Button (`IconButton`):** Quick filter query across titles, body text, and tags.
  - **Multi-Select Toggle:** Enters batch selection mode.
  - **Sort & Grid Toggle:** "Sort by color ▼", "Sort by date modified", "Sort by title", "Sort by folder".
  - **Overflow Menu (`⋮`):** Access to *Log Keeper*, *Import Whisper Model*, *Backup & Restore*, and *Settings*.
- **Bottom Navigation / Tabs:**
  1. **Notes List:** Active notes.
  2. **Calendar View:** Date-associated notes with monthly dot indicators.
  3. **Archive Tab:** Dedicated archived notes view (with Unarchive / Permanent Delete).
  4. **Folders View:** Color category folders (Yellow, Peach, Pink, Green, Blue, Purple) with live note counts.
  - Floating Action Buttons for quick creation of notes and instant LogKeeper console access.

### C. Color Note Cards
- **Card Styling:** Full-width cards with pastel background colors:
  - 🟨 **Yellow** (`#FFF9C4`)
  - 🟧 **Peach / Orange** (`#FFE0B2`)
  - 🟥 **Pink / Coral** (`#FFCDD2`)
  - 🟩 **Mint Green** (`#C8E6C9`)
  - 🟦 **Sky Blue** (`#BBDEFB`)
  - 🟪 **Lavender** (`#E1BEE7`)
- **Left colored vertical stripe**, bold title, content preview, pinned state, timestamp (e.g. `3:11 pm`, `14 Aug`), and quick actions menu (Archive, Change Color, Delete).

### D. Full-Screen Lined Notebook Note Viewer & Editor
- **Full-Screen Notebook Canvas:** Ruled horizontal notebook paper lines matching font baseline line-height so text sits cleanly on top of lines (no strikethrough).
- **Default View Mode:** Opening a note card opens in read-only View Mode (preventing accidental keyboard pop-up or unintended edits).
- **Double-Tap Anywhere to Edit:**
  - Double-tapping anywhere on the text canvas calculates the exact character offset via `TextLayoutResult.getOffsetForPosition(offset)`.
  - Transitions to Edit Mode, places caret at exact spot, and raises system keyboard.
- **Voice-First Input Overlay:**
  - Mic button with live audio waveform.
  - Appends recognized speech directly at the active cursor position on notebook lines.

---

## 4. Diagnostic LogKeeper Subsystem (Global Access)

- **Access:** Accessible via **global Floating Action Button overlay across all scenes** and through the top-bar 3-dots (`⋮`) menu.
- **UI Specification (Matching LogKeeper reference):**
  - **Top Bar:** Back button (`←`), bold title **Log Keeper**, master logging toggle switch, **Copy All** button, and **Download/Export Logs** button.
  - **Time Filter Tabs:** **`6h`** | **`12h`** | **`24h`** | **`All`**
  - **Card List:** Timestamped entries (`16:53:26.361`), category badge (`VoiceEngine`, `Navigation`, `UI/Editor`, `PdfExport`, `Storage`, `System`), and descriptive log message.

---

## 5. Folder Organization & Multi-Chapter Document Export

### A. Folder Hierarchy
- Notes grouped under color categories and custom folders.

### B. Multi-Chapter Export (PDF & Plain Text)
When exporting a folder or multi-selected notes:
1. **PDF Export (Native `android.graphics.pdf.PdfDocument`):**
   - Cover header with folder name, date, and chapter count.
   - Each note treated as a separate **Chapter** with chapter title header, creation timestamp, color metadata, and automatic page breaks.
2. **Plain Text Export (`.txt` / `.md`):**
   - Clean UTF-8 structured plain text with chapter boundaries.

---

## 6. Persistence, Security & Offline Backup/Restore

- **Encrypted Local Database:** Android Room Database (SQLite) storing purely text notes, folders, and model configurations (zero audio binary blobs).
- **Survives App Kill/Reopen:** Reactive StateFlow with automatic flush on change and lifecycle pause.
- **Offline Backup & Restore:**
  - Backup creation exports an encrypted/raw `.vnbak` / `.json` bundle to internal or SD card storage via SAF.
  - Restore allows selecting existing backup bundles with Merge or Replace options.

---

## 7. Technical Stack Summary

| Component | Technology |
| :--- | :--- |
| **Language & Framework** | Kotlin, Jetpack Compose, Material 3 |
| **Window Insets** | `WindowInsets.systemBars` / `safeDrawing` (No status/nav bar overlap) |
| **Audio Input** | `android.media.AudioRecord` (16kHz 16-bit PCM Mono, in-memory stream only, NO Android SpeechRecognizer) |
| **STT Engine** | `whisper.cpp` / ONNX on-demand with `mmap` (Offline Inference) |
| **Model Import** | Storage Access Framework SAF (`.bin`, `.gguf`) — Post-install import (Zero APK bloat) |
| **Editor / Canvas** | Lined notebook paper, View Mode default, Double-Tap to Edit with caret placement |
| **Diagnostics** | Global LogKeeper console (Time filters `6h`/`12h`/`24h`/`All`, Export, Copy) |
| **Persistence & Backup** | Room Database (SQLite) for pure text + SAF Backup/Restore |
| **Export Engine** | Native `android.graphics.pdf.PdfDocument` & UTF-8 Text Streams |
| **Security** | 100% Offline (Zero `INTERNET` permission in AndroidManifest) |

---

## 8. Real-Device Testable Mini-Phases Roadmap

### ✅ Completed Phases:
- **Mini-Phase 1: App Shell, System Bar Inset Protections & LogKeeper UI** (Done)
- **Mini-Phase 2.1: Encrypted Local Database & Auto-Persistence** (Done)
- **Mini-Phase 2.2: Lined Notebook Paper Editor & Baseline Alignment** (Done)
- **Mini-Phase 2.3: Note Organization, Archiving & Screen Views (Calendar, Archive, Folders)** (Done)
- **Phase 3: Whisper Model Manager & Storage Architecture** (Done)
- **Phase 4: Raw Audio Capture & Stream Chunking Engine** (Done)
- **Phase 5: Offline Whisper Inference Engine & Real Neural Tensor Graph** (Done)
- **Phase 6: Voice-First Note Creation & Editor Integration** (Done)
- **Phase 7: FUTO Core Speech Stack (Silero Neural VAD + Circular Ring Buffer + Dual Backend)** (Done)
- **Phase 10: Shorthand Dictionary, Custom Voice Macros & Dynamic Sequence Aggregation** (Done)

---

### 🟢 Upcoming Roadmap:

### **Phase 3: Whisper Model Manager & Storage Architecture**
- **Mini-Phase 3.1: Model Management & SAF Import Engine**
  - Model Manager screen (accessible from 3-dots menu & quick setup prompt).
  - Storage Access Framework file picker for local `.bin` (GGML) and `.gguf` Whisper models.
  - File header & quantization verification (Tiny ~39MB, Base ~75MB, Small ~240MB).
  - Copy and sandbox model into app-private storage (`context.filesDir/models/`).
  - Active model selection and delete model management.
  - Full LogKeeper telemetry for model import, verification, and file checksums.

---

### **Phase 4: Raw Audio Capture & Stream Chunking Engine**
- **Mini-Phase 4.1: Raw PCM Audio Capture (`AudioRecord`)**
  - Native `AudioRecord` engine (16,000 Hz, 16-bit Mono, Little-Endian).
  - Raw Byte -> Normalized Float32 array conversion (`[-1.0f, 1.0f]`).
  - Pure in-memory buffer (zero audio written to disk).
  - Runtime microphone permission request with rationale dialog.
- **Mini-Phase 4.2: Lightweight Audio Chunking & Voice Activity Buffer**
  - Streaming circular/ring buffer in memory.
  - Chunk slicing logic (3–5s chunks or silence-triggered slicing) for lightweight memory footprint.
  - LogKeeper telemetry for audio sampling rate, buffer levels, and chunk timestamps.

---

### **Phase 5: Offline Whisper Inference Engine & Bridge**
- **Mini-Phase 5.1: Whisper Offline Inference Integration (COMPLETED)**
  - Native Whisper engine bridge (`whisper_jni.cpp`) with full `Q5_1`, `Q5_0`, `Q4_1`, `Q4_0`, `Q8_0`, `F16`, and `F32` dequantization support.
  - Direct ingestion of Float32 PCM audio chunks into model inference with dynamic gain normalization.
  - Fast Mel-spectrogram computation with precomputed trigonometric basis tables.
  - Output token stream -> vocabulary text decoder.
- **Mini-Phase 5.2: Inference Telemetry & Benchmarking in LogKeeper (COMPLETED)**
  - Latency measurement (Real-Time Factor / processing ms per audio chunk).
  - Memory consumption and token throughput logs in LogKeeper.

---

### **Phase 6: Voice-First Note Creation & Editor Integration**
- **Mini-Phase 6.1: Voice-First Floating & Note Input Interaction**
  - One-tap voice note creation from Main Screen (opens note and starts live PCM transcription immediately).
  - In-Editor dictation button streaming live transcribed text directly onto the notebook ruled lines.
  - Visual listening / recording state indicator (subtle wave pulse).
- **Mini-Phase 6.2: Auto-Formatting & Refinement**
  - Automatic capitalization and sentence spacing on dictated chunks.
  - Manual touch-up with keyboard on notebook canvas.
  - Complete LogKeeper verification for end-to-end voice-to-text journeys.

---

### **Phase 7: Multi-Select Mode & Batch Operations**
- **Mini-Phase 7.1: Batch Selection & Action Toolbar**
  - Multi-select toggle button in top bar and card long-press triggers.
  - Batch action toolbar: Batch Archive/Unarchive, Batch Color Change, Batch Move to Folder, Batch Delete.

---

### **Phase 8: Folder Drawer & Multi-Chapter PDF / Text Export**
- **Mini-Phase 8.1: Multi-Chapter PDF & Plain Text Export**
  - Native `PdfDocument` export with cover header, numbered chapters for each note, timestamps, and page breaks.
  - Formatted `.txt` / `.md` export with chapter boundaries.

---

### **Phase 10: Shorthand Dictionary, Custom Voice Macros & Dynamic Sequence Aggregation**
- **Mini-Phase 10.1: Custom Word Replacements & Voice Macro Engine (COMPLETED)**
  - Full CRUD management for custom shorthand replacement rules and spoken voice commands.
  - Multi-select, bulk enable/disable, category filtering, search filtering, and default knitting preset restoration.
  - All default rules and commands seeded in a disabled (`isEnabled = false`) state (OFF by default).
- **Mini-Phase 10.2: Dynamic Sequence Counting & Aggregation Engine (COMPLETED)**
  - Monotonic sequence detection for ascending spoken counts (e.g., "knit 1 knit 2 ... knit 5" ➔ "k5", "make 1 make 2" ➔ "m2").
  - Support for Arabic numerals (`1, 2, 3..`) and spoken english words (`one, two, three..`) with punctuation separation handling.
  - Category-level master toggle switch in Word Replacements screen ("Sequence" category) to enable/disable counting rules on demand.

