package com.quickpdf;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class ResultCardController {

    private static final Logger LOG = Logger.getLogger(ResultCardController.class.getName());

    private static final String BG       = "#1e1e1e";
    private static final String SUCCESS  = "#00ff88";
    private static final String TEXT     = "#d4d4d4";
    private static final String DIM      = "#555555";
    private static final String ERROR_COL = "#f44747";

    private final File     pdfFile;
    private final Runnable onReset;   // called when user clicks [reset]
    private StackPane      card;

    public ResultCardController(File pdfFile, Runnable onReset) {
        if (pdfFile == null) throw new IllegalArgumentException("pdfFile must not be null");
        if (onReset == null) throw new IllegalArgumentException("onReset must not be null");
        this.pdfFile = pdfFile;
        this.onReset = onReset;
        this.card    = buildCard();
        setupDragOut();
    }

    // PopupController calls this to get the node to insert into the layout
    public Node getNode() {
        return card;
    }

    // Called by PopupController right after inserting the card into the scene
    public void animateIn() {
        // Start above final position, invisible
        card.setTranslateY(-40);
        card.setOpacity(0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), card);
        slide.setFromY(-40);
        slide.setToY(0);

        FadeTransition fade = new FadeTransition(Duration.millis(300), card);
        fade.setFromValue(0);
        fade.setToValue(1);

        new ParallelTransition(slide, fade).play();
    }

    // ── Card UI ────────────────────────────────────────────────

    private StackPane buildCard() {
        StackPane wrapper = new StackPane();
        wrapper.setPadding(new Insets(16));

        VBox content = new VBox(6);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(14, 18, 14, 18));
        content.setStyle(
            "-fx-background-color: #252526;" +
            "-fx-border-color: " + SUCCESS + ";" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: move;" +
            // Drop shadow effect via border glow
            "-fx-effect: dropshadow(gaussian, #00ff8855, 10, 0.3, 0, 2);"
        );

        // File icon + name
        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size: 16;");
        Label name = new Label(pdfFile.getName());
        name.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-family: 'Courier New'; -fx-font-size: 13;");
        nameRow.getChildren().addAll(icon, name);

        // File size
        long kb = pdfFile.length() / 1024;
        Label size = new Label((kb < 1 ? "<1" : kb) + " KB");
        size.setStyle("-fx-text-fill: " + DIM + "; -fx-font-family: 'Courier New'; -fx-font-size: 11;");

        // Drag hint
        Label hint = new Label("drag me out  →");
        hint.setStyle("-fx-text-fill: " + DIM + "; -fx-font-family: 'Courier New'; -fx-font-size: 11;");

        // Reset link
        Label reset = new Label("[reset]");
        reset.setStyle("-fx-text-fill: " + DIM + "; -fx-font-family: 'Courier New'; -fx-font-size: 11; -fx-cursor: hand;");
        reset.setOnMouseEntered(e -> reset.setStyle(
            "-fx-text-fill: " + ERROR_COL + "; -fx-font-family: 'Courier New'; -fx-font-size: 11; -fx-cursor: hand;"));
        reset.setOnMouseExited(e -> reset.setStyle(
            "-fx-text-fill: " + DIM + "; -fx-font-family: 'Courier New'; -fx-font-size: 11; -fx-cursor: hand;"));
        reset.setOnMouseClicked(e -> onReset.run());

        content.getChildren().addAll(nameRow, size, hint, reset);
        wrapper.getChildren().add(content);
        return wrapper;
    }

    // ── Drag out ───────────────────────────────────────────────

    private void setupDragOut() {
        // User starts dragging the card out of the popup
        card.setOnDragDetected(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putFiles(List.of(pdfFile));   // puts the real File into the drag

            var db = card.startDragAndDrop(TransferMode.COPY);
            db.setContent(content);

            LOG.info("Drag-out started for: " + pdfFile.getName());
            e.consume();
        });

        // User dropped the PDF onto another window — schedule cleanup
        card.setOnDragDone(e -> {
            if (e.getTransferMode() == TransferMode.COPY) {
                LOG.info("Drag-out completed — scheduling deletion.");
                TempFileManager.getInstance().deleteWithDelay(pdfFile);
            }
            e.consume();
        });
    }
}