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

        BufferedImage img = buildPdfIconAwt();

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

    private void setTaskbarIcon() {
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(buildPdfIconAwt());
                    LOG.info("Taskbar icon set.");
                }
            }
        } catch (Exception e) {
            LOG.warning("Could not set taskbar icon: " + e.getMessage());
        }
    }

    // AWT PDF icon for system tray
    private BufferedImage buildPdfIconAwt() {
        int S = 64;
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int fold = 14;
        int[] xPage = {2, S - fold - 2, S - 2, S - 2, 2};
        int[] yPage = {2, 2,            fold + 2, S - 2, S - 2};
        g.setColor(Color.WHITE);
        g.fillPolygon(xPage, yPage, 5);

        g.setColor(new Color(180, 180, 180));
        g.setStroke(new BasicStroke(1.5f));
        g.drawPolygon(xPage, yPage, 5);

        g.setColor(new Color(160, 160, 160));
        g.fillPolygon(
            new int[]{S - fold - 2, S - 2, S - fold - 2},
            new int[]{2,            fold + 2, fold + 2},
            3
        );

        g.setColor(new Color(210, 30, 30));
        g.setFont(new Font("Arial", Font.BOLD, 18));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("PDF",
            (S - fm.stringWidth("PDF")) / 2,
            S / 2 + fm.getAscent() / 2
        );
        g.dispose();
        return img;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
