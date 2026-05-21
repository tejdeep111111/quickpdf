package com.quickpdf;

import java.nio.file.Path;
import java.util.Objects;

public final class DragDropHandler {

    private final ConversionService conversionService;

    public DragDropHandler(ConversionService conversionService) {
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService");
    }

    public boolean canHandle(Path file) {
        return conversionService.supports(file);
    }
}
