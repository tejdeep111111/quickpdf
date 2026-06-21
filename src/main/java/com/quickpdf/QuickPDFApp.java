package com.quickpdf;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class QuickPDFApp extends Application {

    private static final Logger LOG = Logger.getLogger(QuickPDFApp.class.getName());

    private GlobalHotkeyListener hotkeyListener;

    @Override
    public void start(Stage primaryStage) {
        // Keep the app alive in the background when popup is closed
        Platform.setImplicitExit(false);

        // PopupController must be created on the JavaFX thread — we are here ✅
        PopupController popup = new PopupController();

        // Wire hotkey listener to popup
        hotkeyListener = new GlobalHotkeyListener(popup);

        // Sync [×] close button with hotkey toggle state
        popup.setOnHideCallback(() -> hotkeyListener.onPopupHidden());

        // Register Alt+Q global hotkey on a daemon thread
        Thread hotkeyThread = new Thread(hotkeyListener::register, "QuickPDF-HotkeySetup");
        hotkeyThread.setDaemon(true);
        hotkeyThread.start();

        // System tray icon — app lives here when popup is hidden
        setupSystemTray(popup);

        LOG.info("QuickPDF started — press Alt+Q to open.");
    }

    @Override
    public void stop() {
        // Called when Platform.exit() is invoked
        if (hotkeyListener != null) hotkeyListener.unregister();
        TempFileManager.getInstance().deleteAll();
        LOG.info("QuickPDF shut down cleanly.");
    }

    // ── System Tray ────────────────────────────────────────────

    private void setupSystemTray(PopupController popup) {
        if (!SystemTray.isSupported()) {
            LOG.warning("System tray not supported on this platform.");
            return;
        }

        // Simple coloured square as tray icon (replace with real icon asset later)
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x56, 0x9c, 0xd6));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);
        g.dispose();

        TrayIcon trayIcon = new TrayIcon(img, "QuickPDF — Alt+Q to open");
        trayIcon.setImageAutoSize(true);

        // Double-click tray icon → show popup
        trayIcon.addActionListener(e -> Platform.runLater(popup::show));

        // Right-click context menu
        PopupMenu menu = new PopupMenu();

        MenuItem openItem = new MenuItem("Open  (Alt+Q)");
        openItem.addActionListener(e -> Platform.runLater(popup::show));

        MenuItem quitItem = new MenuItem("Quit QuickPDF");
        quitItem.addActionListener(e -> Platform.exit());

        menu.add(openItem);
        menu.addSeparator();
        menu.add(quitItem);
        trayIcon.setPopupMenu(menu);

        try {
            SystemTray.getSystemTray().add(trayIcon);
            LOG.info("System tray icon added.");
        } catch (AWTException e) {
            LOG.warning("Could not add tray icon: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
