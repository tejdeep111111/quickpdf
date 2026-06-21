package com.quickpdf;

import javafx.application.Platform;
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
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
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


    private final Runnable onReset;
    private final VBox     card;
    private       Canvas   iconCanvas; // kept for drag snapshot
    private       File     pdfFile;    // mutable — updated on rename

    public ResultCardController(File pdfFile, Runnable onReset) {
        if (pdfFile == null) throw new IllegalArgumentException("pdfFile must not be null");
        if (onReset == null) throw new IllegalArgumentException("onReset must not be null");
        this.pdfFile = pdfFile;   // mutable field
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

        // ── Inline-editable filename ───────────────────────────
        // Hover → pencil hint appears. Click → Label swaps to TextField.
        // Enter / focus lost → file renamed, Label restored.

        Label nameLabel = new Label(pdfFile.getName());
        nameLabel.setStyle(nameStyle(DIM));

        Label pencil = new Label(" ✎");
        pencil.setStyle(
            "-fx-text-fill: #333333;" +
            "-fx-font-size: 11;"
        );
        pencil.setVisible(false);

        HBox nameRow = new HBox(2, nameLabel, pencil);
        nameRow.setAlignment(Pos.CENTER);
        nameRow.setStyle("-fx-cursor: text;");

        nameRow.setOnMouseEntered(e -> pencil.setVisible(true));
        nameRow.setOnMouseExited(e  -> pencil.setVisible(false));

        nameRow.setOnMouseClicked(e -> startRename(nameRow, nameLabel, pencil));

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
        VBox center = new VBox(7, pdfIcon, nameRow, size, dragHint);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(10));
        center.setStyle("-fx-cursor: move;");

        // ↺ reset icon — top-right corner, overlaid
        Label resetBtn = new Label("↺");
        resetBtn.setStyle(resetStyle("#333333"));
        resetBtn.setOnMouseEntered(e -> resetBtn.setStyle(resetStyle("#666666")));
        resetBtn.setOnMouseExited(e  -> resetBtn.setStyle(resetStyle("#333333")));
        resetBtn.setOnMouseClicked(e -> {
            // Delete the file then go back to idle
            TempFileManager.getInstance().deleteWithDelay(pdfFile);
            onReset.run();
        });
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

    private String nameStyle(String color) {
        return "-fx-text-fill: " + color + ";" +
               "-fx-font-family: '" + FONT + "';" +
               "-fx-font-size: 12;";
    }

    // Swap Label → TextField, handle rename on Enter / focus lost
    private void startRename(HBox nameRow, Label nameLabel, Label pencil) {
        String current = pdfFile.getName().replaceAll("\\.pdf$", "");

        TextField field = new TextField(current);
        field.setStyle(
            "-fx-background-color: #111111;" +
            "-fx-text-fill: #cccccc;" +
            "-fx-font-family: '" + FONT + "';" +
            "-fx-font-size: 12;" +
            "-fx-border-color: #569cd6;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 3;" +
            "-fx-background-radius: 3;" +
            "-fx-padding: 2 6 2 6;" +
            "-fx-pref-width: 200;"
        );

        // Swap label row → text field
        nameRow.getChildren().setAll(field);
        field.requestFocus();
        field.selectAll();

        Runnable commit = () -> {
            String newBase = field.getText().trim();
            if (newBase.isEmpty()) newBase = current;
            if (!newBase.endsWith(".pdf")) newBase += ".pdf";

            File newFile = new File(pdfFile.getParent(), newBase);
            boolean renamed = pdfFile.renameTo(newFile);

            if (renamed) {
                pdfFile = newFile; // ← update reference so next drag uses new path
                nameLabel.setText(newBase);
                nameLabel.setStyle(nameStyle(DIM));
            } else {
                // Flash red if rename failed, restore original name
                nameLabel.setText(pdfFile.getName());
                nameLabel.setStyle(nameStyle(ERROR_COL));
            }
            pencil.setVisible(false);
            nameRow.getChildren().setAll(nameLabel, pencil);
        };

        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) commit.run();
        });
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                nameLabel.setText(pdfFile.getName());
                nameLabel.setStyle(nameStyle(DIM));
                nameRow.getChildren().setAll(nameLabel, pencil);
            }
        });
    }

    private String resetStyle(String color) {
        return "-fx-text-fill: " + color + ";" +
               "-fx-font-size: 16;" +
               "-fx-cursor: hand;" +
               "-fx-opacity: 0.9;";
    }

    // ── Drag the PDF out to another window ─────────────────────

    private void setupDragOut() {
        card.setOnDragDetected(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(pdfFile));

            var db = card.startDragAndDrop(TransferMode.COPY);
            db.setContent(content);

            if (iconCanvas != null) {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                WritableImage dragImage = iconCanvas.snapshot(params, null);
                db.setDragView(dragImage,
                        dragImage.getWidth()  / 2,
                        dragImage.getHeight() / 2);
            }

            LOG.info("Drag-out started: " + pdfFile.getName());
            e.consume();
        });

        card.setOnDragDone(e -> {
            // File stays alive — user can drag again until reset is clicked
            LOG.info("Drag-out completed: " + pdfFile.getName());
            e.consume();
        });
    }
}