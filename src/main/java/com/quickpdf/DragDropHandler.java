package com.quickpdf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

// Sits between the UI and the conversion service
public class DragDropHandler {

    private static final Logger LOG = Logger.getLogger(DragDropHandler.class.getName());

    private final ConversionService conversionService;
    private final Consumer<File>    onSuccess;

    public DragDropHandler(ConversionService conversionService, Consumer<File> onSuccess) {
        if (conversionService == null) throw new IllegalArgumentException("ConversionService cannot be null");
        if (onSuccess == null)        throw new IllegalArgumentException("onSuccess callback cannot be null");
        this.conversionService = conversionService;
        this.onSuccess = onSuccess;
    }

    // Quick check — is this file type supported?
    public boolean canHandle(Path path) {
        return conversionService.supports(path);
    }

    // Converts and fires onSuccess callback (legacy single-file path)
    public void handle(File file) {
        if (file == null) { LOG.warning("Received null file."); return; }
        try {
            File pdf = conversionService.convert(file);
            onSuccess.accept(pdf);
        } catch (IllegalArgumentException e) { LOG.warning("Invalid file: " + e.getMessage()); }
          catch (IOException e)               { LOG.severe("Conversion failed: " + e.getMessage()); }
    }

    // Returns the output PDF directly — used by PopupController for timed loading animation
    public Optional<File> convertAndGet(List<File> files) {
        if (files == null || files.isEmpty()) return Optional.empty();

        List<File> supported = files.stream()
                .filter(f -> f != null && conversionService.supports(f.toPath()))
                .toList();

        if (supported.isEmpty()) {
            LOG.warning("No supported files in drop.");
            return Optional.empty();
        }

        try {
            File pdf = supported.size() == 1
                    ? conversionService.convert(supported.get(0))
                    : conversionService.convertAll(supported);
            LOG.info("Conversion successful: " + pdf.getName());
            return Optional.of(pdf);
        } catch (IllegalArgumentException e) {
            LOG.warning("Invalid file: " + e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            LOG.severe("Conversion failed: " + e.getMessage());
            return Optional.empty();
        }
    }

}