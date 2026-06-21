package com.quickpdf;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class ResultCardController {

    private static final Logger LOG = Logger.getLogger(ResultCardController.class.getName());

    private static final String SUCCESS   = "#4ec994";
    private static final String DIM       = "#444444";
    private static final String ERROR_COL = "#e05c5c";
    private static final String FONT      = "Consolas";

    private final File     pdfFile;
    private final Runnable onReset;
    private final VBox     card;
    private       Canvas   iconCanvas; // kept for drag snapshot

    public ResultCardController(File pdfFile, Runnable onReset) {
        if (pdfFile == null) throw new IllegalArgumentException("pdfFile must not be null");
        if (onReset == null) throw new IllegalArgumentException("onReset must not be null");
        this.pdfFile = pdfFile;
        this.onReset = onReset;
        this.card    = buildCard();
        setupDragOut();
    }

    public Node getNode() { return card; }

    public void animateIn() {
        card.setTranslateY(-20);
        card.setOpacity(0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(280), card);
        slide.setFromY(-20);
        slide.setToY(0);

        FadeTransition fade = new FadeTransition(Duration.millis(280), card);
        fade.setFromValue(0);
        fade.setToValue(1);

        new ParallelTransition(slide, fade).play();
    }

    // ── Centered card inside the drop zone ─────────────────────

    private VBox buildCard() {
        // ── PDF icon ───────────────────────────────────────────
        Node pdfIcon = buildPdfIcon();

        // File name
        Label name = new Label(pdfFile.getName());
        name.setStyle(
            "-fx-text-fill: " + DIM + ";" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 12;"
        );

        // File size
        long kb = Math.max(1, pdfFile.length() / 1024);
        Label size = new Label(kb + " KB");
        size.setStyle(
            "-fx-text-fill: #333333;" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 11;"
        );

        // Prominent drag hint pill — centered
        Label dragHint = new Label("  drag me anywhere  →  ");
        dragHint.setStyle(
            "-fx-text-fill: #cccccc;" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 12;" +
            "-fx-background-color: #1a1a1a;" +
            "-fx-background-radius: 4;" +
            "-fx-padding: 5 10 5 10;" +
            "-fx-cursor: move;"
        );

        // Centered content
        VBox center = new VBox(7, pdfIcon, name, size, dragHint);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(10));
        center.setStyle("-fx-cursor: move;");

        // ↺ reset icon — top-right corner, overlaid
        Label resetBtn = new Label("↺");
        resetBtn.setStyle(resetStyle("#333333"));
        resetBtn.setOnMouseEntered(e -> resetBtn.setStyle(resetStyle(ERROR_COL)));
        resetBtn.setOnMouseExited(e  -> resetBtn.setStyle(resetStyle("#333333")));
        resetBtn.setOnMouseClicked(e -> onReset.run());
        StackPane.setAlignment(resetBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(resetBtn, new Insets(8, 10, 0, 0));

        StackPane wrapper = new StackPane(center, resetBtn);

        VBox root = new VBox(wrapper);
        VBox.setVgrow(wrapper, javafx.scene.layout.Priority.ALWAYS);
        return root;
    }

    // Draws a PDF document icon using Canvas — white page, folded corner, red "PDF"
    private Node buildPdfIcon() {
        int W = 60, H = 76, FOLD = 18;

        iconCanvas = new Canvas(W, H); // store reference for drag snapshot
        GraphicsContext gc = iconCanvas.getGraphicsContext2D();

        // White document body with top-right fold cut
        gc.setFill(Color.WHITE);
        gc.beginPath();
        gc.moveTo(0, 0);
        gc.lineTo(W - FOLD, 0);
        gc.lineTo(W, FOLD);
        gc.lineTo(W, H);
        gc.lineTo(0, H);
        gc.closePath();
        gc.fill();

        // Fold triangle — slightly darker white
        gc.setFill(Color.rgb(190, 190, 190));
        gc.beginPath();
        gc.moveTo(W - FOLD, 0);
        gc.lineTo(W, FOLD);
        gc.lineTo(W - FOLD, FOLD);
        gc.closePath();
        gc.fill();

        // Subtle content lines
        gc.setStroke(Color.rgb(220, 220, 220));
        gc.setLineWidth(1.5);
        for (int y = 44; y <= 64; y += 8) {
            gc.strokeLine(10, y, W - 10, y);
        }

        // Red "PDF" label
        gc.setFill(Color.rgb(210, 30, 30));
        gc.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 14));
        gc.fillText("PDF", 9, 36);

        // 90% opacity — matches OS file icon look
        iconCanvas.setOpacity(0.9);
        return iconCanvas;
    }

    private String resetStyle(String color) {
        return "-fx-text-fill: " + color + ";" +
               "-fx-font-size: 16;" +
               "-fx-cursor: hand;" +
               "-fx-effect: dropshadow(gaussian, " + color + ", 8, 0.6, 0, 0);";
    }

    // ── Drag the PDF out to another window ─────────────────────

    private void setupDragOut() {
        card.setOnDragDetected(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(pdfFile));

            var db = card.startDragAndDrop(TransferMode.COPY);
            db.setContent(content);

            // Snapshot the PDF icon canvas and attach it to the cursor
            if (iconCanvas != null) {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                WritableImage dragImage = iconCanvas.snapshot(params, null);
                // Centre the icon under the cursor
                db.setDragView(dragImage,
                        dragImage.getWidth()  / 2,
                        dragImage.getHeight() / 2);
            }

            LOG.info("Drag-out started: " + pdfFile.getName());
            e.consume();
        });

        card.setOnDragDone(e -> {
            if (e.getTransferMode() == TransferMode.COPY) {
                LOG.info("Drag-out completed — scheduling deletion.");
                TempFileManager.getInstance().deleteWithDelay(pdfFile);
            }
            e.consume();
        });
    }
}