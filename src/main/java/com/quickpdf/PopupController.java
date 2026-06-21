package com.quickpdf;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class PopupController {

    private static final Logger LOG = Logger.getLogger(PopupController.class.getName());

    // ── Colours ────────────────────────────────────────────────
    private static final String BG          = "#1e1e1e";
    private static final String BG_HOVER    = "#2d2d2d";
    private static final String BORDER      = "#3a3a3a";
    private static final String TEXT        = "#d4d4d4";
    private static final String PROMPT      = "#569cd6";   // blue $
    private static final String SUCCESS     = "#00ff88";   // green
    private static final String ERROR_COL   = "#f44747";   // red

    // ── State ──────────────────────────────────────────────────
    private Stage stage;
    private Label statusLabel;
    private Label cursorLabel;
    private Timeline cursorBlink;
    private StackPane dropZone;

    private final ConversionService conversionService;
    private final DragDropHandler   dragDropHandler;

    // Used to drag the transparent window around
    private double dragOffsetX, dragOffsetY;

    // Fired when popup is hidden via [×] — lets GlobalHotkeyListener sync its toggle state
    private Runnable onHideCallback;

    // ── Constructor ────────────────────────────────────────────

    public PopupController() {
        this.conversionService = new ConversionService();
        this.dragDropHandler   = new DragDropHandler(conversionService, this::showPdfCard);
        buildUI();
    }

    // ── Public API ─────────────────────────────────────────────

    // Called by GlobalHotkeyListener via Platform.runLater()
    public void show() {
        Platform.runLater(() -> {
            resetToIdle();
            stage.show();
            stage.toFront();
        });
    }

    public void hide() {
        Platform.runLater(() -> {
            stage.hide();
            if (onHideCallback != null) onHideCallback.run();
        });
    }

    // Called by QuickPDFApp to sync [×] button with hotkey toggle state
    public void setOnHideCallback(Runnable callback) {
        this.onHideCallback = callback;
    }

    // ── UI Construction ────────────────────────────────────────

    private void buildUI() {
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setWidth(340);
        stage.setHeight(260);

        VBox root = new VBox(0);
        root.setStyle(
            "-fx-background-color: " + BG + ";" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;"
        );

        root.getChildren().addAll(
            buildTitleBar(),
            buildDropZone(),
            buildStatusBar()
        );

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.setScene(scene);
    }

    // ── Title bar ──────────────────────────────────────────────
    // Custom title bar — lets user drag the window, has close button

    private HBox buildTitleBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle(
            "-fx-background-color: #252526;" +
            "-fx-background-radius: 6 6 0 0;"
        );

        Label prompt = new Label("> QuickPDF");
        prompt.setStyle("-fx-text-fill: " + PROMPT + "; -fx-font-family: 'Courier New'; -fx-font-size: 13;");

        // Spacer pushes close button to the right
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label closeBtn = new Label("[×]");
        closeBtn.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-family: 'Courier New'; -fx-font-size: 13; -fx-cursor: hand;");
        closeBtn.setOnMouseClicked(e -> hide());
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-text-fill: " + ERROR_COL + "; -fx-font-family: 'Courier New'; -fx-font-size: 13; -fx-cursor: hand;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-text-fill: " + TEXT + "; -fx-font-family: 'Courier New'; -fx-font-size: 13; -fx-cursor: hand;"));

        bar.getChildren().addAll(prompt, spacer, closeBtn);

        // Make the title bar drag the whole window
        bar.setOnMousePressed(e -> { dragOffsetX = e.getSceneX(); dragOffsetY = e.getSceneY(); });
        bar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        return bar;
    }

    // ── Drop zone ──────────────────────────────────────────────

    private StackPane buildDropZone() {
        dropZone = new StackPane();
        dropZone.setPrefHeight(160);
        dropZone.setStyle(idleDropZoneStyle());
        VBox.setVgrow(dropZone, Priority.ALWAYS);

        Label hint = new Label("drop .jpg here");
        hint.setStyle("-fx-text-fill: #555555; -fx-font-family: 'Courier New'; -fx-font-size: 13;");
        dropZone.getChildren().add(hint);

        setupDropEvents();
        return dropZone;
    }

    private void setupDropEvents() {
        // File dragged OVER the zone
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                File file = event.getDragboard().getFiles().get(0);
                if (dragDropHandler.canHandle(file.toPath())) {
                    // Accepted — highlight green
                    dropZone.setStyle(acceptDropZoneStyle());
                    event.acceptTransferModes(TransferMode.COPY);
                } else {
                    // Rejected — highlight red
                    dropZone.setStyle(rejectDropZoneStyle());
                }
            }
            event.consume();
        });

        // Drag left the zone without dropping
        dropZone.setOnDragExited(event -> dropZone.setStyle(idleDropZoneStyle()));

        // File DROPPED onto the zone
        dropZone.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (!files.isEmpty()) {
                File file = files.get(0);
                if (dragDropHandler.canHandle(file.toPath())) {
                    setStatus("reading " + file.getName() + "...", TEXT);
                    // Run conversion off the JavaFX thread so UI doesn't freeze
                    new Thread(() -> dragDropHandler.handle(file), "QuickPDF-Converter").start();
                } else {
                    setStatus("error: only .jpg files supported", ERROR_COL);
                }
            }
            dropZone.setStyle(idleDropZoneStyle());
            event.setDropCompleted(true);
            event.consume();
        });
    }

    // ── Status bar ─────────────────────────────────────────────

    private HBox buildStatusBar() {
        HBox bar = new HBox(4);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #252526; -fx-background-radius: 0 0 6 6;");

        Label dollarSign = new Label("$");
        dollarSign.setStyle("-fx-text-fill: " + PROMPT + "; -fx-font-family: 'Courier New'; -fx-font-size: 12;");

        statusLabel = new Label("waiting for input...");
        statusLabel.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-family: 'Courier New'; -fx-font-size: 12;");

        // Blinking cursor
        cursorLabel = new Label("_");
        cursorLabel.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-family: 'Courier New'; -fx-font-size: 12;");
        cursorBlink = new Timeline(
            new KeyFrame(Duration.millis(500), e -> cursorLabel.setVisible(!cursorLabel.isVisible()))
        );
        cursorBlink.setCycleCount(Timeline.INDEFINITE);
        cursorBlink.play();

        bar.getChildren().addAll(dollarSign, statusLabel, cursorLabel);
        return bar;
    }

    // ── After conversion ───────────────────────────────────────
    // Called by DragDropHandler via onSuccess callback

    private void showPdfCard(File pdfFile) {
        Platform.runLater(() -> {
            setStatus("done ✓  " + pdfFile.getName(), SUCCESS);
            ResultCardController card = new ResultCardController(pdfFile, this::resetToIdle);
            dropZone.getChildren().setAll(card.getNode());
            card.animateIn();
        });
    }

    // ── Helpers ────────────────────────────────────────────────

    private void setStatus(String message, String colour) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: " + colour + "; -fx-font-family: 'Courier New'; -fx-font-size: 12;");
        });
    }

    private void resetToIdle() {
        setStatus("waiting for input...", TEXT);
        // Restore the drop zone hint label
        Label hint = new Label("drop .jpg here");
        hint.setStyle("-fx-text-fill: #555555; -fx-font-family: 'Courier New'; -fx-font-size: 13;");
        dropZone.getChildren().setAll(hint);
        dropZone.setStyle(idleDropZoneStyle());
    }

    private String idleDropZoneStyle() {
        return "-fx-background-color: " + BG + ";" +
               "-fx-border-color: #555555;" +
               "-fx-border-style: dashed;" +
               "-fx-border-width: 1;" +
               "-fx-border-insets: 12;" +
               "-fx-border-radius: 4;";
    }

    private String acceptDropZoneStyle() {
        return "-fx-background-color: #1a2e1a;" +
               "-fx-border-color: " + SUCCESS + ";" +
               "-fx-border-style: dashed;" +
               "-fx-border-width: 1;" +
               "-fx-border-insets: 12;" +
               "-fx-border-radius: 4;";
    }

    private String rejectDropZoneStyle() {
        return "-fx-background-color: #2e1a1a;" +
               "-fx-border-color: " + ERROR_COL + ";" +
               "-fx-border-style: dashed;" +
               "-fx-border-width: 1;" +
               "-fx-border-insets: 12;" +
               "-fx-border-radius: 4;";
    }
}