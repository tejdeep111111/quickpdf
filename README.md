# QuickPDF

A lightweight always-on-top desktop utility for Windows that converts JPG/PNG images to PDF on the fly — without breaking your workflow.

> Press **Alt+Q** → drop your image → drag the PDF directly into any form, browser, or file explorer. Done.

---

## 🎯 Problem Statement

You're filling out an online form that requires a PDF, but your file is a JPG or PNG.  
Instead of opening a browser converter, uploading, waiting, downloading — you press a shortcut key, drag the image into a floating popup, and drag the resulting PDF card directly into the form.

---

## 🧑‍💻 User Flow

```
1. App starts silently → lives in the system tray
         ↓
2. User presses Alt+Q (from anywhere — browser, file explorer, form)
         ↓
3. Floating always-on-top popup appears, centred on screen
         ↓
4. User drags one or more .jpg / .jpeg / .png files onto the drop zone
         ↓
5. Terminal-style animation plays:
     > reading photo.jpg...
     > validating format...
     > building PDF...
     > writing output...
     [==================] ← progress bar fills
         ↓
6. PDF icon animates in — shows filename (editable) + file size
         ↓
7. User drags the PDF card out to any window (form field, file explorer, email, etc.)
         ↓
8. Press Alt+Q again or click [×] to hide the popup
         ↓
9. Temp PDF is cleaned up automatically when reset or app exits
```

---

## 🏗️ Architecture

```
QuickPDFApp  (entry point, system tray, lifecycle)
    │
    ├── GlobalHotkeyListener     (listens for Alt+Q system-wide via JNativeHook)
    │
    ├── PopupController          (JavaFX always-on-top popup — drop zone + status bar)
    │       │
    │       ├── DragDropHandler          (validates incoming drag, accepts JPG/PNG, handles multi-file)
    │       ├── ConversionService        (image → PDF via Apache PDFBox, single + batch)
    │       ├── TempFileManager          (singleton — tracks and cleans up generated PDFs)
    │       └── ResultCardController     (PDF icon card — inline rename, drag-out to OS, reset)
```

---

## 📦 Tech Stack

| Concern                        | Technology                          |
|-------------------------------|--------------------------------------|
| UI framework                  | JavaFX 21                           |
| PDF generation                | Apache PDFBox 3.0.2                 |
| Global hotkey (system-wide)   | JNativeHook 2.2.2                   |
| Build & packaging             | Maven + Maven Shade Plugin (fat JAR)|
| Distribution                  | jpackage (self-contained `.exe`)    |
| Testing                       | JUnit Jupiter 5.10.1                |
| Java version                  | Java 25 (module system enabled)     |

---

## 🗂️ Module Breakdown

### `TempFileManager`
- Singleton — one instance for the entire app lifetime
- `track(file)` — registers a generated PDF for cleanup
- `deleteWithDelay(file)` — deletes after 500ms (lets OS finish reading after drag-out)
- `deleteAll()` — immediate cleanup (called on reset + JVM shutdown hook)

### `ConversionService`
- `supports(path)` — returns `true` for `.jpg`, `.jpeg`, `.png`
- `convert(file)` — single image → PDF, sized to image dimensions (96 DPI → points)
- `convertAll(files)` — multiple images → single merged PDF (one page per image)
- Output filename: `basename_YYYYMMDD_HHmmss.pdf` — human-readable, no UUID
- Registers output with `TempFileManager` automatically

### `DragDropHandler`
- `canHandle(path)` — delegates to `ConversionService.supports()`
- `handle(file)` — single file conversion
- `handleAll(files)` — batch conversion, filters unsupported files silently
- Fires `onSuccess` callback with the resulting PDF

### `PopupController`
- `StageStyle.TRANSPARENT` + `setAlwaysOnTop(true)` — frameless, always on top
- CLI-styled dark UI: black background, Consolas font, blinking cursor
- Drop zone highlights green (accepted) / red (rejected) on drag-over
- Terminal animation on conversion: 4 status lines + `[=====>]` progress bar
- `show()` centres popup on screen, `hide()` fires `onHideCallback` to sync hotkey toggle state

### `ResultCardController`
- Canvas-drawn PDF icon (white page, folded corner, red "PDF" label, 90% opacity)
- Inline filename rename — hover shows `✎`, click swaps to `TextField`, Enter confirms
- `↺` reset button (top-right) — dim gray, lightens on hover, resets popup to drop zone
- Drag-out: `startDragAndDrop(COPY)` puts `File` into system dragboard with PDF icon as cursor image
- File persists on disk until user clicks reset or app exits — drag as many times as needed

### `GlobalHotkeyListener`
- Registers `Alt+Q` via JNativeHook (works system-wide, even when app is not focused)
- Toggles popup show/hide
- `onPopupHidden()` — called when `[×]` is clicked, syncs toggle state
- Silences JNativeHook's verbose logging
- `unregister()` called cleanly on app shutdown

### `QuickPDFApp`
- JavaFX `Application` entry point
- `Platform.setImplicitExit(false)` — app stays alive when popup is closed
- System tray: AWT-drawn PDF icon, right-click menu with Open + Quit
- Wires `GlobalHotkeyListener` → `PopupController` → `DragDropHandler` → `ResultCardController`

---

## 🚀 Running

### Development (IntelliJ)
Hit the green **Run** button on `QuickPDFApp`.

### From terminal
```powershell
# Build
mvn clean package

# Run
QuickPDF.bat
```

### Build distributable `.exe` (no Java required for end user)
```powershell
build-dist.bat
```
Output: `dist\QuickPDF.zip` — send to friend, they unzip and double-click `QuickPDF.exe`.

---

## ✅ Features

- [x] Global hotkey `Alt+Q` — works from any app
- [x] Always-on-top floating popup — centred on screen
- [x] Drag `.jpg` / `.jpeg` / `.png` in from OS
- [x] Multi-file drop — merges into one PDF (one page per image)
- [x] Terminal-style conversion animation with progress bar
- [x] PDF result card with macOS-style drawn icon
- [x] Inline filename rename on hover
- [x] Drag PDF out to any window (browser form, file explorer, email)
- [x] PDF icon follows mouse cursor during drag
- [x] File persists until explicitly reset or app exits
- [x] System tray — app lives in background
- [x] Temp file cleanup on reset + JVM shutdown hook
- [x] Self-contained `.exe` via `jpackage` (no Java needed)

## 🔜 Possible Next Steps

- [ ] Compress PDF option (reduce DPI)
- [ ] Save to folder instead of temp
- [ ] Custom hotkey setting
- [ ] PDF → JPG reverse conversion
- [ ] Rewrite UI in Swing + FlatLaf (~100 MB RAM vs ~400 MB JavaFX)
- [ ] Rewrite in Rust for native performance and smaller binary size
