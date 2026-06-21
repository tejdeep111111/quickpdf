package com.quickpdf;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class PopupController {

    private static final Logger LOG = Logger.getLogger(PopupController.class.getName());

    // ── Design tokens ──────────────────────────────────────────
    private static final String FONT      = "Consolas";
    private static final String BG        = "#000000";
    private static final String BG_PANEL  = "#0a0a0a";
    private static final String BG_HEADER = "#000000";
    private static final String BORDER    = "#1a1a1a";
    private static final String TEXT      = "#cccccc";
    private static final String TEXT_DIM  = "#444444";
    private static final String PROMPT    = "#569cd6";
    private static final String SUCCESS   = "#4ec994";
    private static final String ERROR_COL = "#e05c5c";
    private static final String ACCENT    = "#111111";

    // ── State ──────────────────────────────────────────────────
    private Stage     stage;
    private Label     statusLabel;
    private Label     cursorLabel;
    private StackPane dropZone;

    private final ConversionService conversionService;
    private final DragDropHandler   dragDropHandler;
    private       Runnable          onHideCallback;
    private double dragOffsetX, dragOffsetY;

    // ── Constructor ────────────────────────────────────────────

    public PopupController() {
        this.conversionService = new ConversionService();
        this.dragDropHandler   = new DragDropHandler(conversionService, this::showPdfCard);
        buildUI();
    }

    // ── Public API ─────────────────────────────────────────────

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

    public void setOnHideCallback(Runnable callback) {
        this.onHideCallback = callback;
    }

    // ── Terminal loading animation ──────────────────────────────
    // Shows CLI-style lines appearing one by one while conversion runs in background.
    // Waits for BOTH animation AND conversion to finish before showing the result card.

    private static final long MIN_ANIM_MS = 1900; // must be >= (steps * stepDelay)

    private void showLoadingAnimation(String label, List<File> files) {
        String[] steps = {
            "> reading  " + label + "...",
            "> validating format...",
            "> building PDF...",
            "> writing output..."
        };
        int stepDelay = 420;

        VBox terminal = new VBox(6);
        terminal.setAlignment(Pos.CENTER_LEFT);
        terminal.setPadding(new Insets(18, 28, 18, 28));

        Label[] lineLabels = new Label[steps.length];
        for (int i = 0; i < steps.length; i++) {
            lineLabels[i] = new Label(steps[i]);
            lineLabels[i].setStyle(
                "-fx-text-fill: " + PROMPT + ";" +
                "-fx-font-family: '" + FONT + "';" +
                "-fx-font-size: 12;"
            );
            lineLabels[i].setOpacity(0);
            terminal.getChildren().add(lineLabels[i]);
        }

        // ── Progress bar ──────────────────────────────────────
        // Animates: [>         ] → [===>      ] → [======>   ] → [=========]
        Label progressLabel = new Label();
        progressLabel.setStyle(
            "-fx-text-fill: " + SUCCESS + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 12;"
        );
        progressLabel.setOpacity(0);

        int BAR_WIDTH = 18; // number of chars inside brackets
        int[] frame = {0};  // mutable counter

        Timeline progressAnim = new Timeline(new KeyFrame(Duration.millis(80), e -> {
            frame[0]++;
            int filled = Math.min(frame[0], BAR_WIDTH);
            boolean atEnd = filled >= BAR_WIDTH;
            String bar = "=".repeat(atEnd ? BAR_WIDTH : Math.max(0, filled - 1))
                       + (atEnd ? "" : ">")
                       + " ".repeat(Math.max(0, BAR_WIDTH - filled));
            progressLabel.setText("[" + bar + "]");
        }));
        progressAnim.setCycleCount(Timeline.INDEFINITE);

        // Blinking cursor
        Label cursor = new Label("_");
        cursor.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-family: '" + FONT + "'; -fx-font-size: 12;");
        Timeline blink = new Timeline(new KeyFrame(Duration.millis(400),
                e -> cursor.setVisible(!cursor.isVisible())));
        blink.setCycleCount(Timeline.INDEFINITE);
        blink.play();

        terminal.getChildren().addAll(progressLabel, cursor);
        dropZone.getChildren().setAll(terminal);
        setStatus("converting...", TEXT);

        // Reveal lines one by one
        Timeline reveal = new Timeline();
        for (int i = 0; i < lineLabels.length; i++) {
            final Label lbl = lineLabels[i];
            reveal.getKeyFrames().add(new KeyFrame(Duration.millis((long) i * stepDelay), e -> {
                FadeTransition ft = new FadeTransition(Duration.millis(180), lbl);
                ft.setFromValue(0); ft.setToValue(1);
                ft.play();
            }));
        }
        // Show progress bar immediately with first line
        reveal.getKeyFrames().add(new KeyFrame(Duration.millis(0), e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(200), progressLabel);
            ft.setFromValue(0); ft.setToValue(1);
            ft.play();
            progressAnim.play();
        }));
        reveal.play();

        // Run conversion in background; wait for minimum animation time
        new Thread(() -> {
            long start = System.currentTimeMillis();
            Optional<File> result = dragDropHandler.convertAndGet(files);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed < MIN_ANIM_MS) {
                try { Thread.sleep(MIN_ANIM_MS - elapsed); }
                catch (InterruptedException ignored) {}
            }
            progressAnim.stop();
            Platform.runLater(() -> progressLabel.setText("[==================]"));
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            result.ifPresentOrElse(
                pdf -> Platform.runLater(() -> showPdfCard(pdf)),
                ()  -> Platform.runLater(() -> setStatus("conversion failed", ERROR_COL))
            );
        }, "QuickPDF-Converter").start();
    }

    // ── After conversion ───────────────────────────────────────

    private void buildUI() {
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setWidth(360);
        stage.setHeight(300);

        VBox root = new VBox(0);
        root.setStyle(
            "-fx-background-color: " + BG + ";" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 18;" +
            "-fx-background-radius: 18;"
        );

        root.getChildren().addAll(
            buildTitleBar(),
            separator(),
            buildDropZone(),
            separator(),
            buildStatusBar()
        );

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        // Set the PDF icon as the taskbar / alt-tab icon for this stage
        Canvas iconCanvas = buildPdfIcon(64, 80);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        stage.getIcons().add(iconCanvas.snapshot(sp, null));
    }

    // PDF document icon — white page, folded corner, red "PDF"
    private Canvas buildPdfIcon(int W, int H) {
        int FOLD = W / 3;
        Canvas c = new Canvas(W, H);
        GraphicsContext gc = c.getGraphicsContext2D();

        gc.setFill(Color.WHITE);
        gc.beginPath();
        gc.moveTo(0, 0);
        gc.lineTo(W - FOLD, 0);
        gc.lineTo(W, FOLD);
        gc.lineTo(W, H);
        gc.lineTo(0, H);
        gc.closePath();
        gc.fill();

        gc.setFill(Color.rgb(180, 180, 180));
        gc.beginPath();
        gc.moveTo(W - FOLD, 0);
        gc.lineTo(W, FOLD);
        gc.lineTo(W - FOLD, FOLD);
        gc.closePath();
        gc.fill();

        gc.setFill(Color.rgb(210, 30, 30));
        gc.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, W / 4));
        gc.fillText("PDF", W * 0.10, H * 0.55);

        return c;
    }

    // ── Title bar ──────────────────────────────────────────────

    private HBox buildTitleBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle(
            "-fx-background-color: " + BG_HEADER + ";" +
            "-fx-background-radius: 18 18 0 0;"
        );

        // App name
        Label appName = new Label("QuickPDF");
        appName.setStyle(
            "-fx-text-fill: " + TEXT + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 13;" +
            "-fx-font-weight: bold;"
        );


        // Mode pill — "JPG → PDF"
        Label modePill = new Label(" JPG → PDF ");
        modePill.setStyle(
            "-fx-text-fill: " + PROMPT + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 10;" +
            "-fx-background-color: #1e2a38;" +
            "-fx-background-radius: 3;" +
            "-fx-padding: 2 6 2 6;"
        );

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Close button
        Label closeBtn = new Label("✕");
        closeBtn.setStyle(closeStyle(TEXT_DIM));
        closeBtn.setOnMouseClicked(e -> hide());
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(closeStyle(ERROR_COL)));
        closeBtn.setOnMouseExited(e  -> closeBtn.setStyle(closeStyle(TEXT_DIM)));

        bar.getChildren().addAll(appName, modePill, spacer, closeBtn);

        bar.setOnMousePressed(e -> { dragOffsetX = e.getSceneX(); dragOffsetY = e.getSceneY(); });
        bar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        return bar;
    }

    private String closeStyle(String color) {
        return "-fx-text-fill: " + color + ";" +
               "-fx-font-family: '" + FONT + "';" +
               "-fx-font-size: 12;" +
               "-fx-cursor: hand;";
    }

    // ── Drop zone ──────────────────────────────────────────────

    private StackPane buildDropZone() {
        dropZone = new StackPane();
        dropZone.setPrefHeight(180);
        dropZone.setStyle(idleDropZoneStyle());
        VBox.setVgrow(dropZone, Priority.ALWAYS);
        dropZone.getChildren().add(buildDropHint());
        setupDropEvents();
        return dropZone;
    }

    // Stacked icon + two lines of text, centred
    private VBox buildDropHint() {
        Label arrow = new Label("↓");
        arrow.setStyle(
            "-fx-text-fill: " + TEXT_DIM + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 22;"
        );

        Label line1 = new Label("drop .jpg file here");
        line1.setStyle(
            "-fx-text-fill: " + TEXT_DIM + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 13;"
        );

        Label line2 = new Label("single or multiple files");
        line2.setStyle(
            "-fx-text-fill: #222222;" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 11;"
        );

        VBox hint = new VBox(6, arrow, line1, line2);
        hint.setAlignment(Pos.CENTER);
        return hint;
    }

    private void setupDropEvents() {
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                boolean anySupported = event.getDragboard().getFiles().stream()
                        .anyMatch(f -> dragDropHandler.canHandle(f.toPath()));
                dropZone.setStyle(anySupported ? acceptDropZoneStyle() : rejectDropZoneStyle());
                if (anySupported) event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        dropZone.setOnDragExited(e -> dropZone.setStyle(idleDropZoneStyle()));

        dropZone.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (!files.isEmpty()) {
                long supported = files.stream()
                        .filter(f -> dragDropHandler.canHandle(f.toPath()))
                        .count();
                if (supported > 0) {
                    String label = supported == 1
                            ? files.stream().filter(f -> dragDropHandler.canHandle(f.toPath()))
                                   .findFirst().map(File::getName).orElse("file")
                            : supported + " files";
                    List<File> snapshot = List.copyOf(files);
                    showLoadingAnimation(label, snapshot);
                } else {
                    setStatus("only .jpg files supported", ERROR_COL);
                }
            }
            dropZone.setStyle(idleDropZoneStyle());
            event.setDropCompleted(true);
            event.consume();
        });
    }

    // ── Status bar ─────────────────────────────────────────────

    private HBox buildStatusBar() {
        HBox bar = new HBox(6);
        bar.setPadding(new Insets(9, 14, 9, 14));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle(
            "-fx-background-color: " + BG_HEADER + ";" +
            "-fx-background-radius: 0 0 18 18;"
        );

        Label prompt = new Label("$");
        prompt.setStyle(
            "-fx-text-fill: " + PROMPT + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 12;"
        );

        statusLabel = new Label("waiting for input...");
        statusLabel.setStyle(statusStyle(TEXT));

        cursorLabel = new Label("▌");
        cursorLabel.setStyle(
            "-fx-text-fill: " + TEXT_DIM + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 12;"
        );

        Timeline blink = new Timeline(
            new KeyFrame(Duration.millis(530), e -> cursorLabel.setVisible(!cursorLabel.isVisible()))
        );
        blink.setCycleCount(Timeline.INDEFINITE);
        blink.play();

        bar.getChildren().addAll(prompt, statusLabel, cursorLabel);
        return bar;
    }

    // ── Callbacks ──────────────────────────────────────────────

    private void showPdfCard(File pdfFile) {
        Platform.runLater(() -> {
            setStatus("done  " + pdfFile.getName(), SUCCESS);
            ResultCardController card = new ResultCardController(pdfFile, this::resetToIdle);
            dropZone.getChildren().setAll(card.getNode());
            card.animateIn();
        });
    }

    // ── Helpers ────────────────────────────────────────────────

    private void setStatus(String message, String colour) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle(statusStyle(colour));
        });
    }

    private String statusStyle(String colour) {
        return "-fx-text-fill: " + colour + ";" +
               "-fx-font-family: '" + FONT + "';" +
               "-fx-font-size: 12;";
    }

    private void resetToIdle() {
        setStatus("waiting for input...", TEXT);
        dropZone.getChildren().setAll(buildDropHint());
        dropZone.setStyle(idleDropZoneStyle());
    }

    // Thin horizontal separator line
    private Region separator() {
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: " + BORDER + ";");
        return sep;
    }

    private String idleDropZoneStyle() {
        return "-fx-background-color: " + BG_PANEL + ";" +
               "-fx-border-color: #2e2e2e;" +
               "-fx-border-style: dashed;" +
               "-fx-border-width: 1;" +
               "-fx-border-insets: 14;" +
               "-fx-border-radius: 5;";
    }

    private String acceptDropZoneStyle() {
        return "-fx-background-color: #001a0e;" +
               "-fx-border-color: " + SUCCESS + ";" +
               "-fx-border-style: dashed;" +
               "-fx-border-width: 1;" +
               "-fx-border-insets: 14;" +
               "-fx-border-radius: 5;";
    }

    private String rejectDropZoneStyle() {
        return "-fx-background-color: #1a0000;" +
               "-fx-border-color: " + ERROR_COL + ";" +
               "-fx-border-style: dashed;" +
               "-fx-border-width: 1;" +
               "-fx-border-insets: 14;" +
               "-fx-border-radius: 5;";
    }
}

