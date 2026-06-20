# QuickPDF

A lightweight always-on-top desktop utility for Windows that converts JPG images to PDF on the fly — without breaking your workflow.

---

## 🎯 Problem Statement

A user is filling out an online form that requires a PDF, but their file is a JPG.
Instead of opening a browser tool, uploading, waiting, and downloading — they press a shortcut key, drag the JPG into a floating popup, and drag the resulting PDF directly into the form.

---

## 🧑‍💻 User Flow

```
1. User presses global shortcut (Alt + Q)
         ↓
2. Floating always-on-top popup appears
         ↓
3. Popup shows conversion modes (MVP: JPG → PDF only)
         ↓
4. User drags their JPG onto the drop zone in the popup
         ↓
5. App converts the JPG to PDF in the background
         ↓
6. An animated PDF "card" appears hanging from the popup
         ↓
7. User drags the PDF card directly into any window (form, file explorer, etc.)
         ↓
8. Temp file is cleaned up after drag is complete or app closes
```

---

## 🏗️ Architecture

```
QuickPDFApp  (entry point, system tray, lifecycle)
    │
    ├── GlobalHotkeyListener     (listens for Alt+Q system-wide via JNativeHook)
    │
    ├── PopupController          (JavaFX always-on-top popup, drop zone, mode selector)
    │       │
    │       ├── DragDropHandler          (validates incoming drag, accepts only JPG)
    │       ├── ConversionService        (JPG → PDF via Apache PDFBox)
    │       ├── TempFileManager          (tracks and cleans up generated PDFs)
    │       └── ResultCardController     (animated PDF card, draggable out to other apps)
```

---

## 📦 Tech Stack

| Concern                        | Technology                          |
|-------------------------------|--------------------------------------|
| UI framework                  | JavaFX 21                           |
| PDF generation                | Apache PDFBox 3.0.2                 |
| Global hotkey (system-wide)   | JNativeHook 2.2.2                   |
| Build & packaging             | Maven + Maven Shade Plugin (fat JAR)|
| Testing                       | JUnit Jupiter 5.10.1                |
| Java version                  | Java 17 (module system enabled)     |

---

## 🗂️ Module Breakdown

### `TempFileManager`
- Singleton
- Tracks all generated temp PDF files
- Deletes them on JVM shutdown via a shutdown hook
- `deleteWithDelay(file)` — deletes after 500ms (gives time for drag-out to complete)
- `deleteAll()` — immediate cleanup

### `ConversionService`
- Validates input: must be non-null, existing, readable `.jpg`/`.jpeg` file
- Reads image via `javax.imageio.ImageIO`
- Creates a PDF page sized to the image dimensions (pixels → points at 96 DPI)
- Writes PDF to a uniquely named temp file
- Registers the output with `TempFileManager`

### `DragDropHandler`
- Attached to the JavaFX drop zone
- Accepts `DragEvent` from the OS
- Validates the dragged file via `ConversionService.supports()`
- Triggers conversion and hands off result to `ResultCardController`

### `PopupController`
- JavaFX `Stage` with `StageStyle.TRANSPARENT` and `setAlwaysOnTop(true)`
- Shows mode buttons at the top (MVP: "JPG → PDF" only, others greyed out)
- Contains the drag-and-drop zone (dashed border, icon, label)
- Minimises / hides on focus loss
- Exposed `show()` and `hide()` methods called by `GlobalHotkeyListener`

### `ResultCardController`
- Appears after successful conversion
- Animated entrance: card "drops" into view using `TranslateTransition` + `DropShadow`
- Displays file name and size
- Acts as a drag source: on drag, puts the PDF `File` into the system `Dragboard`
- After drag completes, calls `TempFileManager.deleteWithDelay()`

### `GlobalHotkeyListener`
- Uses JNativeHook to register a native keyboard hook
- Listens for `Alt + Q` globally (works even when app is not focused)
- Calls `PopupController.show()` / `hide()` on the JavaFX Application Thread via `Platform.runLater()`
- Unregisters hook cleanly on app shutdown

### `QuickPDFApp`
- JavaFX `Application` entry point
- Sets `Platform.setImplicitExit(false)` so the app keeps running after popup closes
- Initialises system tray icon (via `java.awt.SystemTray`) with a "Quit" option
- Starts `GlobalHotkeyListener` on a daemon thread
- Manages the single `PopupController` instance

---

## 🔨 Build Order

| Step | Class                        | Test Class                          |
|------|------------------------------|--------------------------------------|
| 1    | `TempFileManager`            | `TempFileManagerTest`               |
| 2    | `ConversionService`          | `ConversionServiceTest`             |
| 3    | `DragDropHandler`            | `DragDropHandlerTest`               |
| 4    | `PopupController`            | *(manual / integration test)*       |
| 5    | `ResultCardController`       | *(manual / integration test)*       |
| 6    | `GlobalHotkeyListener`       | `GlobalHotkeyListenerTest`          |
| 7    | `QuickPDFApp`                | *(manual / integration test)*       |

---

## 🚀 Running the App

```bash
mvn package
java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.graphics -jar target/quickpdf.jar
```

Or via the JavaFX Maven plugin:
```bash
mvn javafx:run
```

---

## 🗺️ MVP Scope

- [x] Project structure & documentation
- [ ] TempFileManager
- [ ] ConversionService (JPG → PDF)
- [ ] DragDropHandler
- [ ] PopupController (drop zone UI)
- [ ] ResultCardController (animated PDF card)
- [ ] GlobalHotkeyListener (Alt+Q)
- [ ] QuickPDFApp (system tray + wiring)
